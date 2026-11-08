package dev.co508.ticketspace.ui

import dev.co508.ticketspace.data.ApiException
import dev.co508.ticketspace.data.Room
import dev.co508.ticketspace.data.SpaceState
import dev.co508.ticketspace.data.SpaceStore
import dev.co508.ticketspace.data.TicketApi
import dev.co508.ticketspace.data.Todo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class RoomDetailViewModelTest {
    private val room = Room(id = "r1", site = "s1", name = "Kitchen")

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `delete flow calls the API and refreshes on success`() =
        runTest {
            val api = FakeTicketApi()
            val repo = FakeSpaceStore()
            val vm = RoomDetailViewModel(api, repo)

            vm.deleteRoom(room)
            advanceUntilIdle()

            assertEquals(listOf("r1"), api.deleted)
            assertEquals(1, repo.refreshCount)
            assertTrue(vm.operation.value.success)
            assertFalse(vm.operation.value.busy)
            assertNull(vm.operation.value.error)
        }

    @Test
    fun `delete flow surfaces an error and does not refresh on failure`() =
        runTest {
            val api = FakeTicketApi(fail = true)
            val repo = FakeSpaceStore()
            val vm = RoomDetailViewModel(api, repo)

            vm.deleteRoom(room)
            advanceUntilIdle()

            assertEquals(listOf("r1"), api.deleted)
            assertEquals(0, repo.refreshCount)
            assertFalse(vm.operation.value.success)
            assertTrue(vm.operation.value.error != null)
        }
}

private class FakeTicketApi(
    private val fail: Boolean = false,
) : TicketApi {
    val deleted = mutableListOf<String>()

    override fun fileUrl(
        collection: String,
        id: String,
        filename: String,
        thumb: String,
    ) = "https://example.com/files/$collection/$id/$filename"

    override suspend fun saveTodo(
        id: String?,
        fields: JsonObject,
        photos: List<File>,
    ): Todo = error("not used")

    override suspend fun saveRoom(
        id: String?,
        site: String,
        name: String,
        panorama: File?,
    ): Room = error("not used")

    override suspend fun delete(
        collection: String,
        id: String,
    ) {
        deleted += id
        if (fail) throw ApiException(404, "This record is no longer available.")
    }
}

private class FakeSpaceStore : SpaceStore {
    private val mutable = MutableStateFlow(SpaceState())
    override val state: StateFlow<SpaceState> = mutable.asStateFlow()
    var refreshCount = 0

    override suspend fun refresh() {
        refreshCount++
    }
}
