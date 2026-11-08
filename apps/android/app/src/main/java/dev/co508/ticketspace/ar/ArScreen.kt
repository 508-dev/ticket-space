package dev.co508.ticketspace.ar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.co508.ticketspace.AppContainer
import dev.co508.ticketspace.ar.providers.ProviderKind
import dev.co508.ticketspace.data.ArPreferences
import dev.co508.ticketspace.data.SpaceState
import dev.co508.ticketspace.ui.DataContent
import dev.co508.ticketspace.ui.Page
import java.util.UUID

data class ArRequest(
    val room: String?,
    val thing: String?,
    val registering: Boolean,
    val id: String = UUID.randomUUID().toString(),
)

@Composable
fun ArScreen(
    app: AppContainer,
    request: ArRequest,
    active: Boolean,
    back: () -> Unit,
) {
    val state by app.spaces.state.collectAsStateWithLifecycle()
    val preferences by app.arSettings.data.collectAsStateWithLifecycle(ArPreferences())
    val scope = rememberCoroutineScope()
    val runtime = remember { ArRuntime(app, scope, null) }
    var settingUp by remember(request.id) { mutableStateOf(request.registering) }
    var created by remember { mutableStateOf(false) }
    val plan = runtime.plan
    val requestedRoom = request.room
    val highlightedThing = request.thing
    DisposableEffect(runtime) { onDispose { runtime.close() } }
    LaunchedEffect(request.id) {
        runtime.cancelRegistration()
        runtime.locator.beginSearch(request.room)
    }
    BackHandler(active) { if (!runtime.busy) back() }
    // Include every room of the selected site in automatic localization, not just the requested room.
    val site = state.rooms.find { it.id == requestedRoom }?.site ?: plan?.site ?: state.sites.firstOrNull()?.id
    val rooms = state.rooms.filter { it.site == site }
    val anchors = state.anchors.filter { anchor -> rooms.any { it.id == anchor.room } }
    val cloud = plan?.provider == "cloud" || anchors.any { it.provider == "cloud" }
    Box(Modifier.fillMaxSize()) {
        ArGate(app, cloud, active && !settingUp, back) { ready ->
            val running = active && ready && !settingUp
            val lifecycle = rememberArLifecycle(running)
            LaunchedEffect(running) { if (running) created = true }
            LaunchedEffect(runtime.session, anchors, runtime.busy, running) {
                if (runtime.session != null && !runtime.busy && running) runtime.locator.reconcile(anchors)
            }
            if (created) {
                Scaffold { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        ArScene(runtime, state, cloud && ready, highlightedThing, lifecycle)
                        if (running) {
                            ArControls(runtime, state, rooms, highlightedThing, back)
                            EdgeArrows(
                                beacons(
                                    state,
                                    runtime.locator.room,
                                    runtime.locator.worldRoom,
                                    runtime.showThings,
                                    highlightedThing,
                                ),
                                runtime,
                            )
                        }
                        if (running) {
                            runtime.draft?.let { draft ->
                                Surface(Modifier.fillMaxSize()) {
                                    ArDraftForm(app, draft) { runtime.draft = null }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (active && settingUp) {
            key(request.id) {
                RegistrationSetup(state, requestedRoom, preferences.defaultProvider, back) {
                    runtime.plan = it
                    settingUp = false
                }
            }
        }
    }
}

@Composable
private fun RegistrationSetup(
    state: SpaceState,
    roomId: String?,
    defaultProvider: String,
    back: () -> Unit,
    start: (RegistrationPlan) -> Unit,
) {
    val existing = state.rooms.find { it.id == roomId }
    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var site by remember(state.sites, existing) {
        mutableStateOf(
            existing?.site ?: state.sites
                .firstOrNull()
                ?.id
                .orEmpty(),
        )
    }
    var provider by remember(defaultProvider) { mutableStateOf(defaultProvider) }
    Page(if (roomId == null) "Register room" else "Re-register room", back) {
        DataContent(state, {}) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Room name") })
            if (existing == null) {
                state.sites.forEach { value ->
                    FilterChip(selected = site == value.id, onClick = { site = value.id }, label = { Text(value.name) })
                }
            }
            Text("Anchor provider")
            ProviderKind.entries.forEach { value ->
                FilterChip(
                    selected = provider == value.id,
                    onClick = { provider = value.id },
                    label = { Text(value.label) },
                )
            }
            Text("Tap three textured surfaces spread around the room. The first anchor defines the room origin.")
            if (provider ==
                "local"
            ) {
                Text("Local anchors last in this app session. Restarting the app or signing out loses them.")
            }
            if (existing !=
                null
            ) {
                Text(
                    "Locate an old anchor before saving to carry Things over. Otherwise all Things become unplaced; their todos remain available.",
                )
            }
            Button(
                onClick = { start(RegistrationPlan(existing, site, name.trim(), provider)) },
                enabled =
                    name.isNotBlank() && name.length <= 80 && site.isNotEmpty() && (roomId == null || existing != null),
            ) { Text("Start scan") }
        }
    }
}
