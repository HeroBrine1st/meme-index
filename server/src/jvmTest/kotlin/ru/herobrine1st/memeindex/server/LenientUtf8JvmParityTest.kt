package ru.herobrine1st.memeindex.server

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The common [lenientUtf8Decode] must match the JVM reference decoder
 * (`CodingErrorAction.IGNORE`): the JVM decoder is the behaviour baseline the
 * Python `errors="ignore"` port targeted.
 */
class LenientUtf8JvmParityTest {
    @Test
    fun matchesJvmDecoderOnCorpus() {
        val valid = listOf(
            "",
            "abc",
            "Hello, мир! 🚀",
            "line1\nline2\twith tabs",
            "\u007F\u0080\u00FF", // 1-byte boundary and 2-byte code points
        )
        val corrupted = listOf(
            byteArrayOf(0x41, 0x80.toByte(), 0x42), // stray continuation byte
            byteArrayOf(0xE0.toByte(), 0x80.toByte(), 0x41), // ill-formed: longest subpart skipped
            byteArrayOf(0xC0.toByte(), 0x80.toByte()), // overlong NUL
            byteArrayOf(0xE0.toByte(), 0x80.toByte(), 0xA0.toByte(), 0x62), // overlong 'a'
            byteArrayOf(0xED.toByte(), 0xA0.toByte(), 0x80.toByte(), 0x78), // surrogate
            byteArrayOf(0xF5.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x79), // above the maximum code point
            byteArrayOf(0xC2.toByte(), 0x28), // 2-byte lead not followed by a continuation
            "A".encodeToByteArray() + "мир".encodeToByteArray(), // valid mix
            "ab".encodeToByteArray() + byteArrayOf(0xE2.toByte(), 0x82.toByte()), // truncated at EOF
            byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x41), // invalid leads + NUL
        )
        for (input in valid.map(String::encodeToByteArray) + corrupted) {
            assertEquals(reference(input), lenientUtf8Decode(input), "input=${input.contentToString()}")
        }
    }

    private fun reference(bytes: ByteArray): String =
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.IGNORE)
            .onUnmappableCharacter(CodingErrorAction.IGNORE)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
}
