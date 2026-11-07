package dev.co508.ticketspace.data

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PocketBaseClientTest {
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
    fun `auth endpoints and authorization token`() =
        runBlocking {
            server.enqueue(
                MockResponse().setBody(
                    """{"token":"secret-token","record":{"id":"u","email":"person@example.com","name":"Person"}}""",
                ),
            )
            val auth = api.authWithPassword("person@example.com", "password")
            val request = server.takeRequest()
            assertEquals("/api/collections/users/auth-with-password", request.path)
            assertTrue(request.body.readUtf8().contains("\"identity\":\"person@example.com\""))
            api.token = auth.token
            server.enqueue(MockResponse().setBody(wireJson.encodeToString(auth)))
            api.refreshAuth()
            val refresh = server.takeRequest()
            assertEquals("/api/collections/users/auth-refresh", refresh.path)
            assertEquals("secret-token", refresh.getHeader("Authorization"))
        }

    @Test
    fun `list sends encoded filters sort and paginates`() =
        runBlocking {
            server.enqueue(MockResponse().setBody("""{"totalPages":2,"items":[{"id":"s1","name":"Home"}]}"""))
            server.enqueue(MockResponse().setBody("""{"totalPages":2,"items":[{"id":"s2","name":"Other"}]}"""))
            val query = TodoQuery("r", "wood & paint")
            assertEquals(2, api.list<Site>("sites", query.filter, TodoQuery.SORT).size)
            val first = server.takeRequest().requestUrl!!
            assertEquals(query.filter, first.queryParameter("filter"))
            assertEquals("-priority,created", first.queryParameter("sort"))
            assertEquals("2", server.takeRequest().requestUrl!!.queryParameter("page"))
        }

    @Test
    fun `multipart uploads photos and JSON tags without sending completion fields`() =
        runBlocking {
            val photo = File.createTempFile("ts-test", ".jpg").apply { writeText("fake-jpeg") }
            try {
                server.enqueue(
                    MockResponse().setBody(
                        """{"id":"t","title":"Door","room":"r","priority":1,"status":"open","created_by":"u"}""",
                    ),
                )
                api.saveTodo(
                    null,
                    buildJsonObject {
                        put("title", "Door")
                        put("created_by", "u")
                    },
                    listOf(photo),
                )
                val request = server.takeRequest()
                assertEquals("POST", request.method)
                assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
                val body = request.body.readUtf8()
                assertTrue(body.contains("name=\"photos+\""))
                assertTrue(body.contains("fake-jpeg"))
                assertFalse(body.contains("done_by"))
                server.enqueue(MockResponse().setResponseCode(204))
                api.delete("todos", "t")
                assertEquals("DELETE", server.takeRequest().method)
            } finally {
                photo.delete()
            }
        }

    @Test
    fun `file URL encodes names and requests expected thumbnails`() {
        val photo = api.fileUrl("todos", "t", "a b.jpg")
        assertTrue(photo.endsWith("/api/files/todos/t/a%20b.jpg?thumb=320x320"))
        assertTrue(api.fileUrl("rooms", "r", "p.jpg", "480x240").endsWith("?thumb=480x240"))
    }

    @Test
    fun `HTTP errors are actionable`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(403))
            try {
                api.list<Site>("sites")
                throw AssertionError("Expected API failure")
            } catch (e: ApiException) {
                assertEquals(403, e.code)
                assertTrue(e.message!!.contains("sign in"))
            }
        }

    @Test
    fun `PB_CONNECT posts authenticated subscriptions`() =
        runBlocking {
            api.token = "auth-token"
            // Throttle the trailing comment to keep the first SSE response open while POST completes.
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("event: PB_CONNECT\ndata: {\"clientId\":\"client1\"}\n\n" + ":".repeat(8192))
                    .throttleBody(128, 1, TimeUnit.SECONDS),
            )
            server.enqueue(MockResponse().setResponseCode(204))
            withTimeout(10_000) {
                val connected = async { Realtime(api).events().first { it == RealtimeEvent.Connected } }
                assertEquals(RealtimeEvent.Connected, connected.await())
            }
            val get = server.takeRequest()
            val post = server.takeRequest()
            assertEquals("GET", get.method)
            assertEquals("POST", post.method)
            assertEquals("/api/realtime", post.path)
            assertEquals("auth-token", post.getHeader("Authorization"))
            val body = post.body.readUtf8()
            assertTrue(body.contains("client1"))
            Realtime.TOPICS.forEach { assertTrue(body.contains(it)) }
        }

    @Test
    fun `realtime retries failed connection and resubscribes before receiving changes`() =
        runBlocking {
            val gets = AtomicInteger()
            api.token = "auth-token"
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        if (request.method == "POST") return MockResponse().setResponseCode(204)
                        if (gets.incrementAndGet() == 1) return MockResponse().setResponseCode(503)
                        val connect = "event: PB_CONNECT\ndata: {\"clientId\":\"reconnected\"}\n\n"
                        val comment = ":" + " ".repeat(256) + "\n\n"
                        val change =
                            "event: rooms/*\ndata: {\"action\":\"create\",\"record\":" +
                                "{\"id\":\"r\",\"site\":\"s\",\"name\":\"New room\"}}\n\n"
                        return MockResponse()
                            .setHeader("Content-Type", "text/event-stream")
                            .setBody(
                                connect + comment + change + ":".repeat(8192),
                            ).throttleBody(128, 100, TimeUnit.MILLISECONDS)
                    }
                }
            val events = mutableListOf<RealtimeEvent>()
            val change =
                withTimeout(10_000) {
                    Realtime(api).events().onEach { events.add(it) }.first { it is RealtimeEvent.Change }
                } as RealtimeEvent.Change
            assertEquals("rooms", change.collection)
            assertEquals("create", change.action)
            assertTrue(events.first() is RealtimeEvent.Disconnected)
            assertTrue(RealtimeEvent.Connected in events)
            assertEquals(2, gets.get())
            val requests = (1..3).map { server.takeRequest() }
            val post = requests.single { it.method == "POST" }
            assertEquals("auth-token", post.getHeader("Authorization"))
            assertTrue(post.body.readUtf8().contains("reconnected"))
        }
}
