package hunoia.luno.quickaccess

import android.content.Context
import coil3.request.ImageRequest
import hunoia.luno.core.domain.AppInfo
import hunoia.luno.bridge.launcher.LauncherInfo
import hunoia.luno.bridge.launcher.DynamicShortcuts
import hunoia.luno.bridge.system.PackageChangeReceiver
import hunoia.luno.quickaccess.query.AppCatalog
import hunoia.luno.quickaccess.query.AppListRepository
import hunoia.luno.quickaccess.query.AppQuery
import hunoia.luno.quickaccess.query.DisabledAppQuery
import hunoia.luno.quickaccess.query.SystemAppsVisibility
import hunoia.luno.bridge.launcher.ShortcutQuery
import hunoia.luno.bridge.launcher.toShortcutInfo
import hunoia.luno.quickaccess.query.LauncherIconQuery
import hunoia.luno.quickaccess.launch.Launcher
import hunoia.luno.quickaccess.launch.QuickAppLaunch
import hunoia.luno.config.model.MiniWindowSettings
import hunoia.luno.config.DynamicShortcutRefsStore
import hunoia.luno.quickaccess.query.ActivityOption
import hunoia.luno.quickaccess.query.LauncherAppOption
import hunoia.luno.quickaccess.query.LauncherEnvironment
import hunoia.luno.quickaccess.query.OpenAppOrUrlQuery
import kotlinx.coroutines.CoroutineScope
import hunoia.luno.core.LOG_TAG

object QuickLaunchFacade {

