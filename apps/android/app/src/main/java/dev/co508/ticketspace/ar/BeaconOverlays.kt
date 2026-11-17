package dev.co508.ticketspace.ar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.google.ar.core.TrackingState
import dev.co508.ticketspace.ar.math.projectBeacon
import dev.co508.ticketspace.data.SpaceState
import kotlin.math.roundToInt

@Composable
fun BeaconOverlays(
    runtime: ArRuntime,
    state: SpaceState,
    highlight: String?,
) {
    // A single projection per beacon/frame supplies arrows, badges and crosshair selection.
    val tick = runtime.tick
    val density = LocalDensity.current
    val margin = with(density) { 28.dp.toPx() }
    val projected =
        if (tick == 0L || runtime.tracking != TrackingState.TRACKING) {
            emptyList()
        } else {
            beacons(state, runtime.locator.room, runtime.locator.worldRoom, runtime.showThings, highlight).map {
                ProjectedBeacon(
                    it,
                    projectBeacon(it.world.t, runtime.view, runtime.projection, runtime.width, runtime.height, margin),
                )
            }
        }
    val hidden = runtime.placing || runtime.plan != null || runtime.selected != null || runtime.newThing != null
    val candidates = if (hidden) emptyList() else statusboardCandidates(projected, runtime.width, runtime.height)
    val nearest = selectStatusboard(candidates, runtime.width, runtime.height)
    var focus by remember { mutableStateOf(StatusboardFocus()) }
    LaunchedEffect(tick, nearest, hidden) {
        focus = advanceStatusboard(focus, nearest, tick / 1_000_000L)
    }
    val target = candidates.find { it.beacon.thing.id == focus.selected }?.beacon?.thing
    Box(Modifier.fillMaxSize()) {
        EdgeArrows(projected)
        projected.forEach { (beacon, point) ->
            val count = beaconCountBadge(point, openThingTodos(state.todos, beacon.thing.id).size)
            if (count != null) {
                Surface(
                    modifier = Modifier.offset { IntOffset(point.x.roundToInt(), point.y.roundToInt()) },
                    shape = CircleShape,
                    color = beaconColor(beacon.priority),
                    contentColor = Color.Black,
                ) {
                    Text(
                        "$count",
                        Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        if (target != null) {
            val todos = openThingTodos(state.todos, target.id)
            Card(
                onClick = { if (!runtime.busy) runtime.selected = target.id },
                modifier = Modifier.align(Alignment.Center).offset(y = 90.dp).widthIn(max = 240.dp),
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(target.displayLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${todos.size} open", style = MaterialTheme.typography.labelMedium)
                        // One dot per represented priority; avoid unbounded rows on busy Things.
                        todos.map { it.priority }.distinct().forEach { priority ->
                            Box(Modifier.size(8.dp).background(beaconColor(priority), CircleShape))
                        }
                    }
                    todos.firstOrNull()?.let { Text(it.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}
