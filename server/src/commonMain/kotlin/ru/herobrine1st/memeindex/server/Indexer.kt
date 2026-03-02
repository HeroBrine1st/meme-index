package ru.herobrine1st.memeindex.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.sqlx4k.Statement
import io.github.smyrgeorge.sqlx4k.postgres.IPostgresSQL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.bytestring.ByteString
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import ru.herobrine1st.memeindex.server.api.Embedder
import ru.herobrine1st.memeindex.server.generated.sqlx4k.EmbeddingRepositoryImpl
import ru.herobrine1st.memeindex.server.generated.sqlx4k.FileRepositoryImpl
import ru.herobrine1st.memeindex.server.generated.sqlx4k.ModelMetaRepositoryImpl
import ru.herobrine1st.memeindex.server.generated.sqlx4k.ScanJoinRepositoryImpl
import ru.herobrine1st.memeindex.server.generated.sqlx4k.ScanRepositoryImpl
import ru.herobrine1st.memeindex.server.repository.MediaFile
import ru.herobrine1st.memeindex.server.repository.ScanEntry
import ru.herobrine1st.memeindex.server.util.debounceIntoBuffer
import ru.herobrine1st.memeindex.shared.model.Vector
import ru.herobrine1st.memeindex.server.util.SuryaUtils
import kotlin.time.Duration.Companion.milliseconds

val TEXT_FILES: Set<String> = setOf(".md", ".txt")
val SUPPORTED_FILES: Set<String> = setOf(".jpg", ".png", ".jpeg", ".webp") + TEXT_FILES
const val FETCH_PAGE_SIZE: Int = 200