    /**
     * 应用清单加载（AppPickerSheet / 黑名单 / 动作选择页共用）：统一清单来源
     * （主用户 + 分身 + 禁用应用 + [visibility] 追加的两类系统包），查询失败回退空表。
     * 调用方须在 IO 线程；[visibility] 由各列表自己的内存开关决定（默认都不含）。
     */
    suspend fun queryAppsIncludingDisabled(
        context: Context,
        systemVisibility: SystemAppsVisibility = SystemAppsVisibility(),
    ): List<AppInfo> = try {
        AppCatalog.query(context, includeDisabled = true, systemVisibility = systemVisibility).apps
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * 应用列表权限刚变更：同步清掉本进程缓存，再广播让其它进程（`:app` / `:service`）重建
     * 清单与搜索索引。
     *
     * 没权限时扫出来的空表会被 [AppQuery] / [AppListRepository] 缓存住，只授权不刷新会一直空；
     * 广播复用包变更通道（`ACTION_LOCAL_PACKAGE_CHANGED`），跨进程也能落到。
     */
    fun invalidateAppListCaches(context: Context) {
        AppQuery.invalidateLauncherCache()
        DisabledAppQuery.invalidate()
        AppListRepository.refresh()
        PackageChangeReceiver.notifyLocalPackageChanged(context)
    }

    fun queryShortcutActivities(context: Context, allowRepeatPackage: Boolean = true): List<LauncherInfo> =
        AppQuery.queryCreateShortcutActivities(context, allowRepeatPackage)

    fun queryShortcuts(context: Context): List<LauncherInfo> =
        ShortcutQuery.getAllAppsWithShortcut(context)

    /**
     * 静态 XML 快捷方式 + 已导入的动态快捷方式，按包名合并成选择器/搜索使用的同一形态。
     *
     * 动态引用已 pin 在系统里，但不在应用的 `shortcuts.xml`，所以静态查询看不到它们。
     * 同一 `(packageName, shortcutId, userSerial)` 只保留一条（静态定义带 intent，优先）。
     *
     * 旧导入记录可能缺资料空间 `userId`（该字段加入前导入）：动态入口会失去归属、被
     * `buildAppShortcutEntries` 挂到所有同包行（主/分身互串）。这里用 `userSerial` 经
     * UserManager 反查补齐并写回，一次性迁移。
     */
    suspend fun queryShortcutsIncludingImported(context: Context): List<LauncherInfo> {
        val static = queryShortcuts(context)
        var imported = runCatching {
            DynamicShortcutRefsStore.imported()
        }.getOrElse { emptyList() }
        if (imported.isEmpty()) return static
        if (imported.any { it.userId < 0 }) {
            val repaired = imported.map { ref ->
                if (ref.userId < 0) {
                    val uid = DynamicShortcuts.userIdForSerial(context, ref.userSerial)
                    if (uid >= 0) ref.copy(userId = uid) else ref
                } else {
                    ref
                }
            }
            if (repaired.any { it.userId >= 0 }) {
                val byIdentity = repaired.associateBy { it.identity }
                runCatching {
                    DynamicShortcutRefsStore.update { refs ->
                        refs.copy(items = refs.items.map { byIdentity[it.identity] ?: it })
                    }
                }
            }
            imported = repaired
        }

        val merged = static.toMutableList()
        imported.groupBy { it.packageName }.forEach { (packageName, refs) ->
            val index = merged.indexOfFirst { it.packageName == packageName }
            val dynamicShortcuts = refs.map { it.toShortcutInfo() }
            if (index >= 0) {
                val existing = merged[index]
                val known = existing.shortcuts
                    .mapTo(hashSetOf()) { it.shortcutId to it.userSerial }
                merged[index] = existing.copy(
                    shortcuts = existing.shortcuts + dynamicShortcuts.filter {
                        (it.shortcutId to it.userSerial) !in known
                    },
                )
            } else {
                merged += LauncherInfo(
                    packageName = packageName,
                    // 空 className = 没有可启动 Activity，图标回退到应用图标、启动走 LauncherApps。
                    className = "",
                    label = refs.first().appLabel,
                    shortcuts = dynamicShortcuts,
                )
            }
        }
        return merged
    }

    fun resolveShortcutIconResourceId(context: Context, iconResource: android.content.Intent.ShortcutIconResource): Int =
        LauncherIconQuery.resolveShortcutIconResourceId(context, iconResource)

    /**
     * 面板图标的 Coil 请求。图标不自己缓存：`AppIconFetcher` 把原生 Drawable 缩到 [sizePx] 后交给
     * Coil，缓存键由 `AppIconKeyer` + 请求 size 共同决定。
     * ImageRequest 本身按 (包名, 边长) 复用：它是普通 class、不重写 equals，每次 new 一个都会让
     * AsyncImage 的 model 参数恒变（Compose 跳过失效）。
     */
    fun appIconRequest(
        context: Context,
        packageName: String,
        sizePx: Int,
        userId: Int = 0,
    ): ImageRequest = hunoia.luno.quickaccess.icon.AppIconRequests.request(
        context,
        packageName,
        sizePx,
        userId,
    )

    fun queryLauncherAppOptions(context: Context): List<LauncherAppOption> =
        OpenAppOrUrlQuery.queryLauncherApps(context)

    fun queryActivityOptions(
        context: Context,
        packageName: String,
        selectedActivityClassName: String,
        launcherClassName: String?
    ): List<ActivityOption> =
        OpenAppOrUrlQuery.queryActivityOptions(context, packageName, selectedActivityClassName, launcherClassName)

    fun formatActivityOptionText(option: ActivityOption, packageName: String): String =
        OpenAppOrUrlQuery.formatActivityOptionText(option, packageName)

    fun launchApp(
        context: Context,
        coroutineScope: CoroutineScope,
        app: AppInfo,
        isDisabled: Boolean = false,
        miniWindow: Boolean = false,
        requestEnableDisabledPackage: ((String, (Boolean) -> Unit) -> Unit)? = null,
        log: ((String) -> Unit)? = null,
        onLaunch: ((AppInfo, Boolean) -> Boolean)? = null,
        onLaunched: (() -> Unit)? = null,
    ) {
        QuickAppLaunch.launch(
            context = context,
            coroutineScope = coroutineScope,
            app = app,
            isDisabled = isDisabled,
            miniWindow = miniWindow,
            requestEnableDisabledPackage = requestEnableDisabledPackage ?: { _, callback -> callback(true) },
            log = log ?: { message -> hunoia.luno.core.LunoLog.i(LOG_TAG, message); Unit },
            onLaunch = onLaunch ?: { _, _ -> true },
            onLaunched = onLaunched ?: {}
        )
    }

    fun invalidateLauncherCache() {
        AppQuery.invalidateLauncherCache()
    }

    suspend fun launchAppInPopup(
        context: Context,
        packageName: String,
        className: String,
        miniWindowSettings: MiniWindowSettings = MiniWindowSettings(),
        intentAction: String = "",
        data: String = "",
        extras: Map<String, String> = emptyMap(),
    ) {
        Launcher.launchAppInPopup(
            context = context,
            packageName = packageName,
            className = className,
            miniWindowSettings = miniWindowSettings,
            intentAction = intentAction,
            data = data,
            extras = extras,
        )
    }

    fun isLauncherPackage(context: Context, packageName: String): Boolean =
        LauncherEnvironment.isLauncherPackage(context, packageName)
}
