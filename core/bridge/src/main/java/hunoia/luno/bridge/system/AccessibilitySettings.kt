package hunoia.luno.bridge.system

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager

/**
 * 一个已安装的无障碍服务（包管理器枚举结果，纯平台数据，不含启用状态）。
 * [id] 与系统 `ENABLED_ACCESSIBILITY_SERVICES` 里的 token 同形（`flattenToString`）。
 */
data class InstalledAccessibilityService(
    val component: ComponentName,
    val label: String,
    val description: String,
    val packageName: String,
) {
    val id: String get() = component.flattenToString()
}

object AccessibilitySettings {

    fun serviceId(context: Context, clazz: Class<out AccessibilityService>): String {
        return serviceComponentName(context, clazz).flattenToShortString()
    }

    private fun serviceComponentName(context: Context, clazz: Class<out AccessibilityService>): ComponentName {
        return ComponentName(context.packageName, clazz.name)
    }

    /** `ENABLED_ACCESSIBILITY_SERVICES` 的冒号分隔串里是否含指定组件。 */
    fun tokenContains(value: String, componentName: ComponentName): Boolean {
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(value)
        while (splitter.hasNext()) {
            if (ComponentName.unflattenFromString(splitter.next()) == componentName) return true
        }
        return false
    }

    private fun parseComponents(value: String): MutableSet<ComponentName> {
        val out = mutableSetOf<ComponentName>()
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(value)
        while (splitter.hasNext()) {
            ComponentName.unflattenFromString(splitter.next())?.let { out += it }
        }
        return out
    }

    /**
     * 当前**启用**的无障碍组件集合。全局总闸 `ACCESSIBILITY_ENABLED` 关闭时返回空集
     * （token 可能仍残留，但系统不会绑定服务，对用户而言即未启用）。
     */
    fun enabledComponents(context: Context): Set<ComponentName> {
        val accessibilityEnabled = runCatching {
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED) == 1
        }.getOrDefault(false)
        if (!accessibilityEnabled) return emptySet()

        val value = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        }.getOrNull() ?: return emptySet()
        return parseComponents(value)
    }

    fun isEnabled(context: Context, clazz: Class<out AccessibilityService>): Boolean {
        return serviceComponentName(context, clazz) in enabledComponents(context)
    }

    /**
     * 设备上已安装的全部无障碍服务（含未启用），供管理页枚举。
     * 需要 `QUERY_ALL_PACKAGES`（清单已声明）才能看到全部提供方。
     */
    fun installedServices(context: Context): List<InstalledAccessibilityService> {
        val pm = context.packageManager
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return emptyList()
        return runCatching { manager.installedAccessibilityServiceList }.getOrDefault(emptyList())
            .mapNotNull { info ->
                val resolveInfo = info.resolveInfo ?: return@mapNotNull null
                val serviceInfo = resolveInfo.serviceInfo ?: return@mapNotNull null
                InstalledAccessibilityService(
                    component = ComponentName(serviceInfo.packageName, serviceInfo.name),
                    label = resolveInfo.loadLabel(pm)?.toString()?.trim().orEmpty()
                        .ifBlank { serviceInfo.packageName },
                    description = runCatching { info.loadDescription(pm) }.getOrNull()
                        ?.toString()?.trim().orEmpty()
                        .ifBlank {
                            runCatching { info.loadSummary(pm) }.getOrNull()
                                ?.toString()?.trim().orEmpty()
                        },
                    packageName = serviceInfo.packageName,
                )
            }
    }

    /**
     * 原子读-改-写地启停一个无障碍组件：**保留其它组件**（这个串是全系统共享的，
     * 只写自己会把别人的服务顶掉），启用时补 `ACCESSIBILITY_ENABLED = 1`。
     *
     * 幂等：已处于目标状态时不做写入，返回 true。需要 `WRITE_SECURE_SETTINGS`。
     * 本写入会被 `DaemonService` 的 ContentObserver 看见（它自带 5s 节流与回声抑制）。
     */
    fun setEnabled(context: Context, component: ComponentName, enable: Boolean): Boolean {
        if (!hasWriteSecureSettings(context)) return false
        val resolver = context.contentResolver

        val current = runCatching {
            Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        }.getOrNull() ?: ""
        val components = parseComponents(current)
        val present = component in components

        if (enable) {
            if (!present) {
                components += component
                val next = components.joinToString(":") { it.flattenToString() }
                val ok = runCatching {
                    Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next)
                }.getOrDefault(false)
                if (!ok) return false
            }
            // token 就位后再开总闸：避免总闸先开、系统按空/旧列表绑定一轮。
            return runCatching {
                Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED) == 1
            }.getOrDefault(false) || runCatching {
                Settings.Secure.putString(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
            }.getOrDefault(false)
        }

        if (!present) return true
        components -= component
        val next = components.joinToString(":") { it.flattenToString() }
        return runCatching {
            Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next)
        }.getOrDefault(false)
    }

    fun hasWriteSecureSettings(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
