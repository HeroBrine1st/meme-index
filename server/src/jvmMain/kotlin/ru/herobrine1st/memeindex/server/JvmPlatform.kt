package ru.herobrine1st.memeindex.server

import kotlinx.io.Source
import kotlinx.io.asInputStream
import kotlinx.io.bytestring.ByteString
import kotlinx.io.files.Path
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.Instant
import java.nio.file.Path as JavaPath


actual fun lastModifiedTime(path: Path): Instant =
    Instant.fromEpochMilliseconds(
        Files.getLastModifiedTime(JavaPath.of(path.toString())).toInstant().toEpochMilli(),
    )

actual fun toPng(data: Source): ByteString? {
    val image: BufferedImage = data.asInputStream().use {
        ImageIO.read(it)
    } ?: return null
    val scaled = scaledImageDimensions(image.width, image.height)?.let { (outWidth, outHeight) ->
        val target = BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_RGB)
        val graphics = target.createGraphics()
        try {
            // surya resizes with LANCZOS; bicubic is the closest Java2D offers.
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC,
            )
            graphics.drawImage(image, 0, 0, outWidth, outHeight, null)
        } finally {
            graphics.dispose()
        }
        target
    } ?: image
    val output = ByteArrayOutputStream()
    return if (ImageIO.write(scaled, "png", output)) ByteString(output.toByteArray()) else null
}

/**
 * Computes the dimensions an image should be resized to before being sent to
 * the llama.cpp server, mirroring surya's `scale_to_fit` defaults: the image
 * is fitted into a 3072x2048 pixel budget (images smaller than 1792x28 pixels
 * are scaled up), and both sides are snapped to a multiple of the model's
 * 28px patch grid, dropping grid blocks that hurt the aspect ratio least when
 * the budget is exceeded.
 *
 * Returns null when the image already has the target dimensions.
 */
internal fun scaledImageDimensions(width: Int, height: Int): Pair<Int, Int>? {
    if (width <= 0 || height <= 0) return null
    val originalAspect = width.toDouble() / height
    val pixels = width.toLong() * height
    val maxPixels = 3072L * 2048L
    val minPixels = 1792L * 28L
    val scale = when {
        pixels > maxPixels -> sqrt(maxPixels.toDouble() / pixels)
        pixels < minPixels -> sqrt(minPixels.toDouble() / pixels)
        else -> 1.0
    }
    var wBlocks = (width * scale / 28.0).roundToInt().coerceAtLeast(1)
    var hBlocks = (height * scale / 28.0).roundToInt().coerceAtLeast(1)
    while (wBlocks * hBlocks * 28 * 28 > maxPixels) {
        if (wBlocks == 1 && hBlocks == 1) break
        if (wBlocks == 1) {
            hBlocks--
            continue
        }
        if (hBlocks == 1) {
            wBlocks--
            continue
        }
        val loseWidth = abs((wBlocks - 1).toDouble() / hBlocks - originalAspect)
        val loseHeight = abs(wBlocks.toDouble() / (hBlocks - 1) - originalAspect)
        if (loseWidth < loseHeight) wBlocks-- else hBlocks--
    }
    val newWidth = wBlocks * 28
    val newHeight = hBlocks * 28
    if (newWidth == width && newHeight == height) return null
    return newWidth to newHeight
}

actual fun normalizeAbsolutePath(path: String): String =
    JavaPath.of(path).toAbsolutePath().normalize().toString()
