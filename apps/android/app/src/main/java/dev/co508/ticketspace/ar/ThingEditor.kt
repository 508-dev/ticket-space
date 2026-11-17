package dev.co508.ticketspace.ar

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.co508.ticketspace.data.SpaceState
import dev.co508.ticketspace.data.Thing

fun deleteThingConfirmation(todoCount: Int): String = "Its $todoCount todos stay in the room as room-level todos."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThingEditor(
    runtime: ArRuntime,
    state: SpaceState,
    thing: Thing,
) {
    val context = LocalContext.current
    val isNew = thing.id.isEmpty()
    var label by remember(thing.id, thing.label) { mutableStateOf(thing.label) }
    var confirmDelete by remember(thing.id) { mutableStateOf(false) }
    val located = runtime.locator.worldRoom != null && runtime.locator.room == thing.room
    val close = {
        if (!runtime.busy) {
            runtime.selected = null
            runtime.newThing = null
        }
    }
    ModalBottomSheet(onDismissRequest = close) {
        SheetColumn {
            Text(if (isNew) "New Thing" else thing.displayLabel, style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(if (isNew) "Label (required)" else "Label") },
                singleLine = true,
                enabled = !runtime.busy,
                isError = label.trim().length > 80,
            )
            Button(
                onClick = { runtime.saveThingLabel(thing, label) },
                enabled = !runtime.busy && label.trim().length <= 80 && (!isNew || label.isNotBlank()),
            ) { Text("Save label") }
            runtime.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (!isNew) {
                val todos = openThingTodos(state.todos, thing.id)
                if (todos.isEmpty()) Text("No open todos here.")
                todos.forEach { todo ->
                    TextButton(onClick = { runtime.detail = todo.id }, enabled = !runtime.busy) {
                        Text("${todo.priorityLevel.label} · ${todo.title}")
                    }
                    TextButton(onClick = { runtime.markDone(todo) }, enabled = !runtime.busy) { Text("Mark done") }
                }
                Button(onClick = { runtime.addTodo(context, thing) }, enabled = !runtime.busy) { Text("Add todo") }
                OutlinedButton(
                    onClick = {
                        runtime.selected = null
                        runtime.moving = thing
                        runtime.placing = true
                    },
                    enabled = !runtime.busy && located,
                ) { Text("Move") }
                TextButton(onClick = { confirmDelete = true }, enabled = !runtime.busy) { Text("Delete Thing") }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { if (!runtime.busy) confirmDelete = false },
            title = { Text("Delete ${thing.displayLabel}?") },
            text = { Text(deleteThingConfirmation(state.todos.count { it.thing == thing.id })) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    runtime.deleteThing(thing)
                }, enabled = !runtime.busy) { Text("Delete Thing") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
