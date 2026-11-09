package dev.co508.ticketspace.ar

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.ar.core.Anchor
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import dev.co508.ticketspace.AppContainer
import dev.co508.ticketspace.ar.providers.AnchorOperations
import dev.co508.ticketspace.ar.providers.CLOUD_TTL_DAYS
import dev.co508.ticketspace.ar.providers.CloudAnchorProvider
import dev.co508.ticketspace.ar.providers.LocalAnchorProvider
import dev.co508.ticketspace.ar.providers.selectProvider
import dev.co508.ticketspace.data.Room
import dev.co508.ticketspace.data.Thing
import dev.co508.ticketspace.data.Todo
import dev.co508.ticketspace.data.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.UUID

data class RegistrationPlan(
    val existing: Room?,
    val site: String,
    val name: String,
    val provider: String,
)

data class ArTodoDraft(
    val room: String,
    val thing: String,
    val photo: File?,
    val key: String = UUID.randomUUID().toString(),
)

class ArRuntime(
    private val app: AppContainer,
    private val scope: CoroutineScope,
    initialPlan: RegistrationPlan?,
) {
    var session by mutableStateOf<Session?>(null)
        private set
    var frame: Frame? = null
        private set
    private val local = LocalAnchorProvider()
    private var cloud: CloudAnchorProvider? = null
    val locator = RoomLocator(scope) { selectProvider(it, local, checkNotNull(cloud)) }
    var plan by mutableStateOf(initialPlan)
    val hosted = mutableStateListOf<RegistrationAnchor>()
    var candidate by mutableStateOf<Anchor?>(null)
        private set
    var quality by mutableStateOf(Session.FeatureMapQuality.INSUFFICIENT)
        private set
    var tracking by mutableStateOf(TrackingState.PAUSED)
        private set
    var tick by mutableStateOf(0L)
        private set
    var error by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
        private set
    var placing by mutableStateOf(false)
    var moving by mutableStateOf<Thing?>(null)
    var selected by mutableStateOf<String?>(null)
    var showThings by mutableStateOf(false)
    var draft by mutableStateOf<ArTodoDraft?>(null)
    var reticle by mutableStateOf<HitResult?>(null)
        private set
    var width = 1f
    var height = 1f
    private var lastQuality = 0L
    val view = FloatArray(16)
    val projection = FloatArray(16)

    fun attach(value: Session) {
        session = value
        cloud = CloudAnchorProvider(value, AnchorOperations())
    }

    fun update(value: Frame) {
        frame = value
        val camera = value.camera
        if (tracking != camera.trackingState) {
            tracking = camera.trackingState
            Log.i("TicketSpaceAR", "tracking=$tracking reason=${camera.trackingFailureReason}")
        }
        locator.update(camera.pose.roomPose(), tracking == TrackingState.TRACKING)
        camera.getViewMatrix(view, 0)
        camera.getProjectionMatrix(projection, 0, 0.05f, 100f)
        reticle = if (placing) hit(width / 2f, height / 2f) else null
        val now = android.os.SystemClock.elapsedRealtime()
        if (candidate != null &&
            plan?.provider == "cloud" &&
            tracking == TrackingState.TRACKING &&
            now - lastQuality > 500
        ) {
            lastQuality = now
            // ARCore estimates hosting quality from the CAMERA pose (features seen in
            // the last few seconds from this viewpoint), not from the anchor pose.
            quality =
                runCatching { checkNotNull(session).estimateFeatureMapQualityForHosting(camera.pose) }
                    .getOrDefault(Session.FeatureMapQuality.INSUFFICIENT)
            Log.i("TicketSpaceAR", "quality=$quality provider=cloud tracking=$tracking")
        }
        tick = value.timestamp
    }

    fun hit(
        x: Float,
        y: Float,
    ): HitResult? {
        if (tracking != TrackingState.TRACKING) return null
        val hits =
            frame?.hitTest(x, y).orEmpty().filter { hit ->
                when (val trackable = hit.trackable) {
                    is DepthPoint -> trackable.trackingState == TrackingState.TRACKING
                    is Plane ->
                        trackable.trackingState == TrackingState.TRACKING &&
                            trackable.isPoseInPolygon(hit.hitPose)
                    else -> false
                }
            }
        return hits.firstOrNull { it.trackable is DepthPoint } ?: hits.firstOrNull()
    }

    fun tapSurface(
        x: Float,
        y: Float,
    ) {
        if (plan == null || busy || hosted.size == 3) return
        val hit =
            hit(x, y) ?: run {
                error = "No surface yet. Move slowly and point at textured walls or furniture."
                return
            }
        if (hosted.any { distanceSquared(it.anchor.pose.translation, hit.hitPose.translation) < 0.75f * 0.75f }) {
            error = "Spread anchors around the room (at least 75 cm apart)."
            return
        }
        candidate?.detach()
        candidate = hit.createAnchor()
        quality = Session.FeatureMapQuality.INSUFFICIENT
        error = null
    }

    fun host() =
        action {
            val anchor = checkNotNull(candidate)
            check(anchor.trackingState == TrackingState.TRACKING) { "Keep the spot in view until tracking recovers." }
            val provider = selectProvider(checkNotNull(plan).provider, local, checkNotNull(cloud))
            val result = provider.host(anchor, CLOUD_TTL_DAYS)
            hosted += RegistrationAnchor(anchor, result)
            candidate = null
        }

    fun finishRegistration(context: Context) =
        action {
            val registration = checkNotNull(plan)
            check(
                tracking == TrackingState.TRACKING &&
                    hosted.all {
                        it.anchor.trackingState == TrackingState.TRACKING
                    },
            ) { "Wait for tracking to recover before saving." }
            val oldFrame = locator.frameFor(registration.existing?.id, checkNotNull(frame).camera.pose.roomPose())
            val photo = captureArPhoto(context, checkNotNull(session), checkNotNull(frame))
            try {
                val (room, records) =
                    saveRegistration(
                        app.api,
                        registration.existing,
                        registration.site,
                        registration.name,
                        registration.provider,
                        hosted.toList(),
                        oldFrame,
                        photo,
                    )
                locator.adopt(records, hosted.map { it.anchor })
                hosted.clear()
                plan = null
                app.spaces.refresh()
                locator.pickRoom(room.id)
            } finally {
                photo.delete()
            }
        }

    fun place(context: Context) =
        action {
            val transform = checkNotNull(locator.worldRoom) { "Locate the room before placing a Thing." }
            val room = checkNotNull(locator.room)
            val target = checkNotNull(reticle) { "Point the reticle at a tracked surface." }
            val pose = transform.inverse() * target.hitPose.roomPose()
            val move = moving
            if (move != null) {
                check(move.room == room)
                app.api.saveThing(move.id, room, move.label, pose)
            } else {
                val photo = captureArPhoto(context, checkNotNull(session), checkNotNull(frame))
                try {
                    val thing = app.api.saveThing(null, room, "", pose)
                    draft = ArTodoDraft(room, thing.id, photo)
                } catch (e: Exception) {
                    photo.delete()
                    throw e
                }
            }
            placing = false
            moving = null
            app.spaces.refresh()
        }

    fun addTodo(
        context: Context,
        thing: Thing,
    ) = action {
        val photo = captureArPhoto(context, checkNotNull(session), checkNotNull(frame))
        selected = null
        draft = ArTodoDraft(thing.room, thing.id, photo)
    }

    fun markDone(todo: Todo) =
        action {
            app.api.saveTodo(
                todo.id,
                buildJsonObject {
                    put("status", "done")
                    put("priority", todo.priority)
                },
            )
            app.spaces.refresh()
        }

    private fun action(block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            error = null
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                busy = false
            }
        }
    }

    fun close() {
        locator.close()
        candidate?.detach()
        hosted.filter { plan?.provider == "cloud" }.forEach { it.anchor.detach() }
        local.clear()
        draft?.photo?.delete()
        frame = null
        session = null
    }

    fun cancelRegistration() {
        candidate?.detach()
        candidate = null
        hosted.forEach {
            local.forget(it.anchor)
            it.anchor.detach()
        }
        hosted.clear()
        plan = null
        placing = false
        moving = null
        selected = null
    }
}

private fun distanceSquared(
    a: FloatArray,
    b: FloatArray,
) = a.indices
    .sumOf {
        ((a[it] - b[it]) * (a[it] - b[it])).toDouble()
    }.toFloat()
