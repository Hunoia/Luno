package hunoia.luno.quicklaunch.query

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import hunoia.luno.core.AppContext
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.bridge.PackageChangeReceiver
import java.util.Collections
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object DisabledAppQuery {

    private var disabledCache: Map<String, AppInfo>? = null
    private var receiverRegistered = false
    private val disabledResultCache: MutableMap<String, Boolean> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Boolean>(1024, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean = size > 1024
        }
    )

    private fun ensureReceiver() {
        if (receiverRegistered) return
        receiverRegistered = true
        PackageChangeReceiver.register(AppContext.get()) {
            disabledCache = null
            disabledResultCache.clear()
            AppContext.applicationScope?.launch {
                withContext(Dispatchers.IO) {
                    queryDisabledApplications(AppContext.get())
                }
            }
        }
    }

    fun isCacheReady(): Boolean = disabledCache != null

    fun invalidateCache() {
        disabledCache = null
        disabledResultCache.clear()
    }

    fun isDisabled(context: Context, packageName: String): Boolean {
        disabledResultCache[packageName]?.let { return it }
        val enabledSetting = runCatching {
            context.packageManager.getApplicationEnabledSetting(packageName)
        }.getOrNull() ?: return false.also { disabledResultCache[packageName] = false }
        val result = enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
            enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
            enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
        disabledResultCache[packageName] = result
        return result
    }

    fun markEnabled(packageName: String) {
        disabledResultCache[packageName] = false
        disabledCache?.let { cache ->
            disabledCache = cache - packageName
        }
    }

    fun queryDisabledApplications(context: Context): List<AppInfo> {
        disabledCache?.let { cache -> return cache.values.toList() }
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
        disabledCache = result.associateBy { it.packageName }
        return result
    }

    suspend fun queryDisabledApplicationsOnIo(context: Context): List<AppInfo> =
        withContext(Dispatchers.IO) { queryDisabledApplications(context) }

    fun queryQuickAppLauncherApps(context: Context): QuickAppLauncherAppList {
        val disabledApps = queryDisabledApplications(context)
        return QuickLaunchFacade.queryCombinedQuickAppList(context, disabledApps)
    }

    fun isSystemApp(ai: ApplicationInfo?): Boolean {
        if (ai == null) return false
        val flags = ai.flags
        return (flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }
}
