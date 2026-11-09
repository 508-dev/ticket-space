package dev.co508.ticketspace.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.co508.ticketspace.BuildConfig
import dev.co508.ticketspace.ar.providers.CLOUD_TTL_DAYS
import dev.co508.ticketspace.ar.providers.ProviderKind
import dev.co508.ticketspace.data.ArPreferences
import dev.co508.ticketspace.data.Priority
import dev.co508.ticketspace.data.TodoStatus
import dev.co508.ticketspace.data.matching

@Composable
fun SignInScreen(
    vm: SignInViewModel,
    restoreError: String?,
) {
    val operation by vm.operation.collectAsStateWithLifecycle()
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    Page("Ticket Space") {
        Text("Small fixes. Shared spaces.", style = MaterialTheme.typography.titleLarge)
        Text("Sign in to see what needs doing in your space.")
        restoreError?.let { ErrorMessage(it) }
        OutlinedTextField(
            value = vm.email,
            onValueChange = { vm.email = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        OutlinedTextField(
            value = vm.password,
            onValueChange = { vm.password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            singleLine = true,
            visualTransformation =
                if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector =
                            if (passwordVisible) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                    )
                }
            },
        )
        Button(
            onClick = vm::signIn,
            enabled = !operation.busy && vm.email.isNotBlank() && vm.password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Sign in") }
        OperationMessage(operation)
        Text("Accounts are created by your space administrator.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    navigate: (String) -> Unit,
) {
    val state by vm.spaces.collectAsStateWithLifecycle()
    Page("Your space") {
        Row {
            TextButton(onClick = { navigate("rooms") }) { Text("Rooms") }
            TextButton(onClick = { navigate("ar") }) { Text("AR") }
            TextButton(onClick = { navigate("settings") }) { Text("Settings") }
        }
        Button(onClick = { navigate("new-todo") }) { Text("New todo") }
        DataContent(state, vm::retry) {
            Text("Room", style = MaterialTheme.typography.labelLarge)
            ChipRow {
                FilterChip(selected = vm.query.room == null, onClick = {
                    vm.query = vm.query.copy(room = null)
                }, label = { Text("All rooms") })
                state.rooms.forEach { room ->
                    FilterChip(selected = vm.query.room == room.id, onClick = {
                        vm.query = vm.query.copy(room = room.id)
                    }, label = { Text(room.name) })
                }
            }
            Text("Tag", style = MaterialTheme.typography.labelLarge)
            ChipRow {
                FilterChip(selected = vm.query.tag == null, onClick = {
                    vm.query = vm.query.copy(tag = null)
                }, label = { Text("All tags") })
                state.todos.flatMap { it.tags }.distinct().sorted().forEach { tag ->
                    FilterChip(
                        selected = vm.query.tag == tag,
                        onClick = { vm.query = vm.query.copy(tag = tag) },
                        label = { Text(tag) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Show done todos")
                Switch(checked = vm.query.showDone, onCheckedChange = { vm.query = vm.query.copy(showDone = it) })
            }
            val todos = state.todos.matching(vm.query)
            if (todos.isEmpty()) Text("No todos here yet. Add one or change your filters.")
            todos.forEach { todo ->
                TodoCard(todo, state.rooms.find { it.id == todo.room }?.name) { navigate("todo/${todo.id}") }
            }
        }
    }
}

@Composable
fun RoomsScreen(
    vm: RoomsViewModel,
    back: () -> Unit,
    navigate: (String) -> Unit,
) {
    val state by vm.spaces.collectAsStateWithLifecycle()
    Page("Rooms", back) {
        Button(onClick = { navigate("new-room") }) { Text("New room") }
        OutlinedButton(onClick = { navigate("register-room") }) { Text("Register room in AR") }
        DataContent(state, vm::retry) {
            if (state.rooms.isEmpty()) Text("No rooms yet. Create your first room to start adding todos.")
            state.rooms.forEach { room ->
                androidx.compose.material3.Card(onClick = { navigate("room/${room.id}") }) {
                    Column {
                        if (room.panorama.isNotEmpty()) {
                            Photo(
                                vm.photoUrl("rooms", room.id, room.panorama, "480x240"),
                                "${room.name} panorama",
                            )
                        }
                        Text(room.name, style = MaterialTheme.typography.titleLarge)
                        Text("${state.todos.count { it.room == room.id && it.status == TodoStatus.OPEN }} open todos")
                    }
                }
            }
        }
    }
}

@Composable
fun RoomDetailScreen(
    vm: RoomDetailViewModel,
    id: String,
    back: () -> Unit,
    navigate: (String) -> Unit,
) {
    val state by vm.spaces.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(operation.success) { if (operation.success) back() }
    Page(state.rooms.find { it.id == id }?.name ?: "Room", back) {
        DataContent(state, vm::retry) {
            val room = state.rooms.find { it.id == id }
            if (room == null) {
                Text("This room was not found or has been deleted.")
            } else {
                if (room.panorama.isNotEmpty()) {
                    Photo(
                        vm.photoUrl("rooms", id, room.panorama, "480x240"),
                        "Room panorama",
                    )
                }
                Button(onClick = { navigate("ar?room=$id") }) { Text("Open AR") }
                OutlinedButton(onClick = { navigate("register-room?room=$id") }) { Text("Re-register") }
                OutlinedButton(onClick = { navigate("new-todo?room=$id") }) { Text("New todo here") }
                val todos = state.todos.filter { it.room == id }
                if (todos.isEmpty()) Text("This room has no todos yet.")
                todos.forEach { todo -> TodoCard(todo, null) { navigate("todo/${todo.id}") } }
                OutlinedButton(onClick = { confirmDelete = true }, enabled = !operation.busy) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Text("Delete room")
                }
            }
        }
        OperationMessage(operation)
        if (confirmDelete) {
            val room = state.rooms.find { it.id == id }
            if (room != null) {
                AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    title = { Text("Delete room") },
                    text = {
                        Text(
                            deleteRoomConfirmation(
                                room.name,
                                state.todos.count { it.room == id },
                                state.things.count { it.room == id },
                            ),
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                confirmDelete = false
                                vm.deleteRoom(room)
                            },
                        ) { Text("Delete") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
                    },
                )
            }
        }
    }
}

fun deleteRoomConfirmation(
    roomName: String,
    todoCount: Int,
    thingCount: Int,
): String =
    "Delete $roomName? This permanently deletes its $todoCount todos and $thingCount things. " +
        "Anchors hosted on Google expire on their own."

@Composable
fun NewTodoScreen(
    vm: NewTodoViewModel,
    room: String?,
    back: () -> Unit,
) {
    val state by vm.spaces.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    LaunchedEffect(room) { if (vm.room.isEmpty()) vm.room = room.orEmpty() }
    LaunchedEffect(operation.success) { if (operation.success) back() }
    Page("New todo", back) {
        DataContent(state, vm::retry) {
            if (state.rooms.isEmpty()) {
                Text("Create a room before adding a todo.")
            } else {
                TodoForm(vm)
                Button(onClick = { vm.submit() }, enabled = !operation.busy) { Text("Create todo") }
            }
        }
        OperationMessage(operation)
    }
}

@Composable
fun TodoDetailScreen(
    vm: TodoDetailViewModel,
    id: String,
    back: () -> Unit,
    navigate: (String) -> Unit,
) {
    val state by vm.spaces.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    val todo = state.todos.find { it.id == id }
    LaunchedEffect(todo?.id) { todo?.let(vm::initialize) }
    LaunchedEffect(operation.success) { if (operation.success) vm.editing = false }
    Page(if (vm.editing) "Edit todo" else "Todo", back) {
        DataContent(state, vm::retry) {
            if (todo == null) {
                Text("This todo was not found or has been deleted.")
            } else if (vm.editing) {
                TodoForm(vm, todo.id, todo.photos)
                Button(
                    onClick = { vm.submit(id, todo.photos.size) },
                    enabled = !operation.busy,
                ) { Text("Save changes") }
                TextButton(onClick = { vm.editing = false }) { Text("Cancel editing") }
            } else {
                Text(todo.title, style = MaterialTheme.typography.headlineMedium)
                Text("${todo.priorityLevel.label} · ${if (todo.status == TodoStatus.DONE) "Done" else "Open"}")
                Text(state.rooms.find { it.id == todo.room }?.name ?: "Room unavailable")
                Text(todo.description.ifEmpty { "No description." })
                Text(todo.tags.joinToString("  ") { "#$it" }.ifEmpty { "No tags." })
                todo.photos.forEach { Photo(vm.photoUrl("todos", id, it), "Todo photo") }
                if (todo.photos.isEmpty()) Text("No photos attached.")
                Button(onClick = { vm.toggle(todo) }, enabled = !operation.busy) {
                    Text(if (todo.status == TodoStatus.OPEN) "Mark done" else "Reopen")
                }
                OutlinedButton(onClick = { vm.beginEditing(todo) }, enabled = !operation.busy) { Text("Edit") }
                if (todo.thing.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { navigate("ar?room=${todo.room}&thing=${todo.thing}") },
                    ) { Text("Show in AR") }
                }
            }
        }
        OperationMessage(operation)
    }
}

@Composable
private fun TodoForm(
    vm: TodoFormViewModel,
    todoId: String = "",
    existingPhotos: List<String> = emptyList(),
) {
    val state by vm.spaces.collectAsStateWithLifecycle()
    OutlinedTextField(value = vm.title, onValueChange = {
        vm.title = it
    }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(value = vm.description, onValueChange = {
        vm.description = it
    }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
    Text("Room")
    ChipRow {
        state.rooms.forEach { room ->
            FilterChip(selected = vm.room == room.id, onClick = {
                if (vm.room != room.id) vm.thing = ""
                vm.room = room.id
            }, label = { Text(room.name) })
        }
    }
    Text("Priority")
    ChipRow {
        Priority.entries.forEach { priority ->
            FilterChip(
                selected = vm.priority == priority,
                onClick = { vm.priority = priority },
                label = { Text(priority.label) },
            )
        }
    }
    OutlinedTextField(value = vm.tagInput, onValueChange = {
        vm.tagInput = it
    }, label = { Text("Add a tag") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    TextButton(onClick = { vm.addTag() }, enabled = vm.tagInput.isNotBlank()) { Text("Add tag") }
    if (vm.tagInput.isNotBlank()) {
        ChipRow {
            state.todos
                .flatMap { it.tags }
                .distinct()
                .sorted()
                .filter { it.contains(vm.tagInput, ignoreCase = true) && it !in vm.tags }
                .take(8)
                .forEach { tag ->
                    FilterChip(selected = false, onClick = { vm.addTag(tag) }, label = { Text(tag) })
                }
        }
    }
    ChipRow {
        vm.tags.toList().forEach { tag ->
            FilterChip(selected = true, onClick = { vm.tags.remove(tag) }, label = { Text("$tag ×") })
        }
    }
    val retainedPhotos = existingPhotos.filterNot { it in vm.removedPhotos }
    Text("Photos (${retainedPhotos.size + vm.photos.size}/3)")
    retainedPhotos.forEach { name ->
        Photo(vm.photoUrl("todos", todoId, name), "Attached todo photo")
        TextButton(onClick = { vm.removedPhotos.add(name) }) { Text("Remove photo") }
    }
    vm.photos.toList().forEach { file ->
        Photo(file, "New todo photo")
        TextButton(onClick = { vm.removePhoto(file) }) { Text("Remove photo") }
    }
    CameraButton("Take photo", enabled = retainedPhotos.size + vm.photos.size < 3) { file ->
        if (existingPhotos.count { it !in vm.removedPhotos } + vm.photos.size <
            3
        ) {
            vm.photos.add(file)
        } else {
            file.delete()
        }
    }
}

@Composable
fun NewRoomScreen(
    vm: NewRoomViewModel,
    back: () -> Unit,
) {
    val state by vm.spaces.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    LaunchedEffect(state.sites) {
        if (vm.site.isEmpty()) {
            vm.site =
                state.sites
                    .firstOrNull()
                    ?.id
                    .orEmpty()
        }
    }
    LaunchedEffect(operation.success) { if (operation.success) back() }
    Page("New room", back) {
        DataContent(state, vm::retry) {
            if (state.sites.isEmpty()) {
                Text("No site is configured. Ask your space administrator to create one.")
            } else {
                if (state.sites.size > 1) {
                    ChipRow {
                        state.sites.forEach { site ->
                            FilterChip(
                                selected = vm.site == site.id,
                                onClick = { vm.site = site.id },
                                label = { Text(site.name) },
                            )
                        }
                    }
                }
                OutlinedTextField(value = vm.name, onValueChange = {
                    vm.name = it
                }, label = { Text("Room name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Optional panorama/reference photo")
                vm.panorama?.let { file -> Photo(file, "Room panorama") }
                CameraButton("Take room photo") { file ->
                    vm.panorama?.delete()
                    vm.panorama = file
                }
                if (vm.panorama != null) {
                    TextButton(onClick = {
                        vm.panorama?.delete()
                        vm.panorama = null
                    }) { Text("Remove photo") }
                }
                Button(onClick = vm::submit, enabled = !operation.busy) { Text("Create room") }
            }
        }
        OperationMessage(operation)
    }
}

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    back: () -> Unit,
) {
    val session by vm.session.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    Page("Settings", back) {
        Text(
            session.user?.name?.ifEmpty { "Your account" } ?: "Signed out",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(session.user?.email.orEmpty())
        if (BuildConfig.DEBUG) {
            Text("Backend (debug only)", style = MaterialTheme.typography.labelLarge)
            Text(BuildConfig.BASE_URL)
        }
        val arSettings by vm.arSettings.collectAsStateWithLifecycle(ArPreferences())
        Text("Default anchor provider")
        ProviderKind.entries.forEach { provider ->
            FilterChip(selected = arSettings.defaultProvider == provider.id, onClick = {
                vm.setProvider(provider)
            }, label = { Text(provider.label) })
        }
        Text(
            "Local anchors last in this app session. Cloud anchors last $CLOUD_TTL_DAYS day(s).",
        )
        Button(onClick = vm::signOut, enabled = !operation.busy) { Text("Sign out") }
        OperationMessage(operation)
    }
}

@Composable
fun ChipRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
