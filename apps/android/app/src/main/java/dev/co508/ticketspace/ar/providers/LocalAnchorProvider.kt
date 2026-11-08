package dev.co508.ticketspace.ar.providers

import android.util.Log
import com.google.ar.core.Anchor
import com.google.ar.core.TrackingState
import java.util.UUID

/** Owned by one live ARCore session. Never resolves a pose from another session's origin. */
class LocalAnchorProvider : AnchorProvider {
    override val id = "local"
    private val anchors = mutableMapOf<String, Anchor>()

    override suspend fun host(
        anchor: Anchor,
        ttlDays: Int,
    ): HostedAnchor {
        val key = UUID.randomUUID().toString()
        anchors[key] = anchor
        Log.i("TicketSpaceAR", "host provider=local result=SUCCESS durationMs=0")
        return HostedAnchor(key)
    }

    override suspend fun resolve(anchorId: String): Anchor? =
        anchors[anchorId]
            ?.takeIf { it.trackingState != TrackingState.STOPPED }
            .also { Log.i("TicketSpaceAR", "resolve provider=local result=${it != null} durationMs=0") }

    fun clear() {
        anchors.values.forEach { it.detach() }
        anchors.clear()
    }

    fun forget(anchor: Anchor) {
        anchors.entries.removeAll { it.value === anchor }
    }
}