class Indexer(
    private val settings: Settings,
    private val db: IPostgresSQL,
    private val embedder: Embedder,
) {
    private val logger = KotlinLogging.logger {}
    private val root: Path = settings.mediaPath

    suspend fun run(scope: CoroutineScope) {
        val mediaDir = settings.mediaPath
        withContext(Dispatchers.IO) {
            val meta = SystemFileSystem.metadataOrNull(mediaDir)
            if (meta != null && !meta.isDirectory) {
                error("${settings.mediaPath} should be a directory!")
            }
            SystemFileSystem.createDirectories(mediaDir)
        }

        logger.info { "Scanning directory $root" }

        stage1Import()
        stage2Process()

        logger.info { "Initial scan complete. Starting observer..." }
        val watcher = MediaFileWatcher(scope, Dispatchers.IO)
        watcher.errors
            .onEach { error ->
                logger.warn { "Watcher error (${error.targetDirectory}): ${error.message}" }
            }
            .launchIn(scope)
        val watchedDirs = withContext(Dispatchers.IO) { walkDirectories(root).toList() }
        val events = watcher.changes
            .debounceIntoBuffer(5_000)
            .onEach { batch ->
                batch.forEach { change ->
                    processEvent(change)
                }
            }
            .flowOn(Dispatchers.IO)
            .onEach {
                stage2Process()
            }
            .launchIn(scope)
        watcher.addDirectories(*watchedDirs.map { it.toString() }.toTypedArray())
        events.join()
    }

    private suspend fun stage1Import() {
        val scan = withContext(Dispatchers.IO) {
            walkSupportedFiles(root).map { path ->
                path to lastModifiedTime(path)
            }
        }

        db.transaction {
            val tx = this
            tx.execute(
                Statement.create("CREATE TEMP TABLE current_scan (path TEXT, mtime TIMESTAMPTZ) ON COMMIT DROP"),
            ).getOrThrow()
            for (chunk in scan.chunked(STAGE1_CHUNK_SIZE)) {
                ScanRepositoryImpl.batchInsert(
                    tx,
                    chunk.map { (path, mtime) -> ScanEntry(rootRelative(path), mtime) },
                ).getOrThrow()
            }

            data class DbRef(val id: Int, val oldPath: String)

            val missingFromDisk = LinkedHashMap<ByteString, MutableList<DbRef>>()
            val inserts = mutableListOf<MediaFile>()

            var offset = 0
            while (true) {
                val page = ScanJoinRepositoryImpl.findAllByMissingFromDisk(tx, FETCH_PAGE_SIZE, offset).getOrThrow()
                for ((id, oldPath, oldSha256) in page) {
                    missingFromDisk.getOrPut(oldSha256!!) { mutableListOf() }
                        .add(DbRef(id!!, oldPath!!))
                }
                if (page.size < FETCH_PAGE_SIZE) break
                offset += page.size
            }

            offset = 0
            val updates = mutableListOf<MediaFile>()
            val contentUpdateIds = mutableListOf<Int>()
            do {
                val page = ScanJoinRepositoryImpl.findAllByChangedOnDisk(tx, FETCH_PAGE_SIZE, offset).getOrThrow()
                for ((oldId, _, oldSha, newPath1, newMtime1) in page) {
                    val newPath = newPath1!!
                    val newMtime = newMtime1!!
                    val sha256 = withContext(Dispatchers.IO) { hashFile(dbToDisk(newPath)) }

                    val queue = missingFromDisk[sha256]
                    val moved = if (!queue.isNullOrEmpty()) queue.removeAt(0) else null
                    when {
                        moved != null -> {
                            updates += MediaFile(moved.id, newPath, sha256, newMtime)
                            logger.info { "Detected move: ${moved.oldPath} -> $newPath" }
                        }

                        oldId == null -> inserts += MediaFile(0, newPath, sha256, newMtime)
                        oldSha != sha256 -> {
                            updates += MediaFile(oldId, newPath, sha256, newMtime)
                            contentUpdateIds += oldId
                        }

                        else -> updates += MediaFile(oldId, newPath, sha256, newMtime)
                    }

                    if (updates.size >= STAGE1_CHUNK_SIZE) {
                        FileRepositoryImpl.batchUpdate(tx, updates).getOrThrow()
                        updates.clear()
                    }
                    if (inserts.size >= STAGE1_CHUNK_SIZE) {
                        FileRepositoryImpl.batchInsert(tx, inserts).getOrThrow()
                        inserts.clear()
                    }
                    if (contentUpdateIds.size >= STAGE1_CHUNK_SIZE) {
                        EmbeddingRepositoryImpl.deleteByFileIdIn(tx, contentUpdateIds).getOrThrow()
                        contentUpdateIds.clear()
                    }
                }
                offset += page.size
            } while (page.size >= FETCH_PAGE_SIZE)

            for (chunk in missingFromDisk.values.flatten().map { it.id }.chunked(STAGE1_CHUNK_SIZE)) {
                FileRepositoryImpl.deleteByIdIn(tx, chunk).getOrThrow()
            }
            if (updates.isNotEmpty()) FileRepositoryImpl.batchUpdate(tx, updates).getOrThrow()
            if (inserts.isNotEmpty()) FileRepositoryImpl.batchInsert(tx, inserts).getOrThrow()
            if (contentUpdateIds.isNotEmpty()) EmbeddingRepositoryImpl.deleteByFileIdIn(tx, contentUpdateIds)
                .getOrThrow()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun stage2Process() {
        val batchSize = settings.embeddings.batchSize
        val count = FileRepositoryImpl.countByPendingEmbedding(db, embedder.modelName).getOrThrow()
        logger.info { "Stage 2 processing: encoding $count files" }
        db.transaction {
            FileRepositoryImpl.streamAllByPendingEmbedding(this, embedder.modelName)
                .chunked(batchSize)
                .collect { batch ->
                    processFiles(batch.map { it.id to dbToDisk(it.path) })
                }
        }
    }

    private suspend fun processFiles(files: List<Pair<Int, Path>>) {
        logger.info { "Batch processing ${files.size} files" }
        logger.debug { "Batch processing $files files" }

        val imageFiles = files.filter { extension(it.second) !in TEXT_FILES }
        val textFiles = files.filter { extension(it.second) in TEXT_FILES }

        data class EmbeddingRow(
            val fileId: Int,
            val imageVector: Vector?,
            val text: String?,
            val textVector: Vector?,
        )

        val results = mutableListOf<EmbeddingRow>()

        try {
            imageFiles.mapNotNull { (id, path) ->
                runCatching { toPng(SystemFileSystem.source(path).buffered()) }
                    .getOrNull()
                    ?.let { id to it }
            }.takeIf {
                it.isNotEmpty()
            }?.let { validFiles ->
                val embeddingMap = embedder.embedImages(validFiles.map { (_, v) -> v })
                    .zip(validFiles) { result, (id, _) -> id to result }
                    .toMap()
                val texts = ocrBatch(validFiles.map { it.second })
                    .zip(validFiles) { result, (id, _) -> id to result }
                    .filter { (_, it) -> it != null }
                val textMap = texts.toMap()

                val textVectorMap = embedder.embedText(texts.map { it.second!! })
                    .zip(texts) { result, (id, _) -> id to result }
                    .toMap()

                for ((k, _) in validFiles) {
                    results += EmbeddingRow(
                        fileId = k,
                        imageVector = embeddingMap[k],
                        text = textMap[k],
                        textVector = textVectorMap[k],
                    )
                }
            }

            if (textFiles.isNotEmpty()) {
                val texts = textFiles
                    .map { (_, path) ->
                        lenientUtf8Decode(SystemFileSystem.source(path).buffered().readByteArray())
                    }
                    .zip(textFiles) { result, (id, _) -> id to result }

                val textMap = texts.toMap()
                val textVectors = embedder.embedText(texts.map { (_, it) -> it })
                    .zip(textFiles) { result, (id, _) -> id to result }
                    .toMap()
                for ((k, _) in textVectors) {
                    results += EmbeddingRow(k, null, textMap[k], textVectors[k])
                }
            }
        } catch (e: Throwable) {
            logger.error(e) { "Failed to process batch of ${files.size} files" }
            return
        }

        if (results.isNotEmpty()) {
            db.transaction {
                ModelMetaRepositoryImpl.findAllByLockedById(this, 1).getOrThrow()
                for ((fileId, imageVector, text, textVector) in results) {
                    EmbeddingRepositoryImpl.executeUpsert(
                        this,
                        fileId = fileId,
                        imageVector = imageVector,
                        text = text,
                        textVector = textVector,
                        model = embedder.modelName,
                    ).getOrThrow()
                }
            }
        }
    }

    private suspend fun ocrBatch(pngs: List<ByteString>): List<String?> = coroutineScope {
        pngs.map { png ->
            async {
                var lastOcrResult: String? = null
                val ocr = settings.ocr
                for (i in 0..<ocr.attempts) {
                    try {
                        // Linear ramp from temperature (first attempt) to temperatureMax (last);
                        // constant temperature when attempts == 1 (avoids division by zero).
                        val temperature = ocr.temperature + (ocr.temperatureMax - ocr.temperature) *
                                i / (ocr.attempts - 1).coerceAtLeast(1).toDouble()
                        val raw = embedder.ocr(png, temperature)
                        lastOcrResult = raw
                        lastOcrResult = SuryaUtils.extractOcrText(lastOcrResult)
                        break
                    } catch (t: Throwable) {
                        logger.warn { "OCR attempt ${i + 1}/${ocr.attempts} failed: $t" }
                        delay((i * 1.5 * OCR_RETRY_DELAY_MS).milliseconds)
                    }
                }
                lastOcrResult
            }
        }.awaitAll()
    }

    private suspend fun processEvent(change: MediaFileChange) {
        val path = change.path
        val exists = SystemFileSystem.metadataOrNull(path)?.isRegularFile == true
        if (
            extension(path) in SUPPORTED_FILES &&
            (exists xor (change.event == MediaFileEvent.Delete))
        ) try {
            handleEvent(change.event, path)
        } catch (e: Throwable) {
            logger.error(e) { "Failed to handle watch event ${change.event} $path" }
        }
    }

    private suspend fun handleEvent(event: MediaFileEvent, path: Path) {
        val rel = rootRelative(path)
        if (event == MediaFileEvent.Delete) {
            logger.info { "Observed deletion: $path" }
            FileRepositoryImpl.deleteByPath(db, rel).getOrThrow()
            return
        }
        val mtime = lastModifiedTime(path)
        val sha256 = hashFile(path)

        val old = FileRepositoryImpl.findAllByPath(db, rel).getOrThrow().firstOrNull()

        if (old == null) {
            val moved = FileRepositoryImpl.findAllBySha256(db, sha256).getOrThrow().firstOrNull()
                ?.takeIf {
                    withContext(Dispatchers.IO) {
                        !SystemFileSystem.exists(
                            dbToDisk(it.path),
                        )
                    }
                }
            if (moved != null) {
                logger.info { "Observed move: ${moved.path} -> $rel" }
                FileRepositoryImpl.update(db, moved.copy(path = rel, mtime = mtime)).getOrThrow()
            } else {
                FileRepositoryImpl.insert(db, MediaFile(0, rel, sha256, mtime))
                    .getOrThrow().id
            }
        } else {
            if (old.sha256 != sha256) {
                FileRepositoryImpl.update(db, old.copy(sha256 = sha256, mtime = mtime)).getOrThrow()
                EmbeddingRepositoryImpl.deleteByFileId(db, old.id).getOrThrow()
            } else if (old.mtime != mtime) {
                FileRepositoryImpl.update(db, old.copy(mtime = mtime)).getOrThrow()
            } else {
                return
            }
        }
    }

    private fun walkDirectories(root: Path): List<Path> {
        val result = mutableListOf(root)
        val visited = mutableSetOf(root)
        fun walk(dir: Path) {
            val entries = runCatching { SystemFileSystem.list(dir) }.getOrNull() ?: return
            for (entry in entries) {
                if (SystemFileSystem.metadataOrNull(entry)?.isDirectory != true) continue
                val resolved = resolveRealPath(entry) ?: entry
                if (visited.add(resolved)) {
                    result.add(resolved)
                    walk(resolved)
                }
            }
        }
        walk(root)
        return result
    }

    private fun walkSupportedFiles(root: Path): List<Path> {
        val result = mutableListOf<Path>()
        val visited = mutableSetOf<Path>()
        fun walk(dir: Path) {
            val resolved = resolveRealPath(dir) ?: dir
            if (!visited.add(resolved)) return
            val entries = runCatching { SystemFileSystem.list(dir) }.getOrNull() ?: return
            for (entry in entries) {
                if (SystemFileSystem.metadataOrNull(entry)?.isDirectory == true) {
                    walk(entry)
                } else if (extension(entry) in SUPPORTED_FILES) {
                    result.add(entry)
                }
            }
        }
        walk(root)
        return result
    }

    private fun hashFile(path: Path): ByteString {
        val bytes = SystemFileSystem.source(path).buffered().readByteArray()
        return sha256(ByteString(bytes))
    }

    private fun rootRelative(path: Path): String {
        val prefix = root.toString().trimEnd('/')
        val s = path.toString()
        return if (s.startsWith("$prefix/")) s.substring(prefix.length + 1) else s
    }

    private fun dbToDisk(path: String): Path {
        val p = Path(path)
        return if (p.isAbsolute) p else Path(root, path)
    }

    companion object {
        const val OCR_RETRY_DELAY_MS: Long = 1_000L

        const val STAGE1_CHUNK_SIZE: Int = 500

        fun extension(path: Path): String {
            val name = path.name
            val dot = name.lastIndexOf('.')
            return if (dot > 0) name.substring(dot).lowercase() else ""
        }

    }
}
