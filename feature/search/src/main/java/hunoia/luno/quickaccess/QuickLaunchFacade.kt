package hunoia.luno.quickaccess

import android.content.Context
import coil3.request.ImageRequest
import hunoia.luno.core.domain.AppInfo
import hunoia.luno.bridge.launcher.LauncherInfo
import hunoia.luno.bridge.system.PackageChangeReceiver
import hunoia.luno.quickaccess.query.AppCatalog
import hunoia.luno.quickaccess.query.AppListRepository
import hunoia.luno.quickaccess.query.AppQuery
import hunoia.luno.quickaccess.query.DisabledAppQuery
import hunoia.luno.bridge.launcher.ShortcutQuery
import hunoia.luno.quickaccess.query.LauncherIconQuery
import hunoia.luno.quickaccess.launch.Launcher
import hunoia.luno.quickaccess.launch.QuickAppLaunch
import hunoia.luno.config.model.MiniWindowSettings
import hunoia.luno.quickaccess.query.ActivityOption
import hunoia.luno.quickaccess.query.LauncherAppOption
import hunoia.luno.quickaccess.query.LauncherEnvironment
import hunoia.luno.quickaccess.query.OpenAppOrUrlQuery
import kotlinx.coroutines.CoroutineScope
import hunoia.luno.core.LOG_TAG

object QuickLaunchFacade {

    /**
     * 应用清单加载（AppPickerSheet / 黑名单 / 动作选择页共用）：统一清单来源
     * （主用户 + 分身 + 禁用应用 + 按开关追加的系统包），查询失败回退空表。
     * 调用方须在 IO 线程。
     */
    suspend fun queryAppsIncludingDisabled(context: Context): List<AppInfo> = try {
        AppCatalog.refreshSystemSetting()
        AppCatalog.query(context, includeDisabled = true).apps
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
