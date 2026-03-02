package ru.herobrine1st.memeindex.server.repository

import io.github.smyrgeorge.sqlx4k.CrudRepository
import io.github.smyrgeorge.sqlx4k.annotation.Id
import io.github.smyrgeorge.sqlx4k.annotation.Repository
import io.github.smyrgeorge.sqlx4k.annotation.Table
import kotlin.time.Instant

@Table("current_scan")
data class ScanEntry(
    @Id(insert = true)
    val path: String,
    val mtime: Instant,
)

@Repository
interface ScanRepository : CrudRepository<ScanEntry>
