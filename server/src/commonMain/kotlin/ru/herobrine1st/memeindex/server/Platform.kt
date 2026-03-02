package ru.herobrine1st.memeindex.server

import kotlinx.io.Source
import kotlinx.io.bytestring.ByteString
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.time.Instant

/**
 * The last modification time of the file at [path] (millisecond precision is
 * enough for the import logic; filesystem mtimes coarser than that don't exist).
 */
expect fun lastModifiedTime(path: Path): Instant

/**
 * Resolves symlinks in [path] (guards the directory walks against symlink
 * loops). Returns null if the path does not exist.
 */
fun resolveRealPath(path: Path): Path? =
    runCatching { SystemFileSystem.resolve(path) }.getOrNull()

/**
 * 32-bit rotate right (FIPS 180-4 `ROTR`); Kotlin has no built-in rotate.
 */
private fun rotr32(x: Int, n: Int): Int = (x ushr n) or (x shl (32 - n))

/**
 * SHA-256 digest of [data] (FIPS 180-4), pure Kotlin (no platform crypto APIs).
 */
expect fun sha256(data: ByteString): ByteString

/**
 * Validates an image and re-encodes it as PNG, scaling it into the model's
 * input budget like surya's client does (see the JVM actual for the exact
 * rules). Returns null for invalid images.
 */
expect fun toPng(data: Source): ByteString?

/**
 * Makes [path] absolute (relative to the working directory) and normalizes it
 * (resolves `.` and `..` segments).
 */
expect fun normalizeAbsolutePath(path: String): String

/**
 * Decodes [bytes] as UTF-8, dropping malformed input instead of failing (the JVM
 * `CodingErrorAction.IGNORE` semantics: an ill-formed subsequence is skipped in
 * its longest well-formed prefix, everything else is decoded).
 */
fun lenientUtf8Decode(bytes: ByteArray): String {
    val sb = StringBuilder()
    var i = 0
    while (i < bytes.size) {
        val lead = bytes[i].toInt() and 0xFF
        val length = when {
            lead and 0x80 == 0 -> 1
            lead and 0xE0 == 0xC0 -> 2
            lead and 0xF0 == 0xE0 -> 3
            lead and 0xF8 == 0xF0 -> 4
            else -> {
                // A continuation byte (10xxxxxx) cannot start a sequence.
                i += 1
                continue
            }
        }
        if (i + length > bytes.size) break // truncated at the end — drop the rest

        var code = when (length) {
            1 -> lead
            2 -> lead and 0x1F
            3 -> lead and 0x0F
            else -> lead and 0x07
        }
        var valid = true
        for (j in 1 until length) {
            val b = bytes[i + j].toInt() and 0xFF
            if (b and 0xC0 != 0x80) {
                valid = false
                break
            }
            code = (code shl 6) or (b and 0x3F)
        }
        val overlong = when (length) {
            1 -> false
            2 -> code < 0x80
            3 -> code < 0x800
            else -> code < 0x10000
        }
        if (valid && !overlong && code !in 0xD800..0xDFFF && code <= 0x10FFFF) {
            if (code < 0x10000) {
                sb.append(code.toChar())
            } else {
                // Supplementary code points are appended as a surrogate pair
                // (appendCodePoint is internal on native).
                val offset = code - 0x10000
                sb.append((0xD800 + (offset ushr 10)).toChar())
                sb.append((0xDC00 + (offset and 0x3FF)).toChar())
            }
        }
        if (!valid) {
            // Skip the longest well-formed prefix of the ill-formed subsequence:
            // the lead byte plus the run of continuation-pattern bytes after it.
            var skip = 1
            while (skip < length && bytes[i + skip].toInt() and 0xC0 == 0x80) skip++
            i += skip
        } else {
            i += length
        }
    }
    return sb.toString()
}

// --------------------------------------------------------------------------------
// Directory watching. kfswatch is multiplatform (inotify on linuxX64), so the
// wrapper — including the recursive directory handling — is common code.
// --------------------------------------------------------------------------------

enum class MediaFileEvent { Create, Modify, Delete }

data class MediaFileChange(val event: MediaFileEvent, val path: Path)

data class WatcherError(val targetDirectory: String, val message: String)
