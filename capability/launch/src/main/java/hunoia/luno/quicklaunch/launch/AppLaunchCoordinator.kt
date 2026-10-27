package hunoia.luno.quicklaunch.launch

import android.content.Context
import hunoia.luno.core.domain.AppLaunchSource
import hunoia.luno.config.model.MiniWindowSettings
import hunoia.luno.launch.LaunchCapability
import hunoia.luno.core.domain.AppInfo

/**
 * 应用启动收口：负责「启动 + 成功后记统计」。统计经 [LaunchCapability] 注入的
 * `LaunchTelemetry` 落库（配置流写入），本库不引用 UI 层门面。
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
        if (launched) LaunchCapability.recordLaunch(packageName, source)
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
        if (launched) LaunchCapability.recordLaunch(packageName, source)
        return launched
    }
}
