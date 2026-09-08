package hunoia.luno.freeze.api

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import hunoia.luno.core.AppContext
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.bridge.PackageChangeReceiver
import java.util.Collections
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object FreezeState {

    private var frozenCache: Map<String, AppInfo>? = null
    private var receiverRegistered = false
    private val frozenResultCache: MutableMap<String, Boolean> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Boolean>(1024, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean = size > 1024
        }
    )

    private fun ensureReceiver() {
        if (receiverRegistered) return
        receiverRegistered = true
        PackageChangeReceiver.register(AppContext.get()) {
            frozenCache = null
            frozenResultCache.clear()
            AppContext.applicationScope?.launch {
                withContext(Dispatchers.IO) {
                    queryFrozenApplications(AppContext.get())
                }
            }
        }
    }

    fun isCacheReady(): Boolean = frozenCache != null

    fun invalidateFrozenCache() {
        frozenCache = null
        frozenResultCache.clear()
    }

    fun isFrozen(context: Context, packageName: String): Boolean {
        frozenResultCache[packageName]?.let { return it }
        val enabledSetting = runCatching {
            context.packageManager.getApplicationEnabledSetting(packageName)
        }.getOrNull() ?: return false.also { frozenResultCache[packageName] = false }
        val result = enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
            enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
            enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
        frozenResultCache[packageName] = result
        return result
    }

    fun markFrozen(packageName: String) {
        frozenResultCache[packageName] = true
        frozenCache?.let { cache ->
            if (packageName !in cache) {
                frozenCache = cache + (packageName to AppInfo(packageName, "", packageName))
            }
        }
    }

    fun markUnfrozen(packageName: String) {
        frozenResultCache[packageName] = false
        frozenCache?.let { cache ->
            frozenCache = cache - packageName
        }
    }

    fun markBatchFrozen(packageNames: Collection<String>) {
        packageNames.forEach { frozenResultCache[it] = true }
        frozenCache?.let { cache ->
            val new = packageNames.filter { it !in cache }
            if (new.isNotEmpty()) {
                frozenCache = cache + new.associateWith { AppInfo(it, "", it) }
            }
        }
    }

    fun markBatchUnfrozen(packageNames: Collection<String>) {
        packageNames.forEach { frozenResultCache[it] = false }
        frozenCache?.let { cache ->
            frozenCache = cache - packageNames.toSet()
        }
    }

    fun queryFrozenStateByPackage(context: Context, packageNames: Collection<String>): Map<String, Boolean> {
        if (packageNames.isEmpty()) return emptyMap()
        val cached = frozenCache
        if (cached != null) {
            return packageNames.associateWith { it in cached }
        }
        return packageNames.associateWith { isFrozen(context, it) }
    }

    fun queryFrozenApplications(context: Context): List<AppInfo> {
        frozenCache?.let { cache -> return cache.values.toList() }
        ensureReceiver()

        val pm = context.packageManager
        val allApps = try {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } catch (e: Exception) {
            return emptyList()
        }

        val result = mutableListOf<AppInfo>()
        val pkgNames = mutableSetOf<String>()
        for (app in allApps) {
            val pkgName = app.packageName
            if (pkgName.isBlank()) continue
            if (pkgName in pkgNames) continue

            val enabledSetting = runCatching { pm.getApplicationEnabledSetting(pkgName) }.getOrNull()
            val isDisabled = enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED

            val isSuspended = runCatching { pm.isPackageSuspended(pkgName) }.getOrDefault(false)

            if (!isDisabled && !isSuspended) continue
            pkgNames.add(pkgName)

            val label = try {
                app.loadLabel(pm).toString()
            } catch (e: Exception) {
                pkgName
            }

            result.add(AppInfo(
                packageName = pkgName,
                className = "",
                label = label
            ))
        }
        frozenCache = result.associateBy { it.packageName }
        return result
    }

    suspend fun queryFrozenApplicationsOnIo(context: Context): List<AppInfo> {
        return queryFrozenApplications(context)
    }

    fun isSystemApp(ai: ApplicationInfo?): Boolean {
        if (ai == null) return false
        val flags = ai.flags
        return (flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }
}
