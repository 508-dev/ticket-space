package dev.co508.ticketspace.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DeleteRoomConfirmationTest {
    @Test
    fun `names the room and counts its todos and things`() {
        assertEquals(
            "Delete Kitchen? This permanently deletes its 3 todos and 2 things. " +
                "Anchors hosted on Google expire on their own.",
            deleteRoomConfirmation("Kitchen", 3, 2),
        )
    }

    @Test
    fun `handles zero counts`() {
        assertEquals(
            "Delete Garage? This permanently deletes its 0 todos and 0 things. " +
                "Anchors hosted on Google expire on their own.",
            deleteRoomConfirmation("Garage", 0, 0),
        )
    }
}
