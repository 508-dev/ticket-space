package dev.co508.ticketspace.ar

import dev.co508.ticketspace.ar.math.Pose
import dev.co508.ticketspace.ar.math.ScreenPoint
import dev.co508.ticketspace.ar.math.projectBeacon
import dev.co508.ticketspace.data.Thing
import dev.co508.ticketspace.data.Todo
import dev.co508.ticketspace.data.TodoStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BeaconSelectionTest {
    private fun projected(
        id: String,
        x: Float = 500f,
        y: Float = 1000f,
        distance: Float = 2f,
        behind: Boolean = false,
        offscreen: Boolean = false,
    ) = ProjectedBeacon(
        Beacon(Thing(id, "room"), Pose.IDENTITY, 1, false),
        ScreenPoint(x, y, offscreen, 0f, behind, distance),
    )

    @Test
    fun `statusboard selects closest screen point with stable id tie breaking`() {
        assertEquals("near", selectStatusboard(listOf(projected("far", 550f), projected("near", 510f)), 1000f, 2000f))
        assertEquals("a", selectStatusboard(listOf(projected("b"), projected("a")), 1000f, 2000f))
        assertNull(selectStatusboard(emptyList(), 1000f, 2000f))
    }

    @Test
    fun `statusboard enforces twelve percent radius front camera and eight metres`() {
        assertEquals("edge", selectStatusboard(listOf(projected("edge", 620f, distance = 8f)), 1000f, 2000f))
        assertNull(selectStatusboard(listOf(projected("outside", 621f)), 1000f, 2000f))
        assertNull(selectStatusboard(listOf(projected("far", distance = 8.01f)), 1000f, 2000f))
        assertNull(selectStatusboard(listOf(projected("behind", behind = true)), 1000f, 2000f))
        assertNull(selectStatusboard(listOf(projected("offscreen", offscreen = true)), 1000f, 2000f))
        assertNull(selectStatusboard(listOf(projected("invalid", distance = Float.NaN)), 1000f, 2000f))
        assertNull(selectStatusboard(listOf(projected("invalid")), 0f, 2000f))
        // Landscape uses the shorter side as well.
        assertEquals("landscape", selectStatusboard(listOf(projected("landscape", 1120f, 500f)), 2000f, 1000f))
    }

    @Test
    fun `focus dwells on initial target and neighbouring changes for 150 ms`() {
        var focus = advanceStatusboard(StatusboardFocus(), "a", 1000)
        assertNull(focus.selected)
        focus = advanceStatusboard(focus, "a", 1149)
        assertNull(focus.selected)
        focus = advanceStatusboard(focus, "a", 1150)
        assertEquals("a", focus.selected)
        focus = advanceStatusboard(focus, "b", 1200)
        assertEquals("a", focus.selected)
        focus = advanceStatusboard(focus, "a", 1250)
        assertEquals("a", focus.selected)
        focus = advanceStatusboard(focus, "b", 1300)
        focus = advanceStatusboard(focus, "b", 1450)
        assertEquals("b", focus.selected)
        assertEquals(StatusboardFocus(), advanceStatusboard(focus, null, 1451))
    }

    @Test
    fun `pending dwell resets after a new camera session timestamp`() {
        val pending = advanceStatusboard(StatusboardFocus(), "a", 1000)
        assertEquals(5L, advanceStatusboard(pending, "a", 5).sinceMs)
    }

    @Test
    fun `count badges require multiple open todos and visible front projection`() {
        val point = projected("a").point
        assertNull(beaconCountBadge(point, 0))
        assertNull(beaconCountBadge(point, 1))
        assertEquals(2, beaconCountBadge(point, 2))
        assertEquals(123, beaconCountBadge(point, 123))
        assertNull(beaconCountBadge(point.copy(offscreen = true), 3))
        assertNull(beaconCountBadge(point.copy(behindCamera = true), 3))
    }

    @Test
    fun `projection exposes actual camera distance and behind flag`() {
        val identity = FloatArray(16) { if (it % 5 == 0) 1f else 0f }
        val point = projectBeacon(listOf(3f, 0f, -4f), identity, identity, 1000f, 2000f)
        assertEquals(5f, point.distance, 0.001f)
        assertTrue(projectBeacon(listOf(0f, 0f, 1f), identity, identity, 1000f, 2000f).behindCamera)
    }

    @Test
    fun `editor open todos and top title sort by priority then oldest and exclude done or other spots`() {
        val normal =
            Todo(
                "normal",
                "Normal",
                priority = 1,
                status = TodoStatus.OPEN,
                room = "room",
                thing = "a",
                createdBy = "u",
            )
        val urgent = normal.copy(id = "urgent", title = "Urgent", priority = 3, created = "2026-11-17")
        val oldest = urgent.copy(id = "oldest", created = "2026-11-16")
        val todos =
            listOf(normal, urgent, oldest, urgent.copy(id = "done", status = TodoStatus.DONE), urgent.copy(thing = "b"))
        assertEquals(listOf(oldest, urgent, normal), openThingTodos(todos, "a"))
        assertEquals("Spot", Thing("a", "room").displayLabel)
        assertEquals("Table", Thing("a", "room", "Table").displayLabel)
        assertEquals("Its 5 todos stay in the room as room-level todos.", deleteThingConfirmation(5))
    }
}
