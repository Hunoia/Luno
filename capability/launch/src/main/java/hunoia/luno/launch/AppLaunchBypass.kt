package hunoia.luno.launch

import android.content.Context
import hunoia.luno.capability.launch.R
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.AppLaunchSource
import hunoia.luno.config.model.MiniWindowSettings
import hunoia.luno.quicklaunch.launch.AppLaunchCoordinator

/**
 * 启动动作的共享门面：解冻被禁用应用后再启动。
 *
 * 归在中性 `hunoia.luno.launch` 包：动作执行器、链接分发、搜索都要用，
 * 不能各自依赖 quicklaunch。Phase 2 会随启动基础设施一起并入 `:feature:execute`。
 *
 * 目前仍转调 `quicklaunch.launch.AppLaunchCoordinator`；启动机制整体上收是 Phase 2 的事，
 * 这里先切断「link → quicklaunch」的直接依赖。
 */
object AppLaunchBypass {

    suspend fun launchWithAutoUnfreeze(
        context: Context,
        packageName: String,
        className: String,
        miniWindow: Boolean = false,
        miniWindowSettings: MiniWindowSettings = MiniWindowSettings(),
        intentAction: String = "",
        data: String = "",
        extras: Map<String, String> = emptyMap(),
        source: AppLaunchSource = AppLaunchSource.ACTION_DISPATCH,
        unfreezePackage: suspend (context: Context, packageName: String) -> Boolean = { _, _ -> true },
    ): Boolean {
        if (!ensureEnabled(context, packageName, unfreezePackage)) return false
        return AppLaunchCoordinator.launchApp(
            context = context,
            packageName = packageName,
            className = className,
            miniWindow = miniWindow,
            miniWindowSettings = miniWindowSettings,
            intentAction = intentAction,
            data = data,
            extras = extras,
            source = source,
        )
    }

    /** 应用被禁用时先解冻再启动；解冻失败弹提示并返回 false。 */
    private suspend fun ensureEnabled(
        context: Context,
        packageName: String,
        unfreezePackage: suspend (context: Context, packageName: String) -> Boolean,
    ): Boolean {
        val disabledApps = LaunchCapability.resolveDisabledState()
        if (!disabledApps.isDisabled(packageName)) return true
        if (!unfreezePackage(context, packageName)) {
            showToast(R.string.enable_disabled_app_failed)
            return false
        }
        disabledApps.markEnabled(packageName)
        disabledApps.invalidateLauncherCache()
        return true
    }
}
