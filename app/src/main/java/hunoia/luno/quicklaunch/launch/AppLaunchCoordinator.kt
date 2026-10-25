package hunoia.luno.quicklaunch.launch

import android.content.Context
import hunoia.luno.config.model.AppLaunchSource
import hunoia.luno.config.model.MiniWindowSettings
import hunoia.luno.domain.usage.AppUsageCenter
import hunoia.luno.quicklaunch.model.AppInfo

/**
 * 应用启动收口：负责「启动 + 成功后记统计」。放在启动机制层，避免 domain 反向依赖 quicklaunch。
 * [Launcher] 只保留 Intent/小窗等纯启动机制，不再直接依赖统计。
 * 需要新增启动入口时走这里，避免又出现某条链路漏记。
 */
object AppLaunchCoordinator {

    fun launchApp(
        context: Context,
        packageName: String,
        className: String,
        miniWindow: Boolean,
        miniWindowSettings: MiniWindowSettings = MiniWindowSettings(),
        intentAction: String = "",
        data: String = "",
        extras: Map<String, String> = emptyMap(),
        source: AppLaunchSource = AppLaunchSource.UNKNOWN,
    ): Boolean {
        val launched = Launcher.launchApp(
            context = context,
            packageName = packageName,
            className = className,
            miniWindow = miniWindow,
            miniWindowSettings = miniWindowSettings,
            intentAction = intentAction,
            data = data,
            extras = extras,
        )
        if (launched) AppUsageCenter.recordLaunch(packageName, source)
        return launched
    }

    fun launchAppInfo(
        context: Context,
        appInfo: AppInfo,
        miniWindow: Boolean,
        miniWindowSettings: MiniWindowSettings = MiniWindowSettings(),
        source: AppLaunchSource = AppLaunchSource.UNKNOWN,
    ): Boolean = launchApp(
        context = context,
        packageName = appInfo.packageName,
        className = appInfo.className,
        miniWindow = miniWindow,
        miniWindowSettings = miniWindowSettings,
        source = source,
    )

    fun launchAppActivity(
        context: Context,
        packageName: String,
        className: String,
        source: AppLaunchSource = AppLaunchSource.UNKNOWN,
    ): Boolean {
        val launched = Launcher.launchAppActivity(context, packageName, className)
        if (launched) AppUsageCenter.recordLaunch(packageName, source)
        return launched
    }
}
