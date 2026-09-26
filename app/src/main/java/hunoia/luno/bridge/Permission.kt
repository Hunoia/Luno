package hunoia.luno.bridge

import android.accessibilityservice.AccessibilityService
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import hunoia.luno.bridge.accessibility.AccessibilitySettings

@Suppress("UNCHECKED_CAST")
fun Context.isAccessibilitySettingsOn(clazz: Class<*>): Boolean {
    return AccessibilitySettings.isEnabled(this, clazz as Class<out AccessibilityService>)
}

fun Context.hasWriteSecureSettingsPermission(): Boolean {
    return AccessibilitySettings.hasWriteSecureSettings(this)
}
