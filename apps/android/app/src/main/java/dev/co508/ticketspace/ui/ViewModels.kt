package dev.co508.ticketspace.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.co508.ticketspace.data.ArSettings
import dev.co508.ticketspace.data.AuthStore
import dev.co508.ticketspace.data.Priority
import dev.co508.ticketspace.data.Room
import dev.co508.ticketspace.data.SpaceStore
import dev.co508.ticketspace.data.TicketApi
import dev.co508.ticketspace.data.Todo
import dev.co508.ticketspace.data.TodoQuery
import dev.co508.ticketspace.data.TodoStatus
import dev.co508.ticketspace.data.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

data class Operation(
    val busy: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
)

open class ScreenViewModel(
    protected val api: TicketApi,
    protected val repo: SpaceStore,
) : ViewModel() {
    val spaces = repo.state
    private val mutableOperation = MutableStateFlow(Operation())
    val operation = mutableOperation.asStateFlow()

    fun perform(action: suspend () -> Unit) {
        if (mutableOperation.value.busy) return
        viewModelScope.launch {
            mutableOperation.value = Operation(busy = true)
            try {
                action()
                mutableOperation.value = Operation(success = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableOperation.value = Operation(error = e.userMessage())
            }
        }
    }

    fun retry() {
        viewModelScope.launch { repo.refresh() }
    }

    fun photoUrl(
        collection: String,
        id: String,
        name: String,
        thumb: String = "320x320",
    ) = api.fileUrl(collection, id, name, thumb)
}

class SignInViewModel(
    api: TicketApi,
    repo: SpaceStore,
    private val auth: AuthStore,
) : ScreenViewModel(api, repo) {
    var email by mutableStateOf("")
    var password by mutableStateOf("")

    fun signIn() =
        perform {
            require(email.isNotBlank() && password.isNotEmpty()) { "Enter your email and password." }
            auth.signIn(email.trim(), password)
            password = ""
        }
}

class HomeViewModel(
    api: TicketApi,
    repo: SpaceStore,
) : ScreenViewModel(api, repo) {
    var query by mutableStateOf(TodoQuery())
}

class RoomsViewModel(
    api: TicketApi,
    repo: SpaceStore,
) : ScreenViewModel(api, repo)

class RoomDetailViewModel(
    api: TicketApi,
    repo: SpaceStore,
) : ScreenViewModel(api, repo) {
    fun deleteRoom(room: Room) =
        perform {
            api.delete("rooms", room.id)
            repo.refresh()
        }
}

open class TodoFormViewModel(
    api: TicketApi,
    repo: SpaceStore,
    private val auth: AuthStore,
) : ScreenViewModel(api, repo) {
    var title by mutableStateOf("")
    var description by mutableStateOf("")
    var room by mutableStateOf("")
    var thing by mutableStateOf("")
    var priority by mutableStateOf(Priority.NORMAL)
    var tagInput by mutableStateOf("")
    val tags = mutableStateListOf<String>()
    val photos = mutableStateListOf<File>()
    val removedPhotos = mutableStateListOf<String>()

    fun addTag(value: String = tagInput) {
        value.trim().takeIf { it.isNotEmpty() && it !in tags }?.let(tags::add)
        tagInput = ""
    }

    fun removePhoto(file: File) {
        photos.remove(file)
        file.delete()
    }

    fun submit(
        id: String? = null,
        existingPhotos: Int = 0,
    ) = perform {
        require(title.isNotBlank() && title.length <= 200) { "Enter a title of at most 200 characters." }
        require(description.length <= 5000) { "Description must be at most 5000 characters." }
        require(room.isNotEmpty()) { "Choose a room first." }
        require(existingPhotos - removedPhotos.size + photos.size <= 3) { "A todo can have at most three photos." }
        addTag()
        val fields =
            buildJsonObject {
                put("title", title.trim())
                put("description", description.trim())
                put("room", room)
                put("thing", thing)
                put("priority", priority.value)
                put("tags", JsonArray(tags.map(::JsonPrimitive)))
                if (removedPhotos.isNotEmpty()) put("photos-", JsonArray(removedPhotos.map(::JsonPrimitive)))
                if (id == null) {
                    put("status", "open")
                    put("created_by", checkNotNull(auth.session.value.user).id)
                }
            }
        api.saveTodo(id, fields, photos.toList())
        photos.forEach { it.delete() }
        photos.clear()
        repo.refresh()
        removedPhotos.clear()
    }

    override fun onCleared() {
        photos.forEach { it.delete() }
    }
}

class NewTodoViewModel(
    api: TicketApi,
    repo: SpaceStore,
    auth: AuthStore,
) : TodoFormViewModel(api, repo, auth)

class TodoDetailViewModel(
    api: TicketApi,
    repo: SpaceStore,
    auth: AuthStore,
) : TodoFormViewModel(api, repo, auth) {
    private var initialized = false
    var editing by mutableStateOf(false)

    fun beginEditing(todo: Todo) {
        initialized = false
        tags.clear()
        removedPhotos.clear()
        photos.toList().forEach(::removePhoto)
        tagInput = ""
        initialize(todo)
        editing = true
    }

    fun initialize(todo: Todo) {
        if (initialized) return
        title = todo.title
        description = todo.description
        room = todo.room
        thing = todo.thing
        priority = todo.priorityLevel
        tags.addAll(todo.tags)
        initialized = true
    }

    fun toggle(todo: Todo) =
        perform {
            api.saveTodo(
                todo.id,
                buildJsonObject {
                    put("status", if (todo.status == TodoStatus.OPEN) "done" else "open")
                    put("priority", todo.priority)
                },
            )
            repo.refresh()
        }
}

class NewRoomViewModel(
    api: TicketApi,
    repo: SpaceStore,
) : ScreenViewModel(api, repo) {
    var name by mutableStateOf("")
    var site by mutableStateOf("")
    var panorama by mutableStateOf<File?>(null)

    fun submit() =
        perform {
            require(name.isNotBlank() && name.length <= 80) { "Enter a room name of at most 80 characters." }
            require(site.isNotEmpty()) { "No site is available. Ask an administrator to create one." }
            api.saveRoom(null, site, name.trim(), panorama)
            repo.refresh()
        }

    override fun onCleared() {
        panorama?.delete()
    }
}

class SettingsViewModel(
    api: TicketApi,
    repo: SpaceStore,
    private val auth: AuthStore,
    private val arSettingsStore: ArSettings,
) : ScreenViewModel(api, repo) {
    val session = auth.session
    val arSettings = arSettingsStore.data

    fun setProvider(provider: dev.co508.ticketspace.ar.providers.ProviderKind) =
        perform {
            arSettingsStore.setProvider(provider)
        }

    fun signOut() = perform { auth.signOut() }
}
