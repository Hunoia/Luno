package hunoia.luno.bridge.system

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import hunoia.luno.bridge.system.AccessibilitySettings

@Suppress("UNCHECKED_CAST")
fun Context.isAccessibilitySettingsOn(clazz: Class<*>): Boolean {
    return AccessibilitySettings.isEnabled(this, clazz as Class<out AccessibilityService>)
}

fun Context.hasWriteSecureSettingsPermission(): Boolean {
    return AccessibilitySettings.hasWriteSecureSettings(this)
}

/**
 * 「显示在其他应用上层」是否已授权。未授权时 `WindowManager.addView` 对
 * `TYPE_APPLICATION_OVERLAY` 会抛 SecurityException（按 UID 校验）；不像无障碍/
 * WRITE_SECURE_SETTINGS 能靠 `DaemonService` 自恢复，必须用户手动授权。
 */
@SuppressLint("InlinedApi")
fun Context.canDrawOverlays(): Boolean = Settings.canDrawOverlays(this)

/** 跳到本应用的「显示在其他应用上层」设置页。 */
fun Context.gotoOverlaySettings() {
    runCatching {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        // 个别 ROM 不认带 package 的 uri，退到全量列表页
        runCatching {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
