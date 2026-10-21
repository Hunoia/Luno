package hunoia.luno.quicklaunch.query

import android.content.Context
import android.content.Intent
import android.content.Intent.ShortcutIconResource
import android.content.pm.PackageManager
import android.os.SystemClock
import hunoia.luno.bridge.system.PackageChangeReceiver
import hunoia.luno.bridge.system.queryIntentActivitiesCompat
import hunoia.luno.quicklaunch.model.AppInfo

data class QuickAppLauncherAppList(
    val apps: List<AppInfo>,
    val disabledPkgs: Set<String>
)

object LauncherIconQuery {

    fun resolveShortcutIconResourceId(context: Context, iconResource: ShortcutIconResource): Int {
        return runCatching {
            val resources = context.packageManager.getResourcesForApplication(iconResource.packageName)
            @Suppress("DiscouragedApi")
            resources.getIdentifier(iconResource.resourceName, null, null)
        }.getOrDefault(0)
    }
}

object LauncherEnvironment {
    /**
     * 桌面集合的最长复用时间。包广播能覆盖装/卸桌面，但「切换默认桌面」在部分 ROM 上不发包广播，
     * 用 TTL 兜住；60s 重新查一次（一次 HOME 查询 + 几个包的 launch intent）代价可忽略，
     * 而错误答案的窗口有上限。
     */
    private const val HOME_SET_TTL_MS = 60_000L

    /** 单包结论的缓存上限：桌面候选通常 2~5 个，32 足够覆盖「刚退出某个应用」的来回。 */
    private const val PKG_CACHE_CAP = 32

    private val lock = Any()

    private val packageCache: MutableMap<String, Boolean> =
        object : LinkedHashMap<String, Boolean>(8, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, Boolean>?,
            ): Boolean = size > PKG_CACHE_CAP
        }

    private var homePackages: Set<String>? = null
    private var homePackagesAtMs = 0L

    @Volatile
    private var observingPackageChanges = false

    fun isLauncherPackage(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        synchronized(lock) { packageCache[packageName] }?.let { return it }
        val inLauncher = packageName in homePackageSet(context)
        synchronized(lock) { packageCache[packageName] = inLauncher }
        return inLauncher
    }

    /** 包变化即整表作废：桌面被装/卸/覆盖后，旧答案不能再复用。 */
    private fun invalidate() {
        synchronized(lock) {
            packageCache.clear()
            homePackages = null
        }
    }

    /**
     * 只注册一次。这个监听伴随进程存活、不反注册：LauncherEnvironment 是 object，
     * 生命周期等同进程，注册多个反而会在每次恢复时重复触发 invalidate。
     */
    private fun ensurePackageObserver(context: Context) {
        if (observingPackageChanges) return
        observingPackageChanges = true
        val appContext = context.applicationContext ?: context
        // 注册失败不致命：还有 TTL 兜底，只是失效得更慢。
        runCatching { PackageChangeReceiver.register(appContext) { invalidate() } }
    }

    private fun homePackageSet(context: Context): Set<String> {
        ensurePackageObserver(context)
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            homePackages?.let { cached ->
                if (now - homePackagesAtMs < HOME_SET_TTL_MS) return cached
            }
        }
        val computed = queryHomePackages(context)
        synchronized(lock) {
            homePackages = computed
            homePackagesAtMs = now
            // 集合本身可能变了（默认桌面切换、桌面被禁用），旧的单包结论一并作废。
            packageCache.clear()
        }
        return computed
    }

    /**
     * 桌面候选 = 响应 HOME、且自身没有启动入口的包。先按包名去重再查 launch intent：
     * 一次 HOME 查询里同名包可能有多条 activity，逐条查是重复 Binder。
     */
    private fun queryHomePackages(context: Context): Set<String> {
        val pm = context.packageManager
        val launcherIntent = Intent().apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolved = runCatching {
            pm.queryIntentActivitiesCompat(launcherIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrNull() ?: return emptySet()
        val out = HashSet<String>(resolved.size)
        val seen = HashSet<String>(resolved.size)
        for (info in resolved) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (!seen.add(pkg)) continue
            val hasOwnLaunchEntry = runCatching {
                pm.getLaunchIntentForPackage(pkg) != null
            }.getOrDefault(true)   // 查询失败按「有入口」处理：宁可判否，也不要误判成桌面
            if (!hasOwnLaunchEntry) out.add(pkg)
        }
        return out
    }
}
