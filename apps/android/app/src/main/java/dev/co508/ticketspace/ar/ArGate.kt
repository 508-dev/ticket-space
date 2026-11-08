package dev.co508.ticketspace.ar

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.ar.core.ArCoreApk
import dev.co508.ticketspace.AppContainer
import dev.co508.ticketspace.ui.Page
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ArGate(
    app: AppContainer,
    cloud: Boolean,
    active: Boolean,
    back: () -> Unit,
    content: @Composable (ready: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.activity() }
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val preferences by app.arSettings.data.collectAsStateWithLifecycle(initialValue = null)
    var permission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var availability by remember { mutableStateOf(ArCoreApk.Availability.UNKNOWN_CHECKING) }
    var installed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var checkVersion by remember { mutableStateOf(0) }
    val permissionRequest =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            permission =
                it
        }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        checkVersion++
    }
    LaunchedEffect(checkVersion) {
        do {
            availability = ArCoreApk.getInstance().checkAvailability(context)
            if (availability.isTransient) delay(300)
        } while (availability.isTransient)
        installed = availability == ArCoreApk.Availability.SUPPORTED_INSTALLED
    }
    val ready =
        preferences != null && (!cloud || preferences?.cloudDisclosureAccepted == true) && permission && installed
    content(ready)
    if (!active) return
    when {
        preferences == null -> Page("Preparing AR", back) { Text("Loading settings…") }
        cloud && preferences?.cloudDisclosureAccepted != true ->
            Page("Google Cloud Anchors", back) {
                Text("To power this session, Google will process sensor data (e.g., camera and location).")
                TextButton(onClick = { uri.openUri("https://policies.google.com/privacy") }) { Text("Learn more") }
                Button(onClick = { scope.launch { app.arSettings.acceptDisclosure() } }) { Text("Continue") }
            }
        !permission ->
            Page("Camera permission", back) {
                Text("AR uses your camera to find surfaces and place Things. Your lists work without AR.")
                Button(onClick = { permissionRequest.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                TextButton(onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                }) { Text("App permission settings") }
            }
        !installed ->
            Page("Google Play Services for AR", back) {
                Text(
                    when {
                        availability.isTransient -> "Checking AR support…"
                        availability.isSupported -> "Install or update Google Play Services for AR to continue."
                        else -> "AR is unavailable on this device. You can still use rooms and todos."
                    },
                )
                error?.let { Text(it) }
                Button(onClick = {
                    try {
                        if (availability.isSupported && activity != null) {
                            installed =
                                ArCoreApk.getInstance().requestInstall(activity, true) ==
                                ArCoreApk.InstallStatus.INSTALLED
                        } else {
                            checkVersion++
                        }
                    } catch (e: Exception) {
                        error = "AR installation could not finish. Retry or return to your lists."
                    }
                }) { Text(if (availability.isSupported) "Install / update AR" else "Retry") }
            }
        else -> Unit
    }
}

private fun Context.activity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.activity()
        else -> null
    }
