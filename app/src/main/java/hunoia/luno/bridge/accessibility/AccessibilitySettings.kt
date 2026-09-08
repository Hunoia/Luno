package hunoia.luno.bridge.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils

object AccessibilitySettings {

    fun serviceId(context: Context, clazz: Class<out AccessibilityService>): String {
        return serviceComponentName(context, clazz).flattenToShortString()
    }

    private fun serviceComponentName(context: Context, clazz: Class<out AccessibilityService>): ComponentName {
        return ComponentName(context.packageName, clazz.name)
    }

    fun isEnabled(context: Context, clazz: Class<out AccessibilityService>): Boolean {
        val accessibilityEnabled = runCatching {
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED) == 1
        }.getOrDefault(false)
        if (!accessibilityEnabled) return false

        val settingValue = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(settingValue)
        val componentName = serviceComponentName(context, clazz)
        while (splitter.hasNext()) {
            val enabledComponent = ComponentName.unflattenFromString(splitter.next())
            if (enabledComponent == componentName) return true
        }
        return false
    }

    fun enable(context: Context, clazz: Class<out AccessibilityService>): Boolean {
        if (!hasWriteSecureSettings(context)) return false

        val accessibilityEnabled = runCatching {
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED) == 1
        }.getOrDefault(false)

        if (!accessibilityEnabled) {
            Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                "1"
            )
        }

        val currentValue = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""

        val componentName = serviceComponentName(context, clazz)
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(currentValue)
        while (splitter.hasNext()) {
            val enabledComponent = ComponentName.unflattenFromString(splitter.next())
            if (enabledComponent == componentName) return false
        }

        val id = componentName.flattenToShortString()
        val newValue = if (currentValue.isEmpty()) id else "$id:$currentValue"
        return Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            newValue
        )
    }

    fun disable(context: Context, clazz: Class<out AccessibilityService>): Boolean {
        if (!hasWriteSecureSettings(context)) return false

        val currentValue = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return true

        val componentName = serviceComponentName(context, clazz)
        val parts = TextUtils.SimpleStringSplitter(':').apply { setString(currentValue) }
        val remaining = mutableListOf<String>()
        while (parts.hasNext()) {
            val part = parts.next()
            if (ComponentName.unflattenFromString(part) != componentName) {
                remaining.add(part)
            }
        }
        val newValue = remaining.joinToString(":")
        return Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            newValue
        )
    }

    fun hasWriteSecureSettings(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
