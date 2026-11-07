package dev.co508.ticketspace.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

data class ImageSize(
    val width: Int,
    val height: Int,
)

fun downscaledSize(
    width: Int,
    height: Int,
    maxEdge: Int = 1600,
): ImageSize {
    require(width > 0 && height > 0 && maxEdge > 0)
    val scale = minOf(1.0, maxEdge.toDouble() / maxOf(width, height))
    return ImageSize((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1))
}

fun cameraFile(context: Context): File =
    File.createTempFile(
        "capture-",
        ".jpg",
        File(context.cacheDir, "camera").apply { mkdirs() },
    )

fun cameraUri(
    context: Context,
    file: File,
): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

suspend fun preparePhoto(
    context: Context,
    source: File,
): File =
    withContext(Dispatchers.IO) {
        // ImageDecoder applies EXIF orientation and decodes at target size, rather than allocating
        // a full-resolution bitmap first. Its listener's size reflects the oriented image.
        val bitmap =
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
                val size = downscaledSize(info.size.width, info.size.height)
                decoder.setTargetSize(size.width, size.height)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        val output = cameraFile(context)
        try {
            output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it)) }
        } finally {
            bitmap.recycle()
            source.delete()
        }
        output
    }
