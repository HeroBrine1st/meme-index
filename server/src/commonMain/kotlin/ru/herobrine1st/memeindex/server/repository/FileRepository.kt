package ru.herobrine1st.memeindex.server.repository

import io.github.smyrgeorge.sqlx4k.CrudRepository
import io.github.smyrgeorge.sqlx4k.QueryExecutor
import io.github.smyrgeorge.sqlx4k.annotation.Converter
import io.github.smyrgeorge.sqlx4k.annotation.Id
import io.github.smyrgeorge.sqlx4k.annotation.Query
import io.github.smyrgeorge.sqlx4k.annotation.Repository
import io.github.smyrgeorge.sqlx4k.annotation.Table
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.io.bytestring.ByteString

@Table("file")
data class MediaFile(
    @Id
    val id: Int,
    val path: String,
    @Converter(ByteStringEnc::class)
    val sha256: ByteString,
    val mtime: Instant,
)

@Repository
interface FileRepository : CrudRepository<MediaFile> {
    @Query("SELECT id, path, sha256, mtime FROM file WHERE path = :path")
    suspend fun findAllByPath(
        context: QueryExecutor,
        path: String,
    ): Result<List<MediaFile>>

    @Query("SELECT id, path, sha256, mtime FROM file WHERE sha256 = :sha256")
    suspend fun findAllBySha256(
        context: QueryExecutor,
        sha256: ByteString,
    ): Result<List<MediaFile>>

    @Query("SELECT file.id, file.path, file.sha256, file.mtime FROM file LEFT JOIN embedding e ON e.file_id = file.id AND e.model_name = :model WHERE e IS NULL ORDER BY file.id", checkSchema = false)
    fun streamAllByPendingEmbedding(
        context: QueryExecutor,
        model: String,
    ): Flow<MediaFile>

    @Query("SELECT COUNT(file) FROM file LEFT JOIN embedding e ON e.file_id = file.id AND e.model_name = :model WHERE e IS NULL", checkSchema = false)
    suspend fun countByPendingEmbedding(
        context: QueryExecutor,
        model: String,
    ): Result<Long>

    @Query("SELECT file.id, file.path, file.sha256, file.mtime FROM file WHERE file.id = :id AND NOT EXISTS (SELECT 1 FROM embedding e WHERE e.file_id = file.id AND e.model_name = :model)", checkSchema = false)
    suspend fun findOneByIdAndPendingEmbedding(
        context: QueryExecutor,
        id: Int,
        model: String,
    ): Result<MediaFile?>

    @Query("DELETE FROM file WHERE path = :path")
    suspend fun deleteByPath(
        context: QueryExecutor,
        path: String,
    ): Result<Long>

    @Query("DELETE FROM file WHERE id IN :ids")
    suspend fun deleteByIdIn(
        context: QueryExecutor,
        ids: List<Int>,
    ): Result<Long>
}
