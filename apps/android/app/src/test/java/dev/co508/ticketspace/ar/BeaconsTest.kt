package dev.co508.ticketspace.ar

import dev.co508.ticketspace.ar.math.Pose
import dev.co508.ticketspace.data.SpaceState
import dev.co508.ticketspace.data.Thing
import dev.co508.ticketspace.data.Todo
import dev.co508.ticketspace.data.TodoStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BeaconsTest {
    @Test
    fun `one beacon per placed Thing takes highest open priority and updates after completion`() {
        val thing = Thing("thing", "room", poseInRoom = Pose.IDENTITY.toJson())
        val normal =
            Todo(
                "normal",
                "Fix",
                priority = 1,
                status = TodoStatus.OPEN,
                room = "room",
                thing = "thing",
                createdBy = "u",
            )
        val urgent = normal.copy(id = "urgent", priority = 3)
        val state =
            SpaceState(
                things = listOf(thing, thing.copy(id = "unplaced", poseInRoom = null)),
                todos = listOf(normal, urgent),
            )
        assertEquals(3, beacons(state, "room", Pose.IDENTITY, false, null).single().priority)
        assertEquals(
            1,
            beacons(
                state.copy(todos = listOf(normal, urgent.copy(status = TodoStatus.DONE))),
                "room",
                Pose.IDENTITY,
                false,
                null,
            ).single().priority,
        )
        assertTrue(beacons(state.copy(todos = emptyList()), "room", Pose.IDENTITY, false, null).isEmpty())
        assertNull(beacons(state.copy(todos = emptyList()), "room", Pose.IDENTITY, true, null).single().priority)
        assertTrue(beacons(state, "room", null, true, null).isEmpty())
        assertTrue(beacons(state.copy(todos = emptyList()), "room", Pose.IDENTITY, false, "thing").single().highlighted)
    }
}
