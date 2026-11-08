package dev.co508.ticketspace.ar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Keep the ARCore world alive across list navigation, but release the camera while AR is hidden. */
@Composable
fun rememberArLifecycle(active: Boolean): Lifecycle {
    val parent = LocalLifecycleOwner.current.lifecycle
    val owner =
        remember {
            object : LifecycleOwner {
                val registry = LifecycleRegistry(this)
                override val lifecycle: Lifecycle get() = registry
            }
        }
    DisposableEffect(parent, active) {
        fun update() {
            owner.registry.currentState = if (active) parent.currentState else Lifecycle.State.CREATED
        }
        val observer = LifecycleEventObserver { _, _ -> update() }
        parent.addObserver(observer)
        update()
        onDispose { parent.removeObserver(observer) }
    }
    DisposableEffect(owner) { onDispose { owner.registry.currentState = Lifecycle.State.DESTROYED } }
    return owner.lifecycle
}
