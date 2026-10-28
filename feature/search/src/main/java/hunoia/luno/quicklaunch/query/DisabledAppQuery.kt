package hunoia.luno.quicklaunch.query

import android.content.Context
import android.content.pm.PackageManager
import androidx.collection.LruCache
import hunoia.luno.core.AppContext
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.bridge.system.PackageChangeReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DisabledAppQuery {

    private var disabledCache: Map<String, AppInfo>? = null
    private var receiverRegistered = false
    private val disabledResultCache = LruCache<String, Boolean>(1024)

    private fun ensureReceiver() {
        if (receiverRegistered) return
        receiverRegistered = true
        // 只作废缓存，不预热重扫：缓存已清空，下次真实查询自然会重建。
        // 预热会在每次包变更事件上白跑一遍 getInstalledApplications + 每包两次 Binder
        // （约 200~400 次），而且没有 in-flight 去重，可能和用户的真实查询并发扫两遍。
        PackageChangeReceiver.register(AppContext.get()) {
            disabledCache = null
            disabledResultCache.evictAll()
        }
    }

    fun isDisabled(context: Context, packageName: String): Boolean {
        ensureReceiver()
        disabledResultCache.get(packageName)?.let { return it }
        val pm = context.packageManager
        val enabledSetting = runCatching {
            pm.getApplicationEnabledSetting(packageName)
        }.getOrNull()
        if (enabledSetting == null) {
            disabledResultCache.put(packageName, false)
            return false
        }
        // 与 queryDisabledApplications 保持同一口径：MIUI 等 ROM 的“冻结”可能表现为
        // disabled，也可能是 suspended；只看 enabledSetting 会漏掉后者，导致
        // 除快速启动器外的启动链路认为应用可启动而跳过解冻。
        val suspended = runCatching { pm.isPackageSuspended(packageName) }.getOrDefault(false)
        val result = suspended ||
            enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
            enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
            enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
        disabledResultCache.put(packageName, result)
        return result
    }

    fun markEnabled(packageName: String) {
        disabledResultCache.put(packageName, false)
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
        } catch (_: Exception) {
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
            } catch (_: Exception) {
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

}
