package dev.co508.ticketspace.ar.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectionTest {
    private val view = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
    private val projection = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, -1.02f, -1f, 0f, 0f, -0.202f, 0f)

    @Test
    fun `front center projects to screen center`() {
        val p = project(0f, 0f, -2f)
        assertEquals(500f, p.x, 0.001f)
        assertEquals(300f, p.y, 0.001f)
        assertFalse(p.offscreen)
    }

    @Test
    fun `outside view clamps along bearing to inset rectangle`() {
        val p = project(4f, 1f, -1f)
        assertTrue(p.offscreen)
        assertEquals(968f, p.x, 0.001f)
        assertEquals(229.8f, p.y, 0.001f)
        assertTrue(p.angle < 0)
    }

    @Test
    fun `behind camera stays offscreen and rightward rather than mirrored`() {
        val p = project(1f, 0f, 2f)
        assertTrue(p.offscreen)
        assertEquals(968f, p.x, 0.001f)
        assertEquals(300f, p.y, 0.001f)
    }

    @Test
    fun `directly behind and at eye plane remain finite`() {
        listOf(project(0f, 0f, 2f), project(0f, 0f, 0f), project(1f, 1f, 0f)).forEach {
            assertTrue(it.offscreen)
            assertTrue(it.x.isFinite() && it.y.isFinite() && it.angle.isFinite())
            assertTrue(it.x in 32f..968f && it.y in 32f..568f)
        }
    }

    @Test
    fun `view translation and portrait viewport are applied`() {
        val translated = view.copyOf().apply { this[12] = -2f }
        val p = projectBeacon(listOf(2f, 0f, -3f), translated, projection, 600f, 1000f)
        assertEquals(300f, p.x, 0.001f)
        assertEquals(500f, p.y, 0.001f)
        assertFalse(p.offscreen)
    }

    private fun project(
        x: Float,
        y: Float,
        z: Float,
    ) = projectBeacon(listOf(x, y, z), view, projection, 1000f, 600f)
}
