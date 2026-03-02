package ru.herobrine1st.memeindex.server

import kotlin.test.Test
import kotlin.test.assertEquals

class LenientUtf8Test {
    @Test
    fun decodesValidSequences() {
        // ASCII, 2-/3-byte BMP and a supplementary-plane (emoji) sequence all pass through.
        assertEquals("Hello, мир! 🚀", lenientUtf8Decode("Hello, мир! 🚀".encodeToByteArray()))
        assertEquals("", lenientUtf8Decode(ByteArray(0)))
    }

    @Test
    fun dropsStrayContinuationBytes() {
        assertEquals("AB", lenientUtf8Decode(byteArrayOf(0x41, 0x80.toByte(), 0x42)))
    }

    @Test
    fun dropsLongestSubpartOfIllFormedSequence() {
        // 0xE0 expects two continuation bytes; 0x41 is not one, so only the
        // well-formed prefix 0xE0 0x80 is skipped and "A" survives.
        assertEquals("A", lenientUtf8Decode(byteArrayOf(0xE0.toByte(), 0x80.toByte(), 0x41)))
    }

    @Test
    fun dropsOverlongEncodings() {
        // 0xC0 0x80 is an overlong NUL; 0xE0 0x80 0xA0 is an overlong 'a'.
        assertEquals("", lenientUtf8Decode(byteArrayOf(0xC0.toByte(), 0x80.toByte())))
        assertEquals("b", lenientUtf8Decode(byteArrayOf(0xE0.toByte(), 0x80.toByte(), 0xA0.toByte(), 0x62)))
    }

    @Test
    fun dropsSurrogatesAndOutOfRangeCodePoints() {
        // 0xED 0xA0 0x80 encodes U+D800 — a surrogate, which is not a valid scalar value.
        assertEquals("x", lenientUtf8Decode(byteArrayOf(0xED.toByte(), 0xA0.toByte(), 0x80.toByte(), 0x78)))
        // 0xF5 0x80 0x80 0x80 = U+110000 — above the maximum code point.
        assertEquals("y", lenientUtf8Decode(byteArrayOf(0xF5.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x79)))
    }

    @Test
    fun dropsTruncatedSequenceAtEnd() {
        // A 3-byte sequence truncated at EOF is dropped.
        assertEquals("ab", lenientUtf8Decode("ab".encodeToByteArray() + byteArrayOf(0xE2.toByte(), 0x82.toByte())))
    }
}
