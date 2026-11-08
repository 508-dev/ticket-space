package dev.co508.ticketspace.ar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.co508.ticketspace.AppContainer
import dev.co508.ticketspace.ui.NewTodoScreen
import dev.co508.ticketspace.ui.NewTodoViewModel
import dev.co508.ticketspace.ui.screenModel

@Composable
fun ArDraftForm(
    app: AppContainer,
    draft: ArTodoDraft,
    close: () -> Unit,
) {
    val owner =
        remember(draft.key) {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        val form = screenModel { NewTodoViewModel(app) }
        LaunchedEffect(draft.key) {
            form.room = draft.room
            form.thing = draft.thing
            draft.photo?.let { form.photos.add(it) }
        }
        NewTodoScreen(form, draft.room, close)
    }
}
