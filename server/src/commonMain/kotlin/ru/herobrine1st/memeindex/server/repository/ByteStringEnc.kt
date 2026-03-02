package ru.herobrine1st.memeindex.server.repository

import io.github.smyrgeorge.sqlx4k.ResultSet
import io.github.smyrgeorge.sqlx4k.ValueEncoder
import kotlinx.io.bytestring.ByteString

object ByteStringEnc : ValueEncoder<ByteString> {
    override fun encode(value: ByteString): Any = value.toByteArray()

    override fun decode(value: ResultSet.Row.Column): ByteString {
        val hex = value.asString().removePrefix("\\x")
        require(hex.length % 2 == 0) { "Invalid hex string: $hex" }
        return ByteString(ByteArray(hex.length / 2) { i ->
            ((hex[i * 2].digitToInt(16) shl 4) or hex[i * 2 + 1].digitToInt(16)).toByte()
        })
    }
}
