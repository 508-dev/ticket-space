package dev.co508.ticketspace.ar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.view.Surface
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.exceptions.NotYetAvailableException
import dev.co508.ticketspace.data.cameraFile
import dev.co508.ticketspace.data.downscaledSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** Copy the CPU image immediately, respecting padding and pixel stride; release it before encoding. */
suspend fun captureArPhoto(
    context: Context,
    session: Session,
    frame: Frame,
): File {
    val image =
        try {
            frame.acquireCameraImage()
        } catch (e: NotYetAvailableException) {
            throw IllegalStateException("Camera image is not ready. Keep the scene in view and retry.", e)
        }
    val width = image.width
    val height = image.height
    val nv21 = ByteArray(width * height * 3 / 2)
    try {
        image.planes.forEachIndexed { index, plane ->
            val buffer = plane.buffer.duplicate()
            val base = buffer.position()
            val w = if (index == 0) width else width / 2
            val h = if (index == 0) height else height / 2
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val dest =
                        if (index ==
                            0
                        ) {
                            y * width + x
                        } else {
                            width * height + y * width + x * 2 + if (index == 1) 1 else 0
                        }
                    nv21[dest] = buffer.get(base + y * plane.rowStride + x * plane.pixelStride)
                }
            }
        }
    } finally {
        image.close()
    }
    val manager = context.getSystemService(CameraManager::class.java)
    val sensor =
        manager.getCameraCharacteristics(session.cameraConfig.cameraId).get(CameraCharacteristics.SENSOR_ORIENTATION)
            ?: 90
    val display =
        when (context.display.rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    return withContext(Dispatchers.Default) {
        val bytes =
            ByteArrayOutputStream().use {
                check(
                    YuvImage(
                        nv21,
                        ImageFormat.NV21,
                        width,
                        height,
                        null,
                    ).compressToJpeg(Rect(0, 0, width, height), 95, it),
                )
                it.toByteArray()
            }
        val source = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        val rotated =
            Bitmap.createBitmap(
                source,
                0,
                0,
                width,
                height,
                Matrix().apply {
                    postRotate(
                        (
                            (
                                sensor - display +
                                    360
                            ) %
                                360
                        ).toFloat(),
                    )
                },
                true,
            )
        val size = downscaledSize(rotated.width, rotated.height)
        val scaled = Bitmap.createScaledBitmap(rotated, size.width, size.height, true)
        val file = cameraFile(context)
        try {
            file.outputStream().use { check(scaled.compress(Bitmap.CompressFormat.JPEG, 85, it)) }
            file
        } catch (e: Exception) {
            file.delete()
            throw e
        } finally {
            if (scaled !== rotated) scaled.recycle()
            if (rotated !== source) rotated.recycle()
            source.recycle()
        }
    }
}
