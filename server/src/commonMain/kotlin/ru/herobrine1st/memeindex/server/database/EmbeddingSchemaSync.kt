package ru.herobrine1st.memeindex.server.database

import io.github.smyrgeorge.sqlx4k.QueryExecutor
import io.github.smyrgeorge.sqlx4k.Statement
import io.github.smyrgeorge.sqlx4k.Transaction
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.sqlx4k.postgres.IPostgresSQL
import ru.herobrine1st.memeindex.server.api.Embedder
import ru.herobrine1st.memeindex.server.Settings
import ru.herobrine1st.memeindex.server.generated.sqlx4k.EmbeddingRepositoryImpl
import ru.herobrine1st.memeindex.server.generated.sqlx4k.ModelMetaRepositoryImpl
import ru.herobrine1st.memeindex.server.repository.ModelMeta

private val logger = KotlinLogging.logger {}

private const val TABLE = "embedding"

private val createIndexStatements: List<String> = listOf(
    "CREATE INDEX IF NOT EXISTS image_embedding_index ON $TABLE USING hnsw (image_embedding vector_cosine_ops)",
    "CREATE INDEX IF NOT EXISTS text_embedding_index ON $TABLE USING hnsw (text_embedding vector_cosine_ops)",

)

private val dropIndexStatements: List<String> = listOf(
    "DROP INDEX IF EXISTS image_embedding_index",
    "DROP INDEX IF EXISTS text_embedding_index",
)

class EmbeddingSchemaSync(
    private val db: IPostgresSQL,
    private val settings: Settings,
    private val embedder: Embedder,
) {
    suspend fun sync() {
        val model = settings.embeddings.model
        val meta = findOneMeta()

        val modelChanged = meta != null && meta.modelName != model
        val dim = if (meta == null || modelChanged) resolveDimensions(model) else meta.dimensions
        if (modelChanged) {
            logger.warn { "Embedding model changed from ${meta.modelName} to $model — dropping and reindexing embeddings" }
        }

        when {
            meta == null -> createFresh(dim, model)
            modelChanged -> rebuild(dim, model)
            else -> logger.debug { "Embedding schema up to date (model $model, dimension $dim)" }
        }
    }

    private suspend fun createFresh(dim: Int, model: String) {
        db.transaction {
            ModelMetaRepositoryImpl.insert(this, ModelMeta(1, model, dim)).getOrThrow()
            alterVectorColumns(dim)
            createIndexes()
        }
        logger.info { "Typed $TABLE vector columns as vector($dim) for model $model" }
    }

    private suspend fun rebuild(dim: Int, model: String) {
        db.transaction {
            lockMetaRow()
            for (sql in dropIndexStatements) {
                execute(Statement.create(sql)).getOrThrow()
            }
            EmbeddingRepositoryImpl.deleteAll(this).getOrThrow()
            alterVectorColumns(dim)
            createIndexes()
            ModelMetaRepositoryImpl.update(this, ModelMeta(1, model, dim)).getOrThrow()
        }
        logger.info { "Rebuilt $TABLE with vector($dim) columns for model $model" }
    }

    private suspend fun Transaction.alterVectorColumns(dim: Int) {
        for (column in listOf("image_embedding", "text_embedding")) {
            execute(
                Statement.create("ALTER TABLE $TABLE ALTER COLUMN $column TYPE vector($dim)")
            ).getOrThrow()
        }
    }

    private suspend fun QueryExecutor.createIndexes() {
        for (sql in createIndexStatements) {
            execute(Statement.create(sql)).getOrThrow()
        }
    }

    private suspend fun Transaction.lockMetaRow() {
        ModelMetaRepositoryImpl.findAllByLockedById(this, 1).getOrThrow()
    }

    private suspend fun findOneMeta(): ModelMeta? =
        ModelMetaRepositoryImpl.findOneById(db, 1).getOrThrow()

    /**
     * The [Settings.Embeddings.dimensions] override, else one small probe
     * request (a fresh database or a model change — both are rare).
     */
    private suspend fun resolveDimensions(model: String): Int {
        settings.embeddings.dimensions?.let { return it }
        logger.info { "Probing embedding dimension for model '$model' (single request)" }
        return embedder.embedQuery("x").values.size
    }
}
