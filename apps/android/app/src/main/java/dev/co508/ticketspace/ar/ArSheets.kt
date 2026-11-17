package dev.co508.ticketspace.ar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.co508.ticketspace.data.Room
import dev.co508.ticketspace.data.SpaceState
import dev.co508.ticketspace.data.TodoStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArSheets(
    runtime: ArRuntime,
    state: SpaceState,
    rooms: List<Room>,
    picker: Boolean,
    unplaced: Boolean,
    closePicker: () -> Unit,
    closeUnplaced: () -> Unit,
) {
    val room = rooms.find { it.id == runtime.locator.room }
    val located = runtime.locator.worldRoom != null
    if (picker) {
        ModalBottomSheet(onDismissRequest = closePicker) {
            SheetColumn {
                Text("Choose room", style = MaterialTheme.typography.titleLarge)
                rooms.forEach { value ->
                    TextButton(onClick = {
                        runtime.locator.pickRoom(value.id)
                        runtime.placing = false
                        runtime.moving = null
                    }) { Text(value.name) }
                }
                room?.let { value ->
                    Text("${value.name} · ${if (located) "located" else "not located"}")
                    Button(onClick = {
                        closePicker()
                        runtime.draft = ArTodoDraft(value.id, "", null)
                    }) {
                        Text("New room-level todo")
                    }
                    state.todos.filter { it.room == value.id && it.status == TodoStatus.OPEN }.forEach { todo ->
                        Text(todo.title)
                        TextButton(onClick = { runtime.markDone(todo) }, enabled = !runtime.busy) { Text("Mark done") }
                    }
                }
            }
        }
    }
    if (unplaced) {
        ModalBottomSheet(onDismissRequest = closeUnplaced) {
            SheetColumn {
                Text("Unplaced Things", style = MaterialTheme.typography.titleLarge)
                if (!located) Text("Locate this room before placing Things.")
                val things = state.things.filter { it.room == room?.id && it.poseInRoom == null }
                if (things.isEmpty()) Text("No unplaced Things in this room.")
                things.forEach { thing ->
                    Button(
                        onClick = {
                            runtime.moving = thing
                            runtime.placing = true
                            closeUnplaced()
                        },
                        enabled = located && !runtime.busy,
                    ) {
                        Text(thing.displayLabel)
                    }
                }
            }
        }
    }
    (runtime.newThing ?: state.things.find { it.id == runtime.selected })?.let { thing ->
        ThingEditor(runtime, state, thing)
    }
}

@Composable
internal fun SheetColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
