package hunoia.luno.quickaccess.query

import android.content.Context
import android.content.pm.PackageManager
import androidx.collection.LruCache
import hunoia.luno.core.AppContext
import hunoia.luno.core.domain.AppInfo
import hunoia.luno.bridge.system.PackageChangeReceiver

/** 单个已安装应用的可用状态：正常 / 被 enabled 位禁用 / 被系统 suspend（厂商冻结）。 */
enum class AppAvailability {
    ENABLED,
    DISABLED,
    SUSPENDED,
}

/** 冻结管理页的一行：应用信息 + 是否为 suspend（只读，Luno 不解）。 */
data class DisabledAppEntry(
    val app: AppInfo,
    val availability: AppAvailability,
)

/**
 * 禁用 / 冻结应用的事实源。
 *
 * 两种系统状态必须分开：`enabledSetting` 是 Luno（经 Shizuku）能改的禁用位；
 * `isPackageSuspended` 是厂商/系统冻结，改 enabled 位对它无效。历史上两者混在
 * 一个 `isDisabled` 里，导致「点一下就解冻再启动」对 suspend 应用静默失败。
 *
 * 缓存与包变更接收器只有这一份；写入方（[hunoia.luno.launch.PackageControl]）
 * 成功后必须调 [markEnabled] / [markDisabled] 或 [invalidate]。
 */
object DisabledAppQuery {

    private var disabledCache: List<DisabledAppEntry>? = null
    private var receiverRegistered = false
    private val disabledResultCache = LruCache<String, Boolean>(1024)
    private val suspendedResultCache = LruCache<String, Boolean>(1024)

    private fun ensureReceiver() {
        if (receiverRegistered) return
        receiverRegistered = true
        // 只作废缓存，不预热重扫：缓存已清空，下次真实查询自然会重建。
        // 预热会在每次包变更事件上白跑一遍 getInstalledApplications + 每包两次 Binder
        // （约 200~400 次），而且没有 in-flight 去重，可能和用户的真实查询并发扫两遍。
        PackageChangeReceiver.register(AppContext.get()) { invalidate() }
    }

    /** 包变更 / 写入成功后整表作废；下一次查询重建。 */
    fun invalidate() {
        disabledCache = null
        disabledResultCache.evictAll()
        suspendedResultCache.evictAll()
    }

    private fun enabledState(context: Context, packageName: String): Int? = runCatching {
        context.packageManager.getApplicationEnabledSetting(packageName)
    }.getOrNull()

    private fun isDisabledState(state: Int?): Boolean =
        state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED

    /** 只判 enabled 位：这是 Luno 能解冻的状态。suspend 见 [isSuspended]。 */
    fun isDisabled(context: Context, packageName: String): Boolean {
        ensureReceiver()
        disabledResultCache.get(packageName)?.let { return it }
        val result = isDisabledState(enabledState(context, packageName))
        disabledResultCache.put(packageName, result)
        return result
    }

    /** 厂商/系统冻结：Luno 不能通过 enablePackage 解除。 */
    fun isSuspended(context: Context, packageName: String): Boolean {
        ensureReceiver()
        suspendedResultCache.get(packageName)?.let { return it }
        val result = runCatching { context.packageManager.isPackageSuspended(packageName) }
            .getOrDefault(false)
        suspendedResultCache.put(packageName, result)
        return result
    }

    fun availability(context: Context, packageName: String): AppAvailability = when {
        isSuspended(context, packageName) -> AppAvailability.SUSPENDED
        isDisabled(context, packageName) -> AppAvailability.DISABLED
        else -> AppAvailability.ENABLED
    }

    /** 写入成功后的定向更新：不用等包广播，也不整表重扫。 */
    fun markEnabled(packageName: String) {
        disabledResultCache.put(packageName, false)
        disabledCache = disabledCache
            ?.mapNotNull { entry ->
                if (entry.app.packageName != packageName) return@mapNotNull entry
                // suspend 不因 enablePackage 改变；只去掉 DISABLED 行。
                if (entry.availability == AppAvailability.SUSPENDED) entry else null
            }
    }

    fun markDisabled(packageName: String, label: String = packageName) {
        disabledResultCache.put(packageName, true)
        suspendedResultCache.put(packageName, false)
        val entries = disabledCache
        if (entries != null && entries.none { it.app.packageName == packageName }) {
            disabledCache = entries + DisabledAppEntry(
                app = AppInfo(packageName = packageName, className = "", label = label),
                availability = AppAvailability.DISABLED,
            )
        }
    }

    fun queryDisabledEntries(context: Context): List<DisabledAppEntry> {
        disabledCache?.let { cache -> return cache }
        ensureReceiver()

        val pm = context.packageManager
        val allApps = try {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } catch (_: Exception) {
            return emptyList()
        }

        val result = mutableListOf<DisabledAppEntry>()
        val pkgNames = mutableSetOf<String>()
        for (app in allApps) {
            val pkgName = app.packageName
            if (pkgName.isBlank()) continue
            if (pkgName in pkgNames) continue

            val disabled = isDisabledState(
                runCatching { pm.getApplicationEnabledSetting(pkgName) }.getOrNull(),
            )
            val suspended = runCatching { pm.isPackageSuspended(pkgName) }.getOrDefault(false)
            if (!disabled && !suspended) continue
            pkgNames.add(pkgName)

            val label = try {
                app.loadLabel(pm).toString()
            } catch (_: Exception) {
                pkgName
            }
            disabledResultCache.put(pkgName, disabled)
            suspendedResultCache.put(pkgName, suspended)
            result.add(
                DisabledAppEntry(
                    app = AppInfo(packageName = pkgName, className = "", label = label),
                    availability = if (suspended) AppAvailability.SUSPENDED else AppAvailability.DISABLED,
                ),
            )
        }
        disabledCache = result
        return result
    }

    fun queryDisabledApplications(context: Context): List<AppInfo> =
        queryDisabledEntries(context).map { it.app }

    /** suspend 包集合：页面只读展示，且不进入任何解冻目标。 */
    fun querySuspendedPackages(context: Context): Set<String> =
        queryDisabledEntries(context)
            .filter { it.availability == AppAvailability.SUSPENDED }
            .mapTo(mutableSetOf()) { it.app.packageName }

    fun queryQuickAppLauncherApps(context: Context): QuickAppLauncherAppList =
        AppCatalog.query(context, includeDisabled = true)
}
