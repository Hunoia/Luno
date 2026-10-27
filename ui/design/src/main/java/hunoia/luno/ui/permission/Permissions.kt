package hunoia.luno.ui.permission

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

data class PermissionState(
    val isGranted: Boolean,
    val deniedForever: Boolean,
    val launchPermissionRequest: () -> Unit
)

/**
 * 通用单权限状态：结果回调 + onResume 重查（从系统设置页授权回来时刷新）。
 * P2 联系人、P3 音乐/文件共用。
 */
@Composable
fun rememberPermissionState(
    permission: String,
    onPermissionResult: (Boolean) -> Unit = {}
): PermissionState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var deniedForever by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        tick++
        if (!granted) {
            deniedForever = context is Activity &&
                !ActivityCompat.shouldShowRequestPermissionRationale(context, permission)
        }
        onPermissionResult(granted)
    }

    val isGranted = remember(tick) {
        !context.supportsPermission() ||
            ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }
    return PermissionState(
        isGranted = isGranted,
        deniedForever = deniedForever && !isGranted,
        launchPermissionRequest = { launcher.launch(permission) }
    )
}

@Composable
fun rememberGetInstalledAppsPermissionState(
    onPermissionResult: (Boolean) -> Unit = {}
): PermissionState = rememberPermissionState(PERMISSION_GET_INSTALLED_APPS, onPermissionResult)

private fun Context.supportsPermission(): Boolean {
    return try {
        packageManager.getPermissionInfo(PERMISSION_GET_INSTALLED_APPS, 0)
        true
    } catch (_: Exception) {
        false
    }
}

const val PERMISSION_GET_INSTALLED_APPS = "com.android.permission.GET_INSTALLED_APPS"
