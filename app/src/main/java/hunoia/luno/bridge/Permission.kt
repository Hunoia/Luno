package hunoia.luno.bridge

import android.accessibilityservice.AccessibilityService
import android.content.Context
import hunoia.luno.bridge.accessibility.AccessibilitySettings

@Suppress("UNCHECKED_CAST")
fun Context.isAccessibilitySettingsOn(clazz: Class<*>): Boolean {
    return AccessibilitySettings.isEnabled(this, clazz as Class<out AccessibilityService>)
}

fun Context.hasWriteSecureSettingsPermission(): Boolean {
    return AccessibilitySettings.hasWriteSecureSettings(this)
}
