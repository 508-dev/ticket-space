package dev.co508.ticketspace.ar.math

import kotlin.math.abs
import kotlin.math.atan2

data class ScreenPoint(
    val x: Float,
    val y: Float,
    val offscreen: Boolean,
    val angle: Float,
)

/** OpenGL column-major matrices; screen origin is top-left. Behind-camera arrows keep their bearing. */
fun projectBeacon(
    point: List<Float>,
    view: FloatArray,
    projection: FloatArray,
    width: Float,
    height: Float,
    margin: Float = 32f,
): ScreenPoint {
    require(point.size == 3 && view.size == 16 && projection.size == 16)
    require(width > 0f && height > 0f)
    val camera = multiply(view, floatArrayOf(point[0], point[1], point[2], 1f))
    val clip = multiply(projection, camera)
    val divisor = abs(clip[3]).coerceAtLeast(0.0001f)
    val nx = clip[0] / divisor
    val ny = -clip[1] / divisor
    val behind = camera[2] >= 0f
    val offscreen = behind || abs(nx) > 1f || abs(ny) > 1f || abs(clip[2]) > divisor
    if (!offscreen) return ScreenPoint((nx + 1f) * width / 2f, (ny + 1f) * height / 2f, false, 0f)
    var dx = nx * width / 2f
    var dy = ny * height / 2f
    // Exactly behind or on the optical axis has no bearing: choose up deterministically.
    if (abs(dx) + abs(dy) < 0.001f) {
        dx = 0f
        dy = -1f
    }
    val halfW = (width / 2f - margin).coerceAtLeast(1f)
    val halfH = (height / 2f - margin).coerceAtLeast(1f)
    val factor = 1f / maxOf(abs(dx) / halfW, abs(dy) / halfH)
    return ScreenPoint(width / 2f + dx * factor, height / 2f + dy * factor, true, atan2(dy, dx))
}

private fun multiply(
    matrix: FloatArray,
    vector: FloatArray,
) = FloatArray(4) { row -> (0..3).sumOf { col -> (matrix[col * 4 + row] * vector[col]).toDouble() }.toFloat() }
