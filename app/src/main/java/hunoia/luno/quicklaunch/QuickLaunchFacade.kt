package hunoia.luno.quicklaunch

import android.content.Context
import android.graphics.drawable.Drawable
import hunoia.luno.config.model.OpenAppOrUrlData
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.LauncherInfo
import hunoia.luno.quicklaunch.query.AppQuery
import hunoia.luno.quicklaunch.query.ShortcutQuery
import hunoia.luno.quicklaunch.query.LauncherIconQuery
import hunoia.luno.quicklaunch.launch.Launcher
import hunoia.luno.quicklaunch.launch.QuickAppLaunch
import hunoia.luno.quicklaunch.icon.IconResizeCache
import hunoia.luno.quicklaunch.query.ActivityOption
import hunoia.luno.quicklaunch.query.LauncherAppOption
import hunoia.luno.quicklaunch.query.LauncherEnvironment
import hunoia.luno.quicklaunch.query.OpenAppOrUrlQuery
import hunoia.luno.quicklaunch.query.QuickAppLauncherAppList
import kotlinx.coroutines.CoroutineScope

object QuickLaunchFacade {

    var showOverlay: () -> Unit = {}

    fun queryApps(context: Context, allowRepeatPackage: Boolean = true): List<AppInfo> =
        AppQuery.queryLauncherActivities(context, allowRepeatPackage)

    fun queryShortcutActivities(context: Context, allowRepeatPackage: Boolean = true): List<LauncherInfo> =
        AppQuery.queryCreateShortcutActivities(context, allowRepeatPackage)

    suspend fun findApp(context: Context, packageName: String): AppInfo? =
        AppQuery.findLauncherActivity(context, packageName)

    fun queryShortcuts(context: Context): List<LauncherInfo> =
        ShortcutQuery.getAllAppsWithShortcut(context)

    fun loadIcon(context: Context, packageName: String): Drawable? =
        LauncherIconQuery.loadApplicationIcon(context, packageName)

    fun resolveShortcutIconResourceId(context: Context, iconResource: android.content.Intent.ShortcutIconResource): Int =
        LauncherIconQuery.resolveShortcutIconResourceId(context, iconResource)

    fun getCachedIcon(packageName: String): Drawable? =
        IconResizeCache.iconCache[packageName]

    fun cacheIcon(packageName: String, icon: Drawable) {
        IconResizeCache.iconCache[packageName] = icon
    }

    fun clearIconCache() {
        IconResizeCache.iconCache.clear()
    }

    fun getIconCacheSnapshot(): Map<String, Drawable> =
        IconResizeCache.iconCache.toMap()

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
        isFrozen: Boolean = false,
        miniWindow: Boolean = false,
        debugPrefix: String? = null,
        requestEnableFrozenPackage: ((String, (Boolean) -> Unit) -> Unit)? = null,
        log: ((String) -> Unit)? = null,
        onLaunch: ((AppInfo, Boolean) -> Boolean)? = null,
        onLaunched: (() -> Unit)? = null,
    ) {
        QuickAppLaunch.launch(
            context = context,
            coroutineScope = coroutineScope,
            app = app,
            isFrozen = isFrozen,
            miniWindow = miniWindow,
            debugPrefix = debugPrefix,
            requestEnableFrozenPackage = requestEnableFrozenPackage ?: { _, callback -> callback(true) },
            log = log ?: { message -> android.util.Log.d("LunoLauncher", message); Unit },
            onLaunch = onLaunch ?: { _, _ -> true },
            onLaunched = onLaunched ?: {}
        )
    }

    fun invalidateLauncherCache() {
        AppQuery.invalidateLauncherCache()
    }

    fun launchAppDirect(
        context: Context,
        packageName: String,
        className: String,
        miniWindow: Boolean = false,
        miniWindowHorizontalBias: Float = 0f,
        miniWindowVerticalBias: Float = 0f,
        miniWindowVerticalOffsetFraction: Float = 0f,
        miniWindowWidthFraction: Float = 0.46f,
        miniWindowHeightFraction: Float = 0.74f,
        miniWindowOverrideBounds: Boolean = false,
    ): Boolean {
        return Launcher.launchApp(
            context, packageName, className, miniWindow,
            miniWindowHorizontalBias, miniWindowVerticalBias,
            miniWindowVerticalOffsetFraction,
            miniWindowWidthFraction, miniWindowHeightFraction,
            overrideBounds = miniWindowOverrideBounds,
        )
    }

    fun launchAppActivityDirect(context: Context, packageName: String, className: String): Boolean {
        return Launcher.launchAppActivity(context, packageName, className)
    }

    fun launchUrlDirect(
        context: Context,
        data: OpenAppOrUrlData,
        miniWindowHorizontalBias: Float = 0f,
        miniWindowVerticalBias: Float = 0f,
        miniWindowVerticalOffsetFraction: Float = 0f,
        miniWindowWidthFraction: Float = 0.46f,
        miniWindowHeightFraction: Float = 0.74f,
        miniWindowOverrideBounds: Boolean = false,
    ): Boolean {
        return Launcher.launchUrl(
            context = context,
            data = data,
            miniWindowHorizontalBias = miniWindowHorizontalBias,
            miniWindowVerticalBias = miniWindowVerticalBias,
            miniWindowVerticalOffsetFraction = miniWindowVerticalOffsetFraction,
            miniWindowWidthFraction = miniWindowWidthFraction,
            miniWindowHeightFraction = miniWindowHeightFraction,
            miniWindowOverrideBounds = miniWindowOverrideBounds,
        )
    }

    fun queryCombinedQuickAppList(context: Context, frozenApps: List<AppInfo>): QuickAppLauncherAppList {
        val frozenPkgSet = frozenApps.map { it.packageName }.toSet()
        val launcherApps = AppQuery.queryLauncherActivities(context = context, allowRepeatPackage = false)
        val normalPkgNames = launcherApps.map { it.packageName }.toSet()
        return QuickAppLauncherAppList(
            apps = launcherApps + frozenApps.filter { it.packageName !in normalPkgNames },
            frozenPkgs = frozenPkgSet
        )
    }

    fun queryAppListWithFrozenMark(context: Context, frozenPkgs: Set<String>): QuickAppLauncherAppList {
        val launcherApps = AppQuery.queryLauncherActivities(context = context, allowRepeatPackage = false)
        return QuickAppLauncherAppList(
            apps = launcherApps,
            frozenPkgs = frozenPkgs
        )
    }

    fun launchAppInPopup(
        context: Context,
        packageName: String,
        className: String,
        horizontalBias: Float = 0.5f,
        verticalBias: Float = 1.0f,
        verticalOffsetFraction: Float = 1.0f,
        widthFraction: Float = 0.5f,
        heightFraction: Float = 0.5f,
        overrideBounds: Boolean = false,
    ) {
        Launcher.launchAppInPopup(
            context, packageName, className,
            horizontalBias, verticalBias, verticalOffsetFraction,
            widthFraction, heightFraction, overrideBounds,
        )
    }

    fun launchShortcutInfo(context: Context, shortcutInfo: LauncherInfo.ShortcutInfo) {
        Launcher.launchShortcutInfo(context, shortcutInfo)
    }

    fun isLauncherPackage(context: Context, packageName: String): Boolean =
        LauncherEnvironment.isLauncherPackage(context, packageName)
}
