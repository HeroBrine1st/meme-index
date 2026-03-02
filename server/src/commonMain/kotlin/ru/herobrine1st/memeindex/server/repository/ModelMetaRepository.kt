package ru.herobrine1st.memeindex.server.repository

import io.github.smyrgeorge.sqlx4k.CrudRepository
import io.github.smyrgeorge.sqlx4k.QueryExecutor
import io.github.smyrgeorge.sqlx4k.annotation.Id
import io.github.smyrgeorge.sqlx4k.annotation.Query
import io.github.smyrgeorge.sqlx4k.annotation.Repository
import io.github.smyrgeorge.sqlx4k.annotation.Table

@Table("embedding_model_meta")
data class ModelMeta(
    @Id
    val id: Int,
    val modelName: String,
    val dimensions: Int,
)

@Repository
interface ModelMetaRepository : CrudRepository<ModelMeta> {
    @Query("SELECT id, model_name, dimensions FROM embedding_model_meta WHERE id = :id")
    suspend fun findOneById(
        context: QueryExecutor,
        id: Int,
    ): Result<ModelMeta?>

    @Query("SELECT id, model_name, dimensions FROM embedding_model_meta WHERE id = :id FOR UPDATE")
    suspend fun findAllByLockedById(
        context: QueryExecutor,
        id: Int,
    ): Result<List<ModelMeta>>
}
