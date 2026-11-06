package hunoia.luno.quickaccess.query

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import hunoia.luno.core.domain.AppInfo
import hunoia.luno.domain.search.SearchCenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * 统一应用清单：主用户桌面应用 + 分身 + 禁用应用（+ 可选的无桌面入口系统包）。
 *
 * 所有消费方（快速启动器 / 抽屉 / 搜索 / 选择弹层 / 动作选择页 / 黑名单）都从这里取，
 * 避免各自调用 PackageManager 得出不同的集合。主用户与分身包名相同，靠 [AppInfo.userId]
 * 区分；匹配类配置仍只存包名（分身与主应用共享规则）。
 */
object AppCatalog {

    /** 无桌面入口的系统包开关；UI 改动后 [refreshSystemSetting] / [setShowSystemApps] 同步。 */
    @Volatile
    private var showSystemAppsCache: Boolean? = null

    fun query(
        context: Context,
        includeDisabled: Boolean = true,
        includeSystem: Boolean = showSystemApps(),
    ): QuickAppLauncherAppList {
        // 清单**包含 Luno 自己**：搜索 / 抽屉 / 选择弹层都要能搜到本应用，
        // 这里一旦排除，用户就永远搜不到 Luno。
        val mainApps = AppQuery.queryLauncherActivities(context, allowRepeatPackage = false)
        val normalPkgs = mainApps.mapTo(mutableSetOf()) { it.packageName }

        val clones = queryCloneApps(context)
        val disabled = if (includeDisabled) {
            DisabledAppQuery.queryDisabledApplications(context)
                .filter { it.packageName !in normalPkgs }
        } else {
            emptyList()
        }
        // 禁用包已经以「禁用应用」行出现，系统包集合里要排除，避免同一身份重复出行。
        val systems = if (includeSystem) {
            querySystemApps(context, normalPkgs + disabled.map { it.packageName })
        } else {
            emptyList()
        }

        val apps = groupClonesAfterPrimary(mainApps + clones) +
            disabled.filter { app -> (mainApps + clones).none { it.packageName == app.packageName && it.userId == 0 } } +
            systems
        return QuickAppLauncherAppList(
            apps = apps,
            disabledPkgs = disabled.mapTo(mutableSetOf()) { it.packageName },
        )
    }

    /** 分身条目：LauncherApps 能列出 XSpace/工作资料里的可启动 Activity。 */
    fun queryCloneApps(context: Context): List<AppInfo> {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            ?: return emptyList()
        val mainUser = Process.myUserHandle()
        val result = mutableListOf<AppInfo>()
        for (profile in runCatching { launcherApps.profiles }.getOrDefault(emptyList())) {
            if (profile == mainUser) continue
            // UserHandle.hashCode() 返回用户 id（AOSP 实现），用来把条目和启动目标对应起来。
            val userId = profile.hashCode()
            val activities = runCatching { launcherApps.getActivityList(null, profile) }
                .getOrDefault(emptyList())
            for (activity in activities) {
                result.add(
                    AppInfo(
                        packageName = activity.componentName.packageName,
                        className = activity.componentName.className,
                        label = runCatching { activity.label.toString() }
                            .getOrDefault(activity.componentName.packageName),
                        userId = userId,
                    ),
                )
            }
        }
        // 同一分身的多个 launcher Activity 只保留一条（应用清单按「应用」而不是「Activity」）。
        return result.distinctBy { it.packageName to it.userId }
    }

    /** 没有桌面入口的系统包：仅用于启动/查看详情，不参与桌面列表常规排序。 */
    fun querySystemApps(context: Context, excludePkgs: Set<String> = emptySet()): List<AppInfo> {
        val pm = context.packageManager
        val installed = runCatching {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        }.getOrDefault(emptyList())
        val result = mutableListOf<AppInfo>()
        for (app in installed) {
            val pkg = app.packageName
            if (pkg.isBlank() || pkg in excludePkgs) continue
            val isSystem = app.flags and (
                ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
                ) != 0
            if (!isSystem) continue
            // 有桌面入口的已经在主清单里；这里只补「无入口」的包。
            if (runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull() != null) continue
            val label = runCatching { app.loadLabel(pm).toString() }.getOrDefault(pkg)
            result.add(AppInfo(packageName = pkg, className = "", label = label, isSystem = true))
        }
        return result
    }

    /** 分身紧跟主应用（标题相同，只靠图标角标区分；乱序会让两行看起来重复）。 */
    fun groupClonesAfterPrimary(apps: List<AppInfo>): List<AppInfo> {
        if (apps.none { it.isClone }) return apps
        val byPkg = apps.filter { it.isClone }.groupBy { it.packageName }
        val emitted = HashSet<String>()
        val result = ArrayList<AppInfo>(apps.size)
        for (app in apps) {
            if (app.isClone) continue
            result.add(app)
            if (emitted.add(app.packageName)) {
                byPkg[app.packageName]?.let(result::addAll)
            }
        }
        // 主应用不在清单里（例如只克隆了某个应用）时仍要给出副本行。
        for (clone in apps.filter { it.isClone }) {
            if (clone.packageName !in emitted) {
                result.add(clone)
                emitted.add(clone.packageName)
            }
        }
        return result
    }

    fun showSystemApps(): Boolean {
        showSystemAppsCache?.let { return it }
        // 同步调用方都在后台线程（查询本身就要扫包）；首次读配置的 IO 远小于扫包成本。
        val value = runBlocking(Dispatchers.IO) {
            runCatching { SearchCenter.showSystemAppsEnabled() }.getOrDefault(false)
        }
        showSystemAppsCache = value
        return value
    }

    fun setShowSystemApps(value: Boolean) {
        showSystemAppsCache = value
    }

    suspend fun refreshSystemSetting() {
        showSystemAppsCache = withContext(Dispatchers.IO) {
            runCatching { SearchCenter.showSystemAppsEnabled() }.getOrDefault(false)
        }
    }
}
