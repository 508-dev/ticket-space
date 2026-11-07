package dev.co508.ticketspace.ar

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import dev.co508.ticketspace.ui.Page

class ArViewModel : ViewModel() {
    val message = "AR coming soon"
}

@Composable
fun ArScreen(
    vm: ArViewModel,
    back: () -> Unit,
) {
    Page("AR", back) { Text(vm.message) }
}
