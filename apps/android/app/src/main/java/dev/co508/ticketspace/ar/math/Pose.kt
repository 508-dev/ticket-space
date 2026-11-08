package dev.co508.ticketspace.ar.math

import dev.co508.ticketspace.data.wireJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.math.abs
import kotlin.math.sqrt

/** Rigid transform: translation in metres and unit quaternion in x,y,z,w order. */
@Serializable
data class Pose(
    val t: List<Float> = listOf(0f, 0f, 0f),
    val q: List<Float> = listOf(0f, 0f, 0f, 1f),
) {
    init {
        require(t.size == 3 && q.size == 4 && (t + q).all { it.isFinite() })
        require(abs(q.sumOf { (it * it).toDouble() } - 1.0) < 0.001) { "Pose needs a unit quaternion" }
    }

    operator fun times(other: Pose): Pose {
        val (x, y, z, w) = q
        val (a, b, c, d) = other.q
        val rotation =
            listOf(
                w * a + x * d + y * c - z * b,
                w * b - x * c + y * d + z * a,
                w * c + x * b - y * a + z * d,
                w * d - x * a - y * b - z * c,
            )
        val norm = sqrt(rotation.sumOf { (it * it).toDouble() }).toFloat()
        return Pose(transformPoint(other.t), rotation.map { it / norm })
    }

    fun inverse(): Pose {
        val rotation = Pose(q = listOf(-q[0], -q[1], -q[2], q[3]))
        return rotation.copy(t = rotation.transformPoint(t.map { -it }))
    }

    fun transformPoint(point: List<Float>): List<Float> {
        require(point.size == 3)
        val u = q.take(3)
        val uv = cross(u, point)
        val uuv = cross(u, uv)
        return List(3) { point[it] + 2f * (q[3] * uv[it] + uuv[it]) + t[it] }
    }

    fun toJson(): JsonObject = wireJson.encodeToJsonElement(this).jsonObject

    companion object {
        val IDENTITY = Pose()

        fun fromJson(json: JsonObject): Pose = wireJson.decodeFromJsonElement(json)
    }
}

private fun cross(
    a: List<Float>,
    b: List<Float>,
) = listOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])

fun roomFrame(
    resolvedWorld: Pose,
    anchorInRoom: Pose,
): Pose = resolvedWorld * anchorInRoom.inverse()

fun carryOver(
    thingInOldRoom: Pose,
    worldOldRoom: Pose,
    worldNewRoom: Pose,
): Pose = worldNewRoom.inverse() * worldOldRoom * thingInOldRoom
