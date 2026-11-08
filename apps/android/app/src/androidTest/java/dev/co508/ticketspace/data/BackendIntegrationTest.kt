package dev.co508.ticketspace.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.co508.ticketspace.ar.math.Pose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Proves the P0 data layer against the live PocketBase backend, on a real
 * device, using the app's own [PocketBaseClient], [Realtime], models and
 * spatial API. It never touches the UI, so it runs on a locked phone.
 *
 * Credentials are supplied as instrumentation arguments (`tsBaseUrl`,
 * `tsEmail`, `tsPassword`) and are skipped cleanly when absent, so a plain
 * `connectedDebugAndroidTest` without them still passes. See
 * `apps/android/scripts/integration-test.sh`.
 */
@RunWith(AndroidJUnit4::class)
class BackendIntegrationTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var api: PocketBaseClient
    private var connected = false

    private val runPrefix = "it-${System.currentTimeMillis()}"
    private val createdRoomIds = mutableListOf<String>()
    private val createdTodoIds = mutableListOf<String>()
    private val createdThingIds = mutableListOf<String>()
    private val createdAnchorIds = mutableListOf<String>()

    @After
    fun cleanup() {
        if (!connected) return
        // Delete in reverse dependency order, tolerating any already-failed write,
        // so a failure mid-test does not leave `it-` records behind.
        runBlocking {
            createdTodoIds.reversed().forEach { runCatching { api.delete("todos", it) } }
            createdThingIds.reversed().forEach { runCatching { api.delete("things", it) } }
            createdAnchorIds.reversed().forEach { runCatching { api.delete("room_anchors", it) } }
            createdRoomIds.reversed().forEach { runCatching { api.delete("rooms", it) } }
        }
    }

    @Test
    fun p0DataLayerEndToEnd() =
        runBlocking {
            val baseUrl = arg("tsBaseUrl")
            val email = arg("tsEmail")
            val password = arg("tsPassword")
            assumeTrue(
                "Missing tsBaseUrl/tsEmail/tsPassword instrumentation arguments",
                baseUrl != null && email != null && password != null,
            )

            api = PocketBaseClient(baseUrl!!)
            val auth = api.authWithPassword(email!!, password!!)
            api.token = auth.token
            val userId = auth.record.id
            connected = true

            // -- sign in: the seeded Home site is listed -------------------
            val sites = api.list<Site>("sites")
            assertTrue("seeded Home site is listed", sites.any { it.name == "Home" })
            val home = sites.first { it.name == "Home" }

            // -- create a room with a small generated JPEG panorama ---------
            val panorama = jpegFile("panorama.jpg", 640, 320, Color.rgb(180, 200, 220))
            val room = api.saveRoom(null, home.id, "$runPrefix-room", panorama)
            createdRoomIds += room.id
            assertTrue("room panorama is stored", room.panorama.isNotEmpty())

            // -- todos: one with 2 photos/tags/priority 2, two more ---------
            val todoA =
                api.saveTodo(
                    null,
                    todoFields(
                        "$runPrefix-a",
                        2,
                        room.id,
                        userId,
                        listOf("$runPrefix-leak", "$runPrefix-urgent"),
                    ),
                    listOf(
                        jpegFile("a-1.jpg", 640, 480, Color.rgb(220, 80, 80)),
                        jpegFile("a-2.jpg", 480, 640, Color.rgb(80, 220, 80)),
                    ),
                )
            createdTodoIds += todoA.id
            assertEquals(2, todoA.photos.size)

            val todoB =
                api.saveTodo(
                    null,
                    todoFields("$runPrefix-b", 1, room.id, userId, listOf("$runPrefix-leak")),
                )
            createdTodoIds += todoB.id
            // Guarantee distinct `created` timestamps so the oldest-first tie-break is deterministic.
            delay(1200)

            val todoC =
                api.saveTodo(
                    null,
                    todoFields("$runPrefix-c", 1, room.id, userId, listOf("$runPrefix-paint")),
                )
            createdTodoIds += todoC.id

            val room2 = api.saveRoom(null, home.id, "$runPrefix-room2", null)
            createdRoomIds += room2.id
            val todoD =
                api.saveTodo(
                    null,
                    todoFields("$runPrefix-d", 0, room2.id, userId, listOf("$runPrefix-paint")),
                )
            createdTodoIds += todoD.id

            // -- sort: priority desc then oldest first ----------------------
            assertEquals(
                "room list sorts priority desc then oldest first",
                listOf(todoA.id, todoB.id, todoC.id),
                api.todos(TodoQuery(room = room.id)).map { it.id },
            )
            val mine = setOf(todoA.id, todoB.id, todoC.id, todoD.id)
            val expectedOrder = listOf(todoA, todoB, todoC, todoD).sortedWith(todoOrder).map { it.id }
            val actualOrder = api.todos(TodoQuery(showDone = true)).map { it.id }.filter { it in mine }
            assertEquals("global list keeps the same relative order", expectedOrder, actualOrder)

            // -- filters: room and tag return the right set -----------------
            assertEquals(
                listOf(todoD.id),
                api.todos(TodoQuery(room = room2.id)).map { it.id },
            )
            assertEquals(
                listOf(todoA.id, todoB.id),
                api.todos(TodoQuery(tag = "$runPrefix-leak")).map { it.id },
            )
            val paint = api.todos(TodoQuery(tag = "$runPrefix-paint")).map { it.id }
            assertEquals(setOf(todoC.id, todoD.id), paint.toSet())
            assertTrue("paint tag list sorts by priority desc", paint.indexOf(todoC.id) < paint.indexOf(todoD.id))

            // -- thumbnail URLs return 200 ----------------------------------
            todoA.photos.forEach { assertStatus(200, api.fileUrl("todos", todoA.id, it, "320x320")) }
            assertStatus(200, api.fileUrl("rooms", room.id, room.panorama, "480x240"))

            // -- realtime: subscribe, create, receive the create event -------
            val events = Channel<RealtimeEvent>(Channel.UNLIMITED)
            val job = launch { Realtime(api).events().collect { events.send(it) } }
            try {
                withTimeout(10_000) { awaitConnected(events) }
                val todoE =
                    api.saveTodo(
                        null,
                        todoFields("$runPrefix-e", 2, room.id, userId, listOf("$runPrefix-live")),
                    )
                createdTodoIds += todoE.id
                withTimeout(10_000) { awaitTodoCreate(events, todoE.id) }
            } finally {
                job.cancel()
            }

            // -- mark done: done_by + done_at; reopen: both cleared ----------
            val done = api.saveTodo(todoA.id, buildJsonObject { put("status", "done") })
            assertEquals(TodoStatus.DONE, done.status)
            assertEquals(userId, done.doneBy)
            assertTrue("done_at is set by the server", done.doneAt.isNotEmpty())
            val reopened = api.saveTodo(todoA.id, buildJsonObject { put("status", "open") })
            assertEquals(TodoStatus.OPEN, reopened.status)
            assertEquals("", reopened.doneBy)
            assertEquals("", reopened.doneAt)

            // -- thing: pose, then unplace (null) ----------------------------
            val pose = Pose(listOf(0.5f, 1f, 1.5f), listOf(0f, 0f, 0f, 1f))
            val thing = api.saveThing(null, room.id, "$runPrefix-table", pose)
            createdThingIds += thing.id
            assertTrue("thing is placed", thing.poseInRoom != null)
            assertEquals(pose, Pose.fromJson(thing.poseInRoom!!))
            assertEquals(null, api.saveThing(thing.id, room.id, "$runPrefix-table", null).poseInRoom)
            assertEquals(null, api.get<Thing>("things", thing.id).poseInRoom)

            // -- room anchor (provider local) with a pose --------------------
            val anchor =
                api.saveAnchor(
                    null,
                    room.id,
                    "local",
                    "$runPrefix-local-anchor",
                    pose,
                    null,
                )
            createdAnchorIds += anchor.id
            assertEquals("local", anchor.provider)
            assertEquals(pose, Pose.fromJson(anchor.poseInRoom))
            assertEquals(anchor, api.get<RoomAnchor>("room_anchors", anchor.id))
        }

    private fun arg(name: String): String? = InstrumentationRegistry.getArguments().getString(name)

    private fun todoFields(
        title: String,
        priority: Int,
        room: String,
        userId: String,
        tags: List<String>,
    ) = buildJsonObject {
        put("title", title)
        put("priority", priority)
        put("tags", JsonArray(tags.map(::JsonPrimitive)))
        put("status", "open")
        put("room", room)
        put("created_by", userId)
    }

    private fun jpegFile(
        name: String,
        width: Int,
        height: Int,
        color: Int,
    ): File {
        val dir = File(context.cacheDir, "backend-it").apply { mkdirs() }
        val file = File(dir, "$runPrefix-$name")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, file.outputStream())) {
                "could not write test JPEG"
            }
        } finally {
            bitmap.recycle()
        }
        return file
    }

    private suspend fun assertStatus(
        expected: Int,
        url: String,
    ) {
        val code =
            withContext(Dispatchers.IO) {
                api.http
                    .newCall(
                        Request
                            .Builder()
                            .url(url)
                            .header("Authorization", api.token)
                            .build(),
                    ).execute()
                    .use { it.code }
            }
        assertEquals("GET $url", expected, code)
    }

    private suspend fun awaitConnected(events: Channel<RealtimeEvent>) {
        while (true) {
            if (events.receive() == RealtimeEvent.Connected) return
        }
    }

    private suspend fun awaitTodoCreate(
        events: Channel<RealtimeEvent>,
        id: String,
    ) {
        while (true) {
            val event = events.receive()
            if (event is RealtimeEvent.Change &&
                event.collection == "todos" &&
                event.action == "create" &&
                event.record["id"]?.jsonPrimitive?.content == id
            ) {
                return
            }
        }
    }
}
