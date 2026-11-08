package dev.co508.ticketspace.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.co508.ticketspace.AppContainer
import dev.co508.ticketspace.ar.ArRequest
import dev.co508.ticketspace.ar.ArScreen

@Composable
inline fun <reified T : ViewModel> screenModel(
    key: String? = null,
    crossinline create: () -> T,
): T =
    viewModel(
        key = key,
        factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
            },
    )

@Composable
fun TicketSpace(app: AppContainer) {
    val session by app.auth.session.collectAsStateWithLifecycle()
    val ready by app.auth.ready.collectAsStateWithLifecycle()
    val error by app.auth.error.collectAsStateWithLifecycle()
    if (!ready) {
        Page("Ticket Space") { CircularProgressIndicator() }
    } else if (session.user == null) {
        SignInScreen(screenModel { SignInViewModel(app.api, app.spaces, app.auth) }, error)
    } else {
        val nav = rememberNavController()
        var arRequest by remember { mutableStateOf<ArRequest?>(null) }
        var arActive by remember { mutableStateOf(false) }
        val back: () -> Unit = { nav.popBackStack() }
        val navigate: (String) -> Unit = { path ->
            val route = path.substringBefore('?')
            if (route == "ar" || route == "register-room") {
                val uri = Uri.parse("ticketspace:///$path")
                arRequest =
                    ArRequest(uri.getQueryParameter("room"), uri.getQueryParameter("thing"), route == "register-room")
                arActive = true
            } else {
                nav.navigate(path)
            }
        }
        Box(Modifier.fillMaxSize()) {
            NavHost(navController = nav, startDestination = "home") {
                composable("home") { HomeScreen(screenModel { HomeViewModel(app.api, app.spaces) }, navigate) }
                composable("rooms") { RoomsScreen(screenModel { RoomsViewModel(app.api, app.spaces) }, back, navigate) }
                composable("room/{id}") { entry ->
                    RoomDetailScreen(
                        screenModel { RoomDetailViewModel(app.api, app.spaces) },
                        entry.arguments?.getString("id").orEmpty(),
                        back,
                        navigate,
                    )
                }
                composable(
                    "new-todo?room={room}",
                    arguments =
                        listOf(
                            navArgument("room") {
                                type = NavType.StringType
                                nullable =
                                    true
                                defaultValue = null
                            },
                        ),
                ) { entry ->
                    NewTodoScreen(
                        screenModel { NewTodoViewModel(app.api, app.spaces, app.auth) },
                        entry.arguments?.getString("room"),
                        back,
                    )
                }
                composable("todo/{id}") { entry ->
                    TodoDetailScreen(
                        screenModel { TodoDetailViewModel(app.api, app.spaces, app.auth) },
                        entry.arguments?.getString("id").orEmpty(),
                        back,
                        navigate,
                    )
                }
                composable("new-room") { NewRoomScreen(screenModel { NewRoomViewModel(app.api, app.spaces) }, back) }
                composable("settings") {
                    SettingsScreen(
                        screenModel { SettingsViewModel(app.api, app.spaces, app.auth, app.arSettings) },
                        back,
                    )
                }
            }
            arRequest?.let { request ->
                // A single retained AR surface preserves Local anchors when returning to the P0 screens.
                // Its lifecycle is paused while hidden; sign-out destroys the surface and session.
                Box(Modifier.fillMaxSize().zIndex(if (arActive) 1f else -1f)) {
                    ArScreen(app, request, arActive) { arActive = false }
                }
            }
        }
    }
}
