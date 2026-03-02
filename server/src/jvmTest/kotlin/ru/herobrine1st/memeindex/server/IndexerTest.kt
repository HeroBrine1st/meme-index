package ru.herobrine1st.memeindex.server

import io.github.smyrgeorge.sqlx4k.ResultSet.Row.Column
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.files.Path
import ru.herobrine1st.memeindex.shared.model.Vector
import ru.herobrine1st.memeindex.server.repository.VectorEncoder

class IndexerTest {
    @Test
    fun embeddingEncRoundTripsThroughPgvectorLiteral() {
        val vector = Vector(listOf(1f, -2.5f, 0f))
        assertEquals("[1.0,-2.5,0.0]", VectorEncoder.encode(vector))
        val decoded = VectorEncoder.decode(Column(0, "v", "vector", "[1.0,-2.5,0.0]"))
        assertEquals(vector, decoded)
    }

    @Test
    fun extensionIsLowercaseDottedSuffix() {
        assertEquals(".jpg", Indexer.extension(Path("a/IMG.JPG")))
        assertEquals(".webp", Indexer.extension(Path("img.WEBP")))
        assertEquals(".txt", Indexer.extension(Path("notes.txt")))
        assertEquals("", Indexer.extension(Path("noext")))
    }

    private fun encodedImage(format: String): ByteArray {
        val image = BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, 0xFF0000)
        image.setRGB(7, 7, 0x0000FF)
        val out = ByteArrayOutputStream()
        assertTrue(ImageIO.write(image, format, out))
        return out.toByteArray()
    }

    @Test
    fun toPngReencodesJpgAndPng() {
        for (format in listOf("png", "jpg")) {
            val png = toPng((Buffer().apply { write(encodedImage(format)) } as RawSource).buffered())
            assertTrue(png != null)
            val back = ImageIO.read(ByteArrayInputStream(png!!.toByteArray()))
            assertTrue(back != null)
            // 8x8 is below surya's min pixel budget (1792x28) and gets upscaled
            // to the nearest 28px grid size.
            assertEquals(224, back.width)
            assertEquals(224, back.height)
        }
    }

    @Test
    fun toPngRejectsGarbageAndEmptyInput() {
        assertNull(toPng((Buffer().apply { write(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)) } as RawSource).buffered()))
        assertNull(toPng((Buffer() as RawSource).buffered()))
    }

    @Test
    fun scaledImageDimensionsFollowsSuryaBudgetAndGrid() {
        // Already inside the budget and on the grid: unchanged.
        assertNull(scaledImageDimensions(2800, 2100))
        // Oversized: fitted into the 3072x2048 pixel budget (block drop
        // prefers keeping the aspect ratio).
        assertEquals(3052 to 2044, scaledImageDimensions(6000, 4000))
        // Tall image inside the budget: just snapped to the grid.
        assertEquals(308 to 4004, scaledImageDimensions(300, 4000))
        // Tiny images are upscaled to surya's minimum pixel budget.
        assertEquals(224 to 224, scaledImageDimensions(100, 100))
        assertEquals(224 to 224, scaledImageDimensions(8, 8))
    }
}
