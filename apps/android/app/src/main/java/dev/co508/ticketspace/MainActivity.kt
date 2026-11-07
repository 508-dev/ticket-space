package dev.co508.ticketspace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.co508.ticketspace.ui.TicketSpace
import dev.co508.ticketspace.ui.TicketSpaceTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as TicketSpaceApplication).container
        if (savedInstanceState == null || !container.auth.ready.value) {
            lifecycleScope.launch { container.auth.restore() }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { container.runForeground() }
        }
        setContent { TicketSpaceTheme { TicketSpace(container) } }
    }
}
