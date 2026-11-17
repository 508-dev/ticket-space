package dev.co508.ticketspace.ar

import dev.co508.ticketspace.ar.math.ScreenPoint
import dev.co508.ticketspace.data.Todo
import dev.co508.ticketspace.data.TodoStatus
import dev.co508.ticketspace.data.todoOrder

data class ProjectedBeacon(
    val beacon: Beacon,
    val point: ScreenPoint,
)

fun openThingTodos(
    todos: List<Todo>,
    thing: String,
): List<Todo> = todos.filter { it.thing == thing && it.status == TodoStatus.OPEN }.sortedWith(todoOrder)

fun statusboardCandidates(
    beacons: List<ProjectedBeacon>,
    width: Float,
    height: Float,
): List<ProjectedBeacon> {
    if (width <= 0f || height <= 0f) return emptyList()
    val radius = minOf(width, height) * 0.12f
    return beacons.filter {
        val p = it.point
        !p.offscreen &&
            !p.behindCamera &&
            p.distance in 0f..8f &&
            centreDistanceSquared(p, width, height) <= radius * radius
    }
}

/** Reuses the edge-arrow projection, rather than doing another camera transform. */
fun selectStatusboard(
    beacons: List<ProjectedBeacon>,
    width: Float,
    height: Float,
): String? =
    statusboardCandidates(beacons, width, height)
        .minWithOrNull(
            compareBy<ProjectedBeacon> {
                centreDistanceSquared(it.point, width, height)
            }.thenBy { it.beacon.thing.id },
        )?.beacon
        ?.thing
        ?.id

private fun centreDistanceSquared(
    p: ScreenPoint,
    width: Float,
    height: Float,
): Float = (p.x - width / 2f) * (p.x - width / 2f) + (p.y - height / 2f) * (p.y - height / 2f)

data class StatusboardFocus(
    val selected: String? = null,
    val pending: String? = null,
    val sinceMs: Long = 0L,
)

/** A new target must remain nearest for 150 ms; leaving all targets hides immediately. */
fun advanceStatusboard(
    focus: StatusboardFocus,
    candidate: String?,
    nowMs: Long,
): StatusboardFocus =
    when {
        candidate == null -> StatusboardFocus()
        candidate == focus.selected -> StatusboardFocus(selected = candidate)
        candidate != focus.pending || nowMs < focus.sinceMs -> StatusboardFocus(focus.selected, candidate, nowMs)
        nowMs - focus.sinceMs >= 150L -> StatusboardFocus(selected = candidate)
        else -> focus
    }

/** Null means no badge; the existing edge arrows cover off-screen/behind-camera spots. */
fun beaconCountBadge(
    point: ScreenPoint,
    openCount: Int,
): Int? = openCount.takeIf { it >= 2 && !point.offscreen && !point.behindCamera }
