package dev.co508.ticketspace.ar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import dev.co508.ticketspace.ar.math.Pose
import dev.co508.ticketspace.ar.math.projectBeacon
import dev.co508.ticketspace.data.SpaceState
import dev.co508.ticketspace.data.Thing
import dev.co508.ticketspace.data.TodoStatus
import kotlin.math.cos
import kotlin.math.sin

data class Beacon(
    val thing: Thing,
    val world: Pose,
    val priority: Int?,
    val highlighted: Boolean,
)

fun beacons(
    state: SpaceState,
    room: String?,
    transform: Pose?,
    showThings: Boolean,
    highlight: String?,
): List<Beacon> {
    if (transform == null) return emptyList()
    val priorities =
        state.todos
            .filter { it.status == TodoStatus.OPEN && it.thing.isNotEmpty() }
            .groupBy { it.thing }
            .mapValues { (_, todos) -> todos.maxOf { it.priority } }
    return state.things.filter { it.room == room }.mapNotNull { thing ->
        val priority = priorities[thing.id]
        if (priority == null && !showThings && highlight != thing.id) return@mapNotNull null
        val pose = thing.poseInRoom?.let { runCatching { Pose.fromJson(it) }.getOrNull() } ?: return@mapNotNull null
        Beacon(thing, transform * pose, priority, highlight == thing.id)
    }
}

fun beaconColor(priority: Int?): Color =
    when (priority) {
        3 -> Color(0xFFFF3449)
        2 -> Color(0xFFFFB300)
        1 -> Color(0xFF00BFA5)
        0 -> Color(0xFF9E9E9E)
        else -> Color(0xFF455A64)
    }

@Composable
fun EdgeArrows(
    beacons: List<Beacon>,
    runtime: ArRuntime,
) {
    // Reading tick invalidates the overlay for every camera frame, even when Things are unchanged.
    val tick = runtime.tick
    Canvas(Modifier.fillMaxSize()) {
        if (tick == 0L) return@Canvas
        beacons.forEach { beacon ->
            val projected =
                projectBeacon(beacon.world.t, runtime.view, runtime.projection, size.width, size.height, 28.dp.toPx())
            if (projected.offscreen) {
                val center = Offset(projected.x, projected.y)
                val forward = Offset(cos(projected.angle), sin(projected.angle))
                val side = Offset(-forward.y, forward.x)
                val length = if (beacon.highlighted) 17.dp.toPx() else 12.dp.toPx()
                val tip = center + forward * length
                val left = center - forward * length + side * length * 0.7f
                val right = center - forward * length - side * length * 0.7f
                drawPath(
                    Path().apply {
                        moveTo(tip.x, tip.y)
                        lineTo(left.x, left.y)
                        lineTo(right.x, right.y)
                        close()
                    },
                    beaconColor(beacon.priority),
                )
                if (beacon.highlighted) {
                    drawCircle(
                        Color.White,
                        length * 1.4f,
                        center,
                        style =
                            androidx.compose.ui.graphics.drawscope
                                .Stroke(2.dp.toPx()),
                    )
                }
            }
        }
    }
}
