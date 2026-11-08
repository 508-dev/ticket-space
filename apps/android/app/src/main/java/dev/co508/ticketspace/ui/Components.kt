package dev.co508.ticketspace.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil3.compose.SubcomposeAsyncImage
import dev.co508.ticketspace.data.SpaceState
import dev.co508.ticketspace.data.Todo
import dev.co508.ticketspace.data.TodoStatus
import dev.co508.ticketspace.data.cameraFile
import dev.co508.ticketspace.data.cameraUri
import dev.co508.ticketspace.data.preparePhoto
import dev.co508.ticketspace.data.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun Page(
    title: String,
    back: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (back != null) TextButton(onClick = back) { Text("‹ Back") }
            Text(title, style = MaterialTheme.typography.headlineLarge)
            content()
        }
    }
}

@Composable
fun ErrorMessage(message: String) {
    Card(Modifier.fillMaxWidth()) {
        Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun DataContent(
    state: SpaceState,
    retry: () -> Unit,
    content: @Composable () -> Unit,
) {
    when {
        state.loading -> CircularProgressIndicator()
        state.error != null -> {
            ErrorMessage(state.error)
            Button(onClick = retry) { Text("Retry") }
        }
        else -> {
            state.liveError?.let {
                ErrorMessage(it)
                Text("Displayed records may be out of date.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = retry) { Text("Retry now") }
            }
            content()
        }
    }
}

@Composable
fun OperationMessage(operation: Operation) {
    if (operation.busy) CircularProgressIndicator()
    operation.error?.let { ErrorMessage(it) }
}

@Composable
fun TodoCard(
    todo: Todo,
    roomName: String?,
    open: () -> Unit,
) {
    Card(onClick = open, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(todo.title, style = MaterialTheme.typography.titleMedium)
            Text("${todo.priorityLevel.label} · ${if (todo.status == TodoStatus.DONE) "Done" else "Open"}")
            if (roomName != null) Text(roomName, style = MaterialTheme.typography.bodySmall)
            if (todo.tags.isNotEmpty()) Text(todo.tags.joinToString("  ") { "#$it" })
        }
    }
}

@Composable
fun Photo(
    model: Any,
    description: String,
) {
    SubcomposeAsyncImage(
        model = model,
        contentDescription = description,
        modifier = Modifier.fillMaxWidth().height(180.dp),
        contentScale = ContentScale.Crop,
        loading = { CircularProgressIndicator() },
        error = { Text("Photo unavailable. Check your connection.") },
    )
}

@Composable
fun CameraButton(
    label: String,
    enabled: Boolean = true,
    onPhoto: (File) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val source = pendingPath?.let(::File)
            pendingPath = null
            if (success && source != null) {
                scope.launch {
                    busy = true
                    try {
                        onPhoto(preparePhoto(context, source))
                    } catch (e: CancellationException) {
                        source.delete()
                        throw e
                    } catch (e: Exception) {
                        error = e.userMessage()
                    } finally {
                        busy = false
                    }
                }
            } else {
                source?.delete()
            }
        }
    val capture = {
        try {
            error = null
            val file = cameraFile(context)
            pendingPath = file.absolutePath
            launcher.launch(cameraUri(context, file))
        } catch (e: ActivityNotFoundException) {
            pendingPath?.let { File(it).delete() }
            pendingPath = null
            error = "No camera app is available on this device."
        } catch (e: Exception) {
            pendingPath = null
            error = "Could not open the camera. Please retry."
        }
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                capture()
            } else {
                error =
                    "Camera permission is needed to take a photo. You can allow it in app settings."
            }
        }
    Row {
        Button(enabled = enabled && !busy && pendingPath == null, onClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                capture()
            } else {
                permission.launch(Manifest.permission.CAMERA)
            }
        }) { Text(if (busy) "Preparing photo…" else label) }
    }
    error?.let { ErrorMessage(it) }
}
