package ru.herobrine1st.memeindex.server

import io.github.irgaly.kfswatch.KfsDirectoryWatcher
import io.github.irgaly.kfswatch.KfsDirectoryWatcherEvent
import io.github.irgaly.kfswatch.KfsEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * Watches a set of directories for file changes, delivering events
 * at-least-once (consumers must be idempotent).
 *
 * kfswatch is not recursive, so this wrapper maintains the watched set:
 * when a directory is created, it is added to the watcher (before its contents
 * are inspected), a CREATE is emitted for every file and folder inside it, and
 * each new subdirectory is added before descending into it. When a watched
 * directory is deleted, it (and everything watched under it) is removed from
 * the watcher — kfswatch also drops such watches on its own, so removal is
 * idempotent.
 */
class MediaFileWatcher(
    scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
) {
    private val watcher = KfsDirectoryWatcher(scope, dispatcher)
    private val mutex = Mutex()
    private val watchedDirs = mutableSetOf<Path>()

    /**
     * Registers [directories] for watching (idempotent — already-registered
     * paths are skipped).
     */
    suspend fun addDirectories(vararg directories: String) {
        val newDirs = mutex.withLock {
            directories.map { Path(it) }.filter { watchedDirs.add(it) }
        }
        if (newDirs.isNotEmpty()) {
            watcher.add(*newDirs.map { it.toString() }.toTypedArray())
        }
    }

    val changes: Flow<MediaFileChange> = flow {
        watcher.onEventFlow.collect { event ->
            emitChanges(event)
        }
    }

    val errors: Flow<WatcherError> = watcher.onErrorFlow.map {
        WatcherError(it.targetDirectory ?: "", it.message)
    }

    private suspend fun FlowCollector<MediaFileChange>.emitChanges(event: KfsDirectoryWatcherEvent) {
        val path = Path(event.targetDirectory, event.path)
        when (event.event) {
            KfsEvent.Create -> {
                if (isDirectory(path)) {
                    emitCreatedDirectory(path)
                } else {
                    emit(MediaFileChange(MediaFileEvent.Create, path))
                }
            }
            KfsEvent.Modify -> emit(MediaFileChange(MediaFileEvent.Modify, path))
            KfsEvent.Delete -> {
                emitDeletedDirectory(path)
                emit(MediaFileChange(MediaFileEvent.Delete, path))
            }
        }
    }

    /**
     * [path] is a newly created directory: add it to the watcher first (files
     * created inside it from now on produce events of their own), then emit
     * synthetic CREATEs for its contents — every file and folder in its subtree.
     */
    private suspend fun FlowCollector<MediaFileChange>.emitCreatedDirectory(path: Path) {
        if (!register(path)) return // already watched — its events come through directly
        scanContents(path)
    }

    /**
     * Emits a CREATE for every file in [dir] and recurses into every folder:
     * a subfolder is added to the watcher BEFORE descending into it (files
     * created inside it during the scan produce real events, and the scan
     * covers whatever already exists).
     */
    private suspend fun FlowCollector<MediaFileChange>.scanContents(dir: Path) {
        listEntries(dir).forEach { entry ->
            if (isDirectory(entry)) {
                // Descending only for newly-registered paths also bounds symlink
                // cycles (each path string is scanned at most once).
                if (register(entry)) {
                    emit(MediaFileChange(MediaFileEvent.Create, entry))
                    scanContents(entry)
                }
            } else {
                emit(MediaFileChange(MediaFileEvent.Create, entry))
            }
        }
    }

    /**
     * Adds [dir] to the watcher (and remembers it). False if it is already watched.
     */
    private suspend fun register(dir: Path): Boolean {
        val isNew = mutex.withLock { watchedDirs.add(dir) }
        if (isNew) {
            watcher.add(dir.toString())
        }
        return isNew
    }

    /**
     * [path] was deleted. If it was a watched directory, drop it — and everything
     * watched under it — from the watcher.
     */
    private suspend fun FlowCollector<MediaFileChange>.emitDeletedDirectory(path: Path) {
        val removed = mutex.withLock {
            val removedDirs = mutableListOf<Path>()
            if (watchedDirs.remove(path)) {
                removedDirs += path
                // Everything watched strictly inside [path] is gone with it.
                val prefix = path.toString() + "/"
                val inner = watchedDirs.filter { it.toString().startsWith(prefix) }
                removedDirs += inner
                watchedDirs.removeAll { it.toString().startsWith(prefix) }
            }
            removedDirs
        }
        if (removed.isNotEmpty()) {
            watcher.remove(*removed.map { it.toString() }.toTypedArray())
        }
    }

    private suspend fun isDirectory(path: Path): Boolean =
        withContext(dispatcher) { SystemFileSystem.metadataOrNull(path)?.isDirectory == true }

    private suspend fun listEntries(dir: Path): Collection<Path> =
        withContext(dispatcher) { runCatching { SystemFileSystem.list(dir) }.getOrNull() }
            .orEmpty()
}
