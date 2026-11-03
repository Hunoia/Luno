package hunoia.luno.quicklaunch

import android.content.Context
import androidx.collection.LruCache
import coil.request.ImageRequest
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.bridge.launcher.LauncherInfo
import hunoia.luno.quicklaunch.query.AppCatalog
import hunoia.luno.quicklaunch.query.AppQuery
import hunoia.luno.bridge.launcher.ShortcutQuery
import hunoia.luno.quicklaunch.query.LauncherIconQuery
import hunoia.luno.quicklaunch.launch.Launcher
import hunoia.luno.quicklaunch.launch.QuickAppLaunch
import hunoia.luno.config.model.MiniWindowSettings
import hunoia.luno.quicklaunch.icon.AppIconModel
import hunoia.luno.quicklaunch.query.ActivityOption
import hunoia.luno.quicklaunch.query.LauncherAppOption
import hunoia.luno.quicklaunch.query.LauncherEnvironment
import hunoia.luno.quicklaunch.query.OpenAppOrUrlQuery
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

    fun queryShortcutActivities(context: Context, allowRepeatPackage: Boolean = true): List<LauncherInfo> =
        AppQuery.queryCreateShortcutActivities(context, allowRepeatPackage)

    fun queryShortcuts(context: Context): List<LauncherInfo> =
        ShortcutQuery.getAllAppsWithShortcut(context)

    /** 按包名 + shortcutId 读取当前安装包里的静态快捷方式定义（用于刷新旧落库数据）。 */
    fun findShortcut(context: Context, packageName: String, shortcutId: String?): LauncherInfo.ShortcutInfo? =
        shortcutId?.let { ShortcutQuery.findShortcut(context, packageName, it) }

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
    ): ImageRequest {
        val key = if (userId == 0) "$packageName@$sizePx" else "$packageName@$sizePx@$userId"
        iconRequestCache.get(key)?.let { return it }
        val req = ImageRequest.Builder(context)
            .data(AppIconModel(packageName, sizePx, userId))
            .size(sizePx, sizePx)
            // 搜索行禁用全局 150ms crossfade：结果列表每次挂载十几行，
            // 每行淡入 stagger 起来就是闪（提示行/空态行全是同步矢量所以不闪）。
            // 图标靠搜索 flow 发射前 resolve 进 SearchIconCache，到时直接呈现，
            // 无需淡入掩盖解码。
            .crossfade(false)
            .build()
        iconRequestCache.put(key, req)
        return req
    }

    /** 请求复用上限：一屏结果十几行，128 ≈ 8 屏的余量。 */
    private const val ICON_REQUEST_CAP = 128
    private val iconRequestCache = LruCache<String, ImageRequest>(ICON_REQUEST_CAP)

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

    fun launchAppInPopup(
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

    suspend fun launchShortcutInfo(context: Context, shortcutInfo: LauncherInfo.ShortcutInfo): Boolean =
        Launcher.launchShortcutWithFallback(
            context = context,
            packageName = shortcutInfo.packageName,
            shortcutId = shortcutInfo.shortcutId,
            intentUris = shortcutInfo.intents,
            label = shortcutInfo.label,
        )

    fun isLauncherPackage(context: Context, packageName: String): Boolean =
        LauncherEnvironment.isLauncherPackage(context, packageName)
}
