package dev.co508.ticketspace.data

import dev.co508.ticketspace.ar.math.Pose
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SpatialApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: PocketBaseClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = PocketBaseClient(server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `Thing create and unplace send JSON objects and explicit null`() =
        runBlocking {
            val thing = Thing("thing", "room", poseInRoom = Pose.IDENTITY.toJson())
            server.enqueue(MockResponse().setBody(wireJson.encodeToString(thing)))
            assertEquals(thing, api.saveThing(null, "room", "", Pose.IDENTITY))
            val create = server.takeRequest()
            assertEquals("POST", create.method)
            assertEquals("/api/collections/things/records", create.path)
            assertEquals(
                Pose.IDENTITY.toJson(),
                wireJson.parseToJsonElement(create.body.readUtf8()).jsonObject["pose_in_room"],
            )
            server.enqueue(MockResponse().setBody(wireJson.encodeToString(thing.copy(poseInRoom = null))))
            api.saveThing("thing", "room", "", null)
            val patch = server.takeRequest()
            assertEquals("PATCH", patch.method)
            assertEquals(JsonNull, wireJson.parseToJsonElement(patch.body.readUtf8()).jsonObject["pose_in_room"])
        }

    @Test
    fun `anchor CRUD preserves provider room transform and expiry`() =
        runBlocking {
            val anchor =
                RoomAnchor("row", "room", "cloud", "provider-id", Pose.IDENTITY.toJson(), "2026-11-09T03:00:00Z")
            server.enqueue(MockResponse().setBody(wireJson.encodeToString(anchor)))
            assertEquals(anchor, api.saveAnchor(null, "room", "cloud", "provider-id", Pose.IDENTITY, anchor.expiresAt))
            val create = server.takeRequest()
            assertEquals("POST", create.method)
            val body = create.body.readUtf8()
            assertTrue(body.contains("\"expires_at\":\"2026-11-09T03:00:00Z\""))
            assertTrue(body.contains("\"provider\":\"cloud\""))
            server.enqueue(MockResponse().setBody(wireJson.encodeToString(anchor)))
            assertEquals(anchor, api.get<RoomAnchor>("room_anchors", "row"))
            assertEquals("GET", server.takeRequest().method)
            server.enqueue(MockResponse().setBody(wireJson.encodeToString(anchor)))
            api.saveAnchor("row", "room", "cloud", "provider-id", Pose.IDENTITY, anchor.expiresAt)
            assertEquals("PATCH", server.takeRequest().method)
            server.enqueue(MockResponse().setResponseCode(204))
            api.delete("room_anchors", "row")
            assertEquals("DELETE", server.takeRequest().method)
        }

    @Test
    fun `spatial realtime update and delete replace rather than duplicate`() {
        val thing = Thing("thing", "room", poseInRoom = Pose.IDENTITY.toJson())
        val anchor = RoomAnchor("anchor", "room", "local", "uuid", Pose.IDENTITY.toJson())
        var state = SpaceState()
        state = state.merge(RealtimeEvent.Change("things", "create", wireJson.encodeToJsonElement(thing).jsonObject))
        state =
            state.merge(
                RealtimeEvent.Change(
                    "things",
                    "update",
                    wireJson.encodeToJsonElement(thing.copy(poseInRoom = null)).jsonObject,
                ),
            )
        assertEquals(listOf(thing.copy(poseInRoom = null)), state.things)
        state =
            state.merge(RealtimeEvent.Change("room_anchors", "create", wireJson.encodeToJsonElement(anchor).jsonObject))
        assertEquals(listOf(anchor), state.anchors)
        state =
            state.merge(RealtimeEvent.Change("room_anchors", "delete", wireJson.encodeToJsonElement(anchor).jsonObject))
        assertTrue(state.anchors.isEmpty())
        assertTrue("things/*" in Realtime.TOPICS && "room_anchors/*" in Realtime.TOPICS)
    }
}
