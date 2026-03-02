package ru.herobrine1st.memeindex.server.repository

import io.github.smyrgeorge.sqlx4k.CrudRepository
import io.github.smyrgeorge.sqlx4k.QueryExecutor
import io.github.smyrgeorge.sqlx4k.annotation.Id
import io.github.smyrgeorge.sqlx4k.annotation.Query
import io.github.smyrgeorge.sqlx4k.annotation.Repository
import io.github.smyrgeorge.sqlx4k.annotation.Table
import ru.herobrine1st.memeindex.shared.model.Vector

@Table("embedding")
data class EmbeddingRow(
    @Id
    val id: Int,
    val fileId: Int,
    val text: String?,
    val imageVector: Vector?,
    val textVector: Vector?,
    val modelName: String,
)

@Repository
interface EmbeddingRepository : CrudRepository<EmbeddingRow> {
    @Query(
        "INSERT INTO embedding (file_id, image_embedding, text, text_embedding, model_name) " +
                "VALUES (:fileId, :imageVector::vector, :text, :textVector::vector, :model) " +
                "ON CONFLICT (file_id, model_name) DO UPDATE " +
                "SET image_embedding = EXCLUDED.image_embedding, text = EXCLUDED.text, text_embedding = EXCLUDED.text_embedding",
        checkSchema = false,
    )
    suspend fun executeUpsert(
        context: QueryExecutor,
        fileId: Int,
        imageVector: Vector?,
        text: String?,
        textVector: Vector?,
        model: String,
    ): Result<Long>

    @Query("DELETE FROM embedding WHERE file_id IN :fileIds")
    suspend fun deleteByFileIdIn(
        context: QueryExecutor,
        fileIds: List<Int>,
    ): Result<Long>

    @Query("DELETE FROM embedding WHERE file_id = :fileId")
    suspend fun deleteByFileId(
        context: QueryExecutor,
        fileId: Int,
    ): Result<Long>

    @Query("DELETE FROM embedding")
    suspend fun deleteAll(
        context: QueryExecutor,
    ): Result<Long>
}
