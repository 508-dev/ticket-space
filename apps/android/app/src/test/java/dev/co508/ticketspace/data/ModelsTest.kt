package dev.co508.ticketspace.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    private val todoJson =
        """
        {"id":"todo1","collectionName":"todos","title":"Fix door","description":"Loose hinge",
        "priority":3,"tags":["repair","wood"],"status":"open","photos":["door.jpg"],"room":"room1",
        "thing":"","created_by":"user1","done_by":"","done_at":"",
        "created":"2026-11-01 00:00:00.000Z","updated":"2026-11-02 00:00:00.000Z"}
        """.trimIndent()

    @Test
    fun `maps PocketBase fields and enum wire names`() {
        val todo = wireJson.decodeFromString<Todo>(todoJson)
        assertEquals(Priority.URGENT, todo.priorityLevel)
        assertEquals(TodoStatus.OPEN, todo.status)
        assertEquals("user1", todo.createdBy)
        assertEquals(listOf("repair", "wood"), todo.tags)
        assertEquals(listOf("door.jpg"), todo.photos)
        assertEquals(todo, wireJson.decodeFromString<Todo>(wireJson.encodeToString(todo)))
        val done = wireJson.decodeFromString<Todo>(todoJson.replace("\"open\"", "\"done\""))
        assertEquals(TodoStatus.DONE, done.status)
    }

    @Test
    fun `JSON tags null defaults to empty and unplaced thing pose is nullable`() {
        val todo = wireJson.decodeFromString<Todo>(todoJson.replace("[\"repair\",\"wood\"]", "null"))
        assertTrue(todo.tags.isEmpty())
        val thing = wireJson.decodeFromString<Thing>("""{"id":"x","room":"r","pose_in_room":null}""")
        assertNull(thing.poseInRoom)
        val placed =
            wireJson.decodeFromString<Thing>(
                """{"id":"x","room":"r","pose_in_room":{"t":[0,1,2],"q":[0,0,0,1]}}""",
            )
        assertTrue(placed.poseInRoom is JsonObject)
        val room = wireJson.decodeFromString<Room>("""{"id":"r","site":"s","name":"Kitchen","panorama":""}""")
        assertEquals("", room.panorama)
        val site = wireJson.decodeFromString<Site>("""{"id":"s","name":"Home"}""")
        assertEquals("Home", site.name)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects invalid priority`() {
        wireJson.decodeFromString<Todo>(todoJson.replace("\"priority\":3", "\"priority\":4"))
    }

    @Test
    fun `query quotes JSON array member and escapes filter literals`() {
        assertEquals("status = 'open'", TodoQuery().filter)
        assertEquals("", TodoQuery(showDone = true).filter)
        assertEquals("room = 'r' && tags ~ '\"repair\"' && status = 'open'", TodoQuery("r", "repair").filter)
        assertEquals("'can\\'t\\\\x'", filterLiteral("can't\\x"))
        val query = TodoQuery(tag = "x' || status = 'done")
        assertTrue(query.filter.contains("x\\' || status = \\'done"))
        assertEquals("-priority,created", TodoQuery.SORT)
        assertEquals(
            "tags ~ ${filterLiteral(JsonPrimitive("quote\"tag").toString())}",
            TodoQuery(tag = "quote\"tag", showDone = true).filter,
        )
    }

    @Test
    fun `sorting is priority descending then oldest first and exact filters are applied`() {
        val first = wireJson.decodeFromString<Todo>(todoJson)
        val second = first.copy(id = "b", priority = 1)
        val older = first.copy(id = "c", created = "2026-10-01 00:00:00.000Z")
        val done = first.copy(id = "d", status = TodoStatus.DONE)
        assertEquals(listOf("c", "todo1", "b"), listOf(second, first, done, older).matching(TodoQuery()).map { it.id })
        assertEquals(4, listOf(second, first, done, older).matching(TodoQuery(showDone = true)).size)
        assertTrue(listOf(first).matching(TodoQuery(room = "elsewhere")).isEmpty())
        assertFalse(listOf(first).matching(TodoQuery(tag = "repair")).isEmpty())
        assertTrue(listOf(first).matching(TodoQuery(tag = "rep")).isEmpty())
        assertTrue(listOf(first).matching(TodoQuery(tag = "%")).isEmpty())
    }

    @Test
    fun `realtime merge is idempotent and handles update delete unknown action`() {
        val todo = wireJson.decodeFromString<Todo>(todoJson)
        val created = mergeEvent(emptyList(), "create", todo)
        assertEquals(created, mergeEvent(created, "create", todo))
        val update = todo.copy(title = "Fixed")
        assertEquals(listOf(update), mergeEvent(created, "update", update))
        assertEquals(listOf(update), mergeEvent(emptyList(), "update", update))
        assertEquals(created, mergeEvent(created, "unknown", update))
        assertTrue(mergeEvent(created, "delete", todo).isEmpty())
        assertTrue(mergeEvent(emptyList(), "delete", todo).isEmpty())
        val newer = todo.copy(title = "Newest", updated = "2026-11-03 00:00:00.000Z")
        assertEquals(listOf(newer), mergeEvent(listOf(newer), "update", todo))
    }

    @Test
    fun `repository merges collection events and re-sorts todos`() {
        val todo = wireJson.decodeFromString<Todo>(todoJson)
        val initial = SpaceState(todos = listOf(todo, todo.copy(id = "low", priority = 0)))

        fun event(
            action: String,
            t: Todo,
        ) = RealtimeEvent.Change("todos", action, wireJson.parseToJsonElement(wireJson.encodeToString(t)) as JsonObject)
        val changed = initial.merge(event("update", todo.copy(priority = 0, created = "2027")))
        assertEquals(listOf("low", "todo1"), changed.todos.map { it.id })
        assertEquals(listOf("low"), changed.merge(event("delete", todo)).todos.map { it.id })
        val roomEvent =
            RealtimeEvent.Change(
                "rooms",
                "create",
                wireJson.parseToJsonElement("""{"id":"r","site":"s","name":"Room"}""") as JsonObject,
            )
        assertEquals(
            "Room",
            initial
                .merge(roomEvent)
                .rooms
                .single()
                .name,
        )
    }
}
