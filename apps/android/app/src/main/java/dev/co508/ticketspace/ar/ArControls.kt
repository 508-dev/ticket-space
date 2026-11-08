package dev.co508.ticketspace.ar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import dev.co508.ticketspace.data.Room
import dev.co508.ticketspace.data.SpaceState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArControls(
    runtime: ArRuntime,
    state: SpaceState,
    rooms: List<Room>,
    highlight: String?,
    back: () -> Unit,
) {
    val context = LocalContext.current
    var tips by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var unplaced by remember { mutableStateOf(false) }
    val room = rooms.find { it.id == runtime.locator.room }
    val located = runtime.locator.worldRoom != null
    val plan = runtime.plan
    LaunchedEffect(located) {
        tips = false
        if (!located) {
            delay(15_000)
            tips = true
        }
    }
    BackHandler(runtime.draft != null || runtime.placing) {
        if (!runtime.busy) {
            runtime.draft?.photo?.delete()
            runtime.draft = null
            runtime.placing = false
            runtime.moving = null
        }
    }
    Box(Modifier.fillMaxSize()) {
        Card(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    TextButton(onClick = back, enabled = !runtime.busy) { Text("‹ Back") }
                    if (plan == null) TextButton(onClick = { picker = true }) { Text(room?.name ?: "Choose room") }
                }
                Text(
                    when {
                        plan != null -> "${plan.name} · Anchor ${(runtime.hosted.size + 1).coerceAtMost(3)} of 3"
                        located -> "Located: ${room?.name.orEmpty()}"
                        else -> "Locating room…"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (runtime.tracking !=
                    TrackingState.TRACKING
                ) {
                    Text("Tracking paused. Move slowly and keep textured surfaces in view.")
                }
                if (!located &&
                    plan == null
                ) {
                    Text("Room not located. Room-level todos are still available in Choose room.")
                }
                if (tips &&
                    plan == null
                ) {
                    Text(
                        "Move slowly, point at walls and furniture. Try another angle, better lighting, or select a room manually.",
                    )
                }
                if (plan != null) {
                    Text(
                        if (runtime.candidate ==
                            null
                        ) {
                            "Tap a surface; spread the anchors around the room."
                        } else {
                            "Walk around the spot, keep it in view."
                        },
                    )
                    if (plan.provider == "cloud") {
                        Text("Feature quality: ${runtime.quality.name.lowercase()}")
                        LinearProgressIndicator(progress = {
                            when (runtime.quality) {
                                Session.FeatureMapQuality.GOOD -> 1f
                                Session.FeatureMapQuality.SUFFICIENT -> 0.6f
                                else -> 0.15f
                            }
                        }, modifier = Modifier.fillMaxWidth())
                    }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.liveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                runtime.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        if (runtime.placing) {
            Text(
                if (runtime.reticle != null) "⊕" else "+",
                modifier = Modifier.align(Alignment.Center),
                color =
                    if (runtime.reticle !=
                        null
                    ) {
                        Color.Green
                    } else {
                        Color.White
                    },
                style = MaterialTheme.typography.displayMedium,
            )
        }
        Card(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    plan != null -> {
                        if (runtime.hosted.size < 3) {
                            Button(
                                onClick = runtime::host,
                                enabled =
                                    !runtime.busy &&
                                        runtime.candidate != null &&
                                        runtime.tracking == TrackingState.TRACKING &&
                                        (
                                            plan.provider == "local" ||
                                                runtime.quality != Session.FeatureMapQuality.INSUFFICIENT
                                        ),
                            ) {
                                Text(if (runtime.busy) "Hosting…" else "Host anchor ${runtime.hosted.size + 1}")
                            }
                        } else {
                            if (plan.existing !=
                                null
                            ) {
                                Text(
                                    if (runtime.locator.frameFor(
                                            plan.existing.id,
                                            runtime.frame
                                                ?.camera
                                                ?.pose
                                                ?.roomPose()
                                                ?: dev.co508.ticketspace.ar.math.Pose.IDENTITY,
                                        ) !=
                                        null
                                    ) {
                                        "Old anchor located: Things will be carried over."
                                    } else {
                                        "No old anchor located: saving will mark Things unplaced."
                                    },
                                )
                            }
                            Button(
                                onClick = { runtime.finishRegistration(context) },
                                enabled =
                                    !runtime.busy && runtime.tracking == TrackingState.TRACKING,
                            ) {
                                Text(if (runtime.busy) "Saving…" else "Save room and camera photo")
                            }
                        }
                    }
                    runtime.placing -> {
                        Text(
                            if (runtime.moving ==
                                null
                            ) {
                                "Point at the spot for a new Thing"
                            } else {
                                "Point at the new position"
                            },
                        )
                        Row {
                            Button(
                                onClick = { runtime.place(context) },
                                enabled =
                                    !runtime.busy && located && runtime.reticle != null,
                            ) { Text("Confirm placement") }
                            TextButton(onClick = {
                                runtime.placing = false
                                runtime.moving = null
                            }, enabled = !runtime.busy) { Text("Cancel") }
                        }
                    }
                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { runtime.placing = true },
                                enabled = located && !runtime.busy,
                            ) { Text("+ Thing") }
                            Text(" Show things ")
                            Switch(checked = runtime.showThings, onCheckedChange = { runtime.showThings = it })
                        }
                        Row {
                            TextButton(onClick = {
                                unplaced = true
                            }) {
                                Text(
                                    "Unplaced (${state.things.count { it.room == room?.id && it.poseInRoom == null }})",
                                )
                            }
                            TextButton(onClick = { picker = true }) { Text("Room todos") }
                            if (!located) {
                                TextButton(onClick = {
                                    runtime.locator.retry()
                                    runtime.locator.reconcile(
                                        state.anchors.filter { row ->
                                            rooms.any {
                                                it.id ==
                                                    row.room
                                            }
                                        },
                                    )
                                }) { Text("Retry") }
                            }
                        }
                        if (highlight != null) {
                            val thing = state.things.find { it.id == highlight }
                            Text(
                                when {
                                    thing == null -> "This Thing is unavailable."
                                    thing.poseInRoom == null -> "Choose this Thing in Unplaced to position it."
                                    !located || thing.room != room?.id -> "Locate its room to see the beacon."
                                    else -> "Follow the highlighted beacon or edge arrow."
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    ArSheets(runtime, state, rooms, picker, unplaced, { picker = false }, { unplaced = false })
}
