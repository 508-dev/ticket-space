package dev.co508.ticketspace.ar

import com.google.ar.core.Anchor
import dev.co508.ticketspace.ar.math.Pose
import dev.co508.ticketspace.ar.math.carryOver
import dev.co508.ticketspace.ar.providers.HostedAnchor
import dev.co508.ticketspace.data.PocketBaseClient
import dev.co508.ticketspace.data.Room
import dev.co508.ticketspace.data.RoomAnchor
import dev.co508.ticketspace.data.Thing
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File

data class RegistrationAnchor(
    val anchor: Anchor,
    val hosted: HostedAnchor,
)

/** Registration uses final live poses, rather than poses snapshotted before ARCore refined tracking. */
suspend fun saveRegistration(
    api: PocketBaseClient,
    existing: Room?,
    site: String,
    name: String,
    provider: String,
    anchors: List<RegistrationAnchor>,
    oldFrame: Pose?,
    panorama: File,
): Pair<Room, List<RoomAnchor>> {
    require(anchors.size == 3)
    val newFrame =
        anchors
            .first()
            .anchor.pose
            .roomPose()
    val oldAnchors = existing?.let { api.list<RoomAnchor>("room_anchors", "room = '${it.id}'") }.orEmpty()
    val things = existing?.let { api.list<Thing>("things", "room = '${it.id}'") }.orEmpty()
    val saved = mutableListOf<RoomAnchor>()
    val moved = mutableListOf<Thing>()
    val deleted = mutableListOf<RoomAnchor>()
    var room: Room? = existing
    try {
        if (room == null) room = api.saveRoom(null, site, name, panorama)
        val target = checkNotNull(room)
        anchors.forEach {
            saved +=
                api.saveAnchor(
                    null,
                    target.id,
                    provider,
                    it.hosted.anchorId,
                    newFrame.inverse() * it.anchor.pose.roomPose(),
                    it.hosted.expiresAt,
                )
        }
        things.forEach { thing ->
            val pose = thing.poseInRoom?.let(Pose::fromJson)
            val carried = if (oldFrame != null && pose != null) carryOver(pose, oldFrame, newFrame) else null
            api.saveThing(thing.id, target.id, thing.label, carried)
            moved += thing
        }
        oldAnchors.forEach {
            api.delete("room_anchors", it.id)
            deleted += it
        }
        if (existing != null) room = api.saveRoom(existing.id, existing.site, name, panorama)
        return checkNotNull(room) to saved
    } catch (failure: Exception) {
        // PocketBase's ordinary record API is not transactional. Compensate confirmed writes;
        // an ambiguous network failure still needs owner inspection (documented in device tests).
        withContext(NonCancellable) {
            val rollback = mutableListOf<Exception>()

            suspend fun undo(action: suspend () -> Unit) {
                try {
                    action()
                } catch (e: Exception) {
                    rollback += e
                }
            }
            moved.forEach { undo { api.saveThing(it.id, it.room, it.label, it.poseInRoom?.let(Pose::fromJson)) } }
            deleted.forEach {
                undo {
                    api.saveAnchor(
                        null,
                        it.room,
                        it.provider,
                        it.anchorId,
                        Pose.fromJson(it.poseInRoom),
                        it.expiresAt,
                    )
                }
            }
            saved.forEach { undo { api.delete("room_anchors", it.id) } }
            if (existing == null) room?.let { undo { api.delete("rooms", it.id) } }
            if (rollback.isNotEmpty()) {
                throw IllegalStateException(
                    "Registration failed and rollback was incomplete. Reload and re-register this room before placing Things.",
                    failure,
                )
            }
        }
        throw failure
    }
}
