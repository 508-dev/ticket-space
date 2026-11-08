package dev.co508.ticketspace.ar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.lifecycle.Lifecycle
import com.google.ar.core.Config
import dev.co508.ticketspace.data.SpaceState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.rememberARView
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberOnGestureListener
import kotlin.math.sin

@Composable
fun ArScene(
    runtime: ArRuntime,
    state: SpaceState,
    cloud: Boolean,
    highlight: String?,
    lifecycle: Lifecycle,
) {
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val view =
        rememberARView(engine).apply {
            bloomOptions =
                bloomOptions.apply {
                    enabled = true
                    strength = 0.25f
                }
        }
    val materials =
        remember(materialLoader) {
            (-1..3).associateWith { priority ->
                val color = beaconColor(priority.takeIf { it >= 0 })
                // Unlit HDR output is emissive: bright values drive Filament's bloom regardless of room lighting.
                materialLoader.createUnlitColorInstance(
                    io.github.sceneview.math.Color(
                        color.red * 3f,
                        color.green * 3f,
                        color.blue * 3f,
                        1f,
                    ),
                )
            }
        }
    val white = remember(materialLoader) { materialLoader.createUnlitColorInstance(Color.White) }
    val halo = remember(materialLoader) { materialLoader.createUnlitColorInstance(Color.White.copy(alpha = 0.16f)) }
    val markers = beacons(state, runtime.locator.room, runtime.locator.worldRoom, runtime.showThings, highlight)
    val tick = runtime.tick
    ARSceneView(
        modifier =
            Modifier.fillMaxSize().onSizeChanged {
                runtime.width = it.width.toFloat()
                runtime.height =
                    it.height.toFloat()
            },
        engine = engine,
        materialLoader = materialLoader,
        view = view,
        lifecycle = lifecycle,
        cloudAnchorMode = if (cloud) Config.CloudAnchorMode.ENABLED else Config.CloudAnchorMode.DISABLED,
        planeRenderer = runtime.plan != null || runtime.placing,
        sessionConfiguration = { session, config ->
            config.cloudAnchorMode = if (cloud) Config.CloudAnchorMode.ENABLED else Config.CloudAnchorMode.DISABLED
            config.depthMode =
                if (session.isDepthModeSupported(
                        Config.DepthMode.AUTOMATIC,
                    )
                ) {
                    Config.DepthMode.AUTOMATIC
                } else {
                    Config.DepthMode.DISABLED
                }
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
        },
        onSessionCreated = runtime::attach,
        onSessionUpdated = { _, frame -> runtime.update(frame) },
        onSessionFailed = {
            runtime.error =
                "AR session failed: ${it.javaClass.simpleName}. Return to the list and retry."
        },
        onGestureListener =
            rememberOnGestureListener(
                onSingleTapConfirmed = { event, node ->
                    if (runtime.plan != null) {
                        runtime.tapSurface(event.x, event.y)
                    } else {
                        node?.name?.takeIf { id -> state.things.any { it.id == id } }?.let { runtime.selected = it }
                    }
                },
                onLongPress = { _, node ->
                    node?.name?.takeIf { id -> state.things.any { it.id == id } }?.let {
                        runtime.selected =
                            it
                    }
                },
            ),
    ) {
        markers.forEach { beacon ->
            key(beacon.thing.id) {
                val pulse =
                    if (beacon.priority ==
                        3
                    ) {
                        1f + 0.2f * sin(tick / 1_000_000_000.0 * Math.PI * 2).toFloat()
                    } else {
                        1f
                    }
                val position = Position(beacon.world.t[0], beacon.world.t[1], beacon.world.t[2])
                SphereNode(
                    radius = if (beacon.priority == null) 0.045f else 0.065f,
                    position = position,
                    scale = Scale(pulse),
                    materialInstance = materials[beacon.priority ?: -1],
                    apply = {
                        name =
                            beacon.thing.id
                    },
                )
                if (beacon.highlighted) {
                    SphereNode(radius = 0.11f, position = position, materialInstance = halo, apply = {
                        isHittable =
                            false
                    })
                }
            }
        }
        (runtime.hosted.map { it.anchor } + listOfNotNull(runtime.candidate)).forEachIndexed { index, anchor ->
            key("anchor-$index") {
                val pose = anchor.pose
                SphereNode(
                    radius = 0.045f,
                    position =
                        Position(
                            pose.tx(),
                            pose.ty(),
                            pose.tz(),
                        ),
                    materialInstance = white,
                    apply = {
                        isHittable =
                            false
                    },
                )
            }
        }
    }
}
