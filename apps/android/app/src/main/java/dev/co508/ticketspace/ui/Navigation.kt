package dev.co508.ticketspace.ui

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import dev.co508.ticketspace.ar.ArScreen
import dev.co508.ticketspace.ar.ArViewModel

@Composable
inline fun <reified T : ViewModel> screenModel(crossinline create: () -> T): T =
    viewModel(
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
        SignInScreen(screenModel { SignInViewModel(app) }, error)
    } else {
        val nav = rememberNavController()
        val back: () -> Unit = { nav.popBackStack() }
        val navigate: (String) -> Unit = { nav.navigate(it) }
        NavHost(navController = nav, startDestination = "home") {
            composable("home") { HomeScreen(screenModel { HomeViewModel(app) }, navigate) }
            composable("rooms") { RoomsScreen(screenModel { RoomsViewModel(app) }, back, navigate) }
            composable("room/{id}") { entry ->
                RoomDetailScreen(
                    screenModel { RoomDetailViewModel(app) },
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
                NewTodoScreen(screenModel { NewTodoViewModel(app) }, entry.arguments?.getString("room"), back)
            }
            composable("todo/{id}") { entry ->
                TodoDetailScreen(
                    screenModel { TodoDetailViewModel(app) },
                    entry.arguments?.getString("id").orEmpty(),
                    back,
                    navigate,
                )
            }
            composable("new-room") { NewRoomScreen(screenModel { NewRoomViewModel(app) }, back) }
            composable("settings") { SettingsScreen(screenModel { SettingsViewModel(app) }, back) }
            composable("ar/{room}") { ArScreen(screenModel { ArViewModel() }, back) }
        }
    }
}
