package dev.co508.ticketspace.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotosTest {
    @Test
    fun `landscape portrait and square retain aspect ratio`() {
        assertEquals(ImageSize(1600, 1200), downscaledSize(4000, 3000))
        assertEquals(ImageSize(1200, 1600), downscaledSize(3000, 4000))
        assertEquals(ImageSize(1600, 1600), downscaledSize(2000, 2000))
    }

    @Test
    fun `never upscales and never rounds a dimension to zero`() {
        assertEquals(ImageSize(640, 480), downscaledSize(640, 480))
        assertEquals(ImageSize(1600, 1), downscaledSize(10000, 1))
        assertEquals(ImageSize(1, 1), downscaledSize(1, 1))
        assertEquals(ImageSize(1600, 1067), downscaledSize(6000, 4000))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects empty image`() {
        downscaledSize(0, 4000)
    }
}
