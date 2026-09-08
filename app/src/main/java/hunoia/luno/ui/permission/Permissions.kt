package hunoia.luno.ui.permission

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

data class PermissionState(
    val isGranted: Boolean,
    val deniedForever: Boolean,
    val launchPermissionRequest: () -> Unit
)

@Composable
fun rememberGetInstalledAppsPermissionState(
    onPermissionResult: (Boolean) -> Unit = {}
): PermissionState {
    val context = LocalContext.current
    var isGranted by remember {
        mutableStateOf(
            !context.supportsPermission() ||
            ContextCompat.checkSelfPermission(context, PERMISSION_GET_INSTALLED_APPS) ==
            PackageManager.PERMISSION_GRANTED
        )
    }
    var deniedForever by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        isGranted = granted
        if (!granted) {
            deniedForever = context is Activity &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    context, PERMISSION_GET_INSTALLED_APPS
                )
        }
        onPermissionResult(granted)
    }

    return PermissionState(
        isGranted = isGranted,
        deniedForever = deniedForever,
        launchPermissionRequest = { launcher.launch(PERMISSION_GET_INSTALLED_APPS) }
    )
}

private fun Context.supportsPermission(): Boolean {
    return try {
        packageManager.getPermissionInfo(PERMISSION_GET_INSTALLED_APPS, 0)
        true
    } catch (_: Exception) {
        false
    }
}

const val PERMISSION_GET_INSTALLED_APPS = "com.android.permission.GET_INSTALLED_APPS"
