package dev.co508.ticketspace.ar

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.ar.core.Anchor
import com.google.ar.core.TrackingState
import dev.co508.ticketspace.ar.math.Pose
import dev.co508.ticketspace.ar.math.roomFrame
import dev.co508.ticketspace.ar.providers.AnchorProvider
import dev.co508.ticketspace.data.RoomAnchor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant

class RoomLocator(
    private val scope: CoroutineScope,
    private val provider: (String) -> AnchorProvider,
) {
    private val jobs = mutableMapOf<String, Job>()
    private val resolved = mutableMapOf<String, Pair<RoomAnchor, Anchor>>()
    private var rows = emptyMap<String, RoomAnchor>()
    var room by mutableStateOf<String?>(null)
        private set
    var worldRoom by mutableStateOf<Pose?>(null)
        private set
    private var manual = false
    private var firstSelected = false

    fun beginSearch(hint: String?) {
        manual = false
        firstSelected = false
        room = hint
        worldRoom = null
    }

    fun pickRoom(id: String) {
        manual = true
        room = id
        worldRoom = null
    }

    fun reconcile(records: List<RoomAnchor>) {
        val next = records.associateBy { it.id }
        rows.filter { (id, row) -> next[id] != row }.keys.forEach { id ->
            jobs.remove(id)?.cancel()
            resolved.remove(id)?.let { if (it.first.provider == "cloud") it.second.detach() }
        }
        rows = next
        records.filter { it.id !in jobs }.forEach { row ->
            jobs[row.id] =
                scope.launch {
                    try {
                        val expiry =
                            row.expiresAt.takeIf { it.isNotEmpty() }?.let {
                                runCatching { Instant.parse(it.replace(' ', 'T')) }.getOrNull()
                            }
                        if (expiry != null && expiry < Instant.now()) return@launch
                        val anchor = provider(row.provider).resolve(row.anchorId) ?: return@launch
                        resolved[row.id] = row to anchor
                        if (!manual && !firstSelected) {
                            room = row.room
                            firstSelected = true
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w("TicketSpaceAR", "resolve provider=${row.provider} result=${e.javaClass.simpleName}")
                    }
                }
        }
    }

    fun update(
        camera: Pose,
        tracking: Boolean,
    ) {
        if (tracking && !manual && !firstSelected) {
            resolved.values.firstOrNull { it.second.trackingState == TrackingState.TRACKING }?.let {
                room = it.first.room
                firstSelected = true
            }
        }
        worldRoom = if (tracking) frameFor(room, camera) else null
    }

    fun frameFor(
        roomId: String?,
        camera: Pose,
    ): Pose? =
        resolved.values
            .filter { it.first.room == roomId && it.second.trackingState == TrackingState.TRACKING }
            .minByOrNull { (_, anchor) ->
                anchor.pose.translation
                    .zip(camera.t)
                    .sumOf { (a, b) -> ((a - b) * (a - b)).toDouble() }
            }?.let { (row, anchor) -> roomFrame(anchor.pose.roomPose(), Pose.fromJson(row.poseInRoom)) }

    fun adopt(
        records: List<RoomAnchor>,
        anchors: List<Anchor>,
    ) {
        records.zip(anchors).forEach { (row, anchor) ->
            jobs.remove(row.id)?.cancel()
            resolved[row.id] = row to anchor
            jobs[row.id] = Job().apply { complete() }
        }
        rows = rows + records.associateBy { it.id }
        room = records.first().room
        manual = true
    }

    fun retry() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        resolved.values.filter { it.first.provider == "cloud" }.forEach { it.second.detach() }
        resolved.clear()
        firstSelected = false
    }

    fun close() {
        jobs.values.forEach { it.cancel() }
        resolved.values.filter { it.first.provider == "cloud" }.forEach { it.second.detach() }
        jobs.clear()
        resolved.clear()
    }
}
