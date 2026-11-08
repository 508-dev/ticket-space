package dev.co508.ticketspace.ar.math

import dev.co508.ticketspace.data.wireJson
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class PoseTest {
    @Test
    fun `JSON uses contract shape and preserves rotation`() {
        val pose = pose(1f, 2f, 3f, 0.7f)
        val json = pose.toJson()
        assertEquals(setOf("t", "q"), json.keys)
        assertEquals(pose, Pose.fromJson(json))
        assertEquals(Pose.IDENTITY, wireJson.decodeFromString<Pose>("""{"t":[0,0,0],"q":[0,0,0,1]}"""))
    }

    @Test
    fun `compose inverse and point rotation obey rigid transforms`() {
        val a = pose(3f, -4f, 8f, 1.2f)
        val b = Pose(listOf(-3f, 0.3f, 2f), listOf(sin(0.3f), 0f, 0f, cos(0.3f)))
        assertPose(Pose.IDENTITY, a.inverse() * a)
        assertPose(Pose.IDENTITY, a * a.inverse())
        val point = listOf(2f, 4f, -5f)
        assertVector((a * b).transformPoint(point), a.transformPoint(b.transformPoint(point)))
        assertVector(point, a.inverse().transformPoint(a.transformPoint(point)))
    }

    @Test
    fun `registration round trip resolves a non-origin anchor in a different world`() {
        val a0Session1 = pose(1.3f, -0.4f, 2.5f, 0.8f)
        val a1Session1 = pose(-2.1f, 0.7f, -3.8f, -0.4f)
        val hitSession1 = pose(4.1f, 1.2f, -2.3f, 1.7f)
        val anchorInRoom = a0Session1.inverse() * a1Session1
        val thingInRoom = a0Session1.inverse() * hitSession1
        assertPose(Pose.IDENTITY, a0Session1.inverse() * a0Session1)
        val session2FromSession1 = Pose(listOf(-7f, 2f, 5f), listOf(0f, 0f, sin(0.45f), cos(0.45f)))
        val resolvedA1 = session2FromSession1 * a1Session1
        val worldRoomSession2 = roomFrame(resolvedA1, anchorInRoom)
        assertPose(session2FromSession1 * hitSession1, worldRoomSession2 * thingInRoom)
        assertPose(thingInRoom, worldRoomSession2.inverse() * (session2FromSession1 * hitSession1))
    }

    @Test
    fun `re-register carries old frame into new frame without moving the physical Thing`() {
        val oldRoom = pose(1f, 2f, -3f, 0.6f)
        val newRoom = pose(-4f, 0.1f, 2f, -1.1f)
        val oldThing = pose(2f, 1f, 4f, 0.2f)
        val newThing = carryOver(oldThing, oldRoom, newRoom)
        assertPose(oldRoom * oldThing, newRoom * newThing)
        assertPose(oldThing, carryOver(newThing, newRoom, oldRoom))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects invalid quaternion instead of corrupting all room poses`() {
        Pose(q = listOf(0f, 0f, 0f, 0f))
    }

    private fun pose(
        x: Float,
        y: Float,
        z: Float,
        yaw: Float,
    ) = Pose(
        listOf(x, y, z),
        listOf(
            0f,
            sin(yaw / 2),
            0f,
            cos(
                yaw / 2,
            ),
        ),
    )

    private fun assertPose(
        expected: Pose,
        actual: Pose,
    ) {
        assertVector(expected.t, actual.t)
        // Compare transformed basis points to account for q and -q describing the same rotation.
        listOf(listOf(1f, 0f, 0f), listOf(0f, 1f, 0f), listOf(0f, 0f, 1f)).forEach {
            assertVector(expected.transformPoint(it), actual.transformPoint(it))
        }
    }

    private fun assertVector(
        expected: List<Float>,
        actual: List<Float>,
    ) {
        expected.zip(actual).forEach { (a, b) -> assertEquals(a, b, 0.0001f) }
    }
}
