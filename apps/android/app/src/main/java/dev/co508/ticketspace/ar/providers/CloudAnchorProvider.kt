package dev.co508.ticketspace.ar.providers

import android.os.SystemClock
import android.util.Log
import com.google.ar.core.Anchor
import com.google.ar.core.Session
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CloudAnchorProvider(
    private val session: Session,
    private val operations: AnchorOperations,
) : AnchorProvider {
    override val id = "cloud"

    override suspend fun host(
        anchor: Anchor,
        ttlDays: Int,
    ): HostedAnchor {
        require(ttlDays == CLOUD_TTL_DAYS)
        // Feature-map quality is estimated from the camera pose and gated by the caller
        // (ArRuntime/ArControls): SUFFICIENT or GOOD may host.
        Log.i("TicketSpaceAR", "host start provider=cloud ttlDays=$ttlDays")
        val started = SystemClock.elapsedRealtime()
        try {
            return operations.run {
                suspendCancellableCoroutine { continuation ->
                    val future =
                        session.hostCloudAnchorAsync(anchor, ttlDays) { id, state ->
                            Log.i(
                                "TicketSpaceAR",
                                "host result=$state provider=cloud durationMs=${SystemClock.elapsedRealtime() - started}",
                            )
                            if (continuation.isActive) {
                                if (state == Anchor.CloudAnchorState.SUCCESS && id != null) {
                                    continuation.resume(HostedAnchor(id, anchorExpiry(Instant.now(), ttlDays)))
                                } else {
                                    continuation.resumeWithException(
                                        IllegalStateException("Cloud hosting failed: $state"),
                                    )
                                }
                            }
                        }
                    continuation.invokeOnCancellation { future.cancel() }
                }
            } ?: run {
                Log.w("TicketSpaceAR", "host result=TIMEOUT provider=cloud")
                error("Cloud hosting timed out. Keep the spot in view and retry.")
            }
        } finally {
            Log.i("TicketSpaceAR", "host finished provider=cloud durationMs=${SystemClock.elapsedRealtime() - started}")
        }
    }

    override suspend fun resolve(anchorId: String): Anchor? {
        val started = SystemClock.elapsedRealtime()
        Log.i("TicketSpaceAR", "resolve start provider=cloud")
        try {
            return operations
                .run {
                    suspendCancellableCoroutine { continuation ->
                        val future =
                            session.resolveCloudAnchorAsync(anchorId) { anchor, state ->
                                Log.i(
                                    "TicketSpaceAR",
                                    "resolve result=$state provider=cloud durationMs=${SystemClock.elapsedRealtime() - started}",
                                )
                                if (continuation.isActive) {
                                    val result = anchor.takeIf { state == Anchor.CloudAnchorState.SUCCESS }
                                    if (result == null) anchor?.detach()
                                    continuation.resume(result) { _, value, _ -> value?.detach() }
                                } else {
                                    anchor?.detach()
                                }
                            }
                        continuation.invokeOnCancellation { future.cancel() }
                    }
                }.also { if (it == null) Log.i("TicketSpaceAR", "resolve result=FAILED_OR_TIMEOUT provider=cloud") }
        } finally {
            Log.i(
                "TicketSpaceAR",
                "resolve finished provider=cloud durationMs=${SystemClock.elapsedRealtime() - started}",
            )
        }
    }
}
