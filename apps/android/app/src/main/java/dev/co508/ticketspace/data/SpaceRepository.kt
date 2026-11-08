package dev.co508.ticketspace.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.decodeFromJsonElement

data class SpaceState(
    val loading: Boolean = true,
    val error: String? = null,
    val liveError: String? = null,
    val todos: List<Todo> = emptyList(),
    val rooms: List<Room> = emptyList(),
    val sites: List<Site> = emptyList(),
    val things: List<Thing> = emptyList(),
    val anchors: List<RoomAnchor> = emptyList(),
)

fun SpaceState.merge(event: RealtimeEvent.Change): SpaceState =
    when (event.collection) {
        "todos" ->
            copy(
                todos =
                    mergeEvent(
                        todos,
                        event.action,
                        wireJson.decodeFromJsonElement<Todo>(event.record),
                    ).sortedWith(todoOrder),
            )
        "rooms" ->
            copy(
                rooms =
                    mergeEvent(
                        rooms,
                        event.action,
                        wireJson.decodeFromJsonElement<Room>(event.record),
                    ).sortedBy {
                        it.name
                    },
            )
        "things" -> copy(things = mergeEvent(things, event.action, wireJson.decodeFromJsonElement<Thing>(event.record)))
        "room_anchors" ->
            copy(
                anchors = mergeEvent(anchors, event.action, wireJson.decodeFromJsonElement<RoomAnchor>(event.record)),
            )
        else -> this
    }

/**
 * The subset of [SpaceRepository] the screens and view models depend on.
 * Extracted so view models can be unit-tested against fakes without an
 * Android [android.content.Context].
 */
interface SpaceStore {
    val state: StateFlow<SpaceState>

    suspend fun refresh()
}

class SpaceRepository(
    private val api: PocketBaseClient,
    private val realtime: Realtime,
) : SpaceStore {
    private val mutableState = MutableStateFlow(SpaceState())
    override val state = mutableState.asStateFlow()
    private val mutex = Mutex()
    private var connected = false

    suspend fun watch() {
        realtime.events().collect { event ->
            when (event) {
                RealtimeEvent.Connected -> {
                    connected = true
                    refresh()
                }
                is RealtimeEvent.Disconnected ->
                    mutex.withLock {
                        connected = false
                        mutableState.value = mutableState.value.copy(loading = false, liveError = event.message)
                    }
                is RealtimeEvent.Change ->
                    mutex.withLock {
                        try {
                            mutableState.value = mutableState.value.merge(event)
                        } catch (e: Exception) {
                            mutableState.value =
                                mutableState.value.copy(
                                    liveError = "Could not read a live update. Retry to reload records.",
                                )
                        }
                    }
            }
        }
    }

    override suspend fun refresh() =
        mutex.withLock {
            mutableState.value = mutableState.value.copy(loading = true, error = null)
            try {
                // Fetch after subscription; queued events are then merged after this snapshot.
                mutableState.value =
                    SpaceState(
                        loading = false,
                        liveError = if (connected) null else "Live updates are disconnected. Reconnecting…",
                        todos = api.todos(TodoQuery(showDone = true)).sortedWith(todoOrder),
                        rooms = api.list<Room>("rooms", sort = "name"),
                        sites = api.list("sites"),
                        things = api.list("things"),
                        anchors = api.list("room_anchors"),
                    )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.value = SpaceState(loading = false, error = e.userMessage())
            }
        }

    suspend fun clear() =
        mutex.withLock {
            connected = false
            mutableState.value = SpaceState()
        }
}

fun Exception.userMessage(): String =
    when (this) {
        is ApiException -> message ?: "The request failed."
        is java.io.IOException -> "Cannot connect to Ticket Space. Check your connection and retry."
        else -> message ?: "Something went wrong. Please retry."
    }
