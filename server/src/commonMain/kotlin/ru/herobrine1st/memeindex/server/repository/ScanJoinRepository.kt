package ru.herobrine1st.memeindex.server.repository

import io.github.smyrgeorge.sqlx4k.CrudRepository
import io.github.smyrgeorge.sqlx4k.QueryExecutor
import io.github.smyrgeorge.sqlx4k.annotation.Converter
import io.github.smyrgeorge.sqlx4k.annotation.Id
import io.github.smyrgeorge.sqlx4k.annotation.Query
import io.github.smyrgeorge.sqlx4k.annotation.Repository
import io.github.smyrgeorge.sqlx4k.annotation.Table
import kotlin.time.Instant
import kotlinx.io.bytestring.ByteString

@Table("file")
data class ScanJoinRow(
    @Id
    val id: Int?,
    val oldPath: String?,
    @Converter(ByteStringEnc::class)
    val oldSha256: ByteString?,
    val newPath: String?,
    val newMtime: Instant?,
)

@Repository
interface ScanJoinRepository : CrudRepository<ScanJoinRow> {
    @Query("SELECT f.id AS id, f.path AS old_path, f.sha256 AS old_sha256, c.path AS new_path, c.mtime AS new_mtime FROM file f FULL OUTER JOIN current_scan c ON f.path = c.path WHERE (c.path IS NULL OR f.path IS NULL OR c.mtime > f.mtime) AND " + "c.path IS NULL ORDER BY COALESCE(c.path, f.path) LIMIT :limit OFFSET :offset", checkSchema = false)
    suspend fun findAllByMissingFromDisk(
        context: QueryExecutor,
        limit: Int,
        offset: Int,
    ): Result<List<ScanJoinRow>>

    @Query("SELECT f.id AS id, f.path AS old_path, f.sha256 AS old_sha256, c.path AS new_path, c.mtime AS new_mtime FROM file f FULL OUTER JOIN current_scan c ON f.path = c.path WHERE (c.path IS NULL OR f.path IS NULL OR c.mtime > f.mtime) AND " + "c.path IS NOT NULL ORDER BY COALESCE(c.path, f.path) LIMIT :limit OFFSET :offset", checkSchema = false)
    suspend fun findAllByChangedOnDisk(
        context: QueryExecutor,
        limit: Int,
        offset: Int,
    ): Result<List<ScanJoinRow>>
}
