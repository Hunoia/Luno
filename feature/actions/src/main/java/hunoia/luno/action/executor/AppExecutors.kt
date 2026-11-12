package hunoia.luno.action.executor

import hunoia.luno.action.execution.ActionExecutor
import hunoia.luno.action.execution.ExecutorContext
import hunoia.luno.action.model.Action
import hunoia.luno.core.domain.ActionFailure
import hunoia.luno.core.domain.ActionResult
import hunoia.luno.core.domain.LaunchSpec

/**
 * 应用类执行器：只做「动作 → 端口调用」的翻译。
 *
 * 启动机制（解冻被禁用应用、小窗、弹窗、快捷方式、Intent 覆盖）都在 `:app` 的
 * `LaunchSurfaces` 里；执行器不读配置、不碰 PackageManager。
 */
class AppExecutors : ActionExecutor {

    override val supportedTypes: Set<String> = setOf(
        hunoia.luno.core.domain.AppLaunchAction.TYPE_ID,
        "app.details",
        "app.popup",
        "system.mini_window",
        "app.launchShortcut",
    )

    override suspend fun execute(action: Action, context: ExecutorContext): ActionResult {
        return when (action) {
            is Action.LaunchApp -> {
                val ok = context.surfaces.launchApp(
                    LaunchSpec(
                        packageName = action.packageName,
                        className = action.className,
                        miniWindow = action.miniWindow,
                        intentAction = action.intentAction,
                        data = action.data,
                        extras = action.extras,
                        userId = action.userId,
                        anchor = context.launchAnchor,
                    )
                )
                if (ok) ActionResult.Success() else ActionResult.Failed(ActionFailure.ExecutionFailed)
            }
            is Action.AppDetails -> context.surfaces.openAppDetails(action.packageName, action.userId)
            is Action.Popup -> {
                val foreground = context.foregroundHint ?: context.environment.currentPackageName()
                if (foreground.isNullOrBlank()) {
                    ActionResult.Failed(ActionFailure.NoForegroundApp)
                } else {
                    context.surfaces.launchInPopup(foreground, context.launchAnchor)
                }
            }
            is Action.MiniWindow -> {
                val foreground = context.foregroundHint ?: context.environment.currentPackageName()
                if (foreground.isNullOrBlank()) {
                    ActionResult.Failed(ActionFailure.NoForegroundApp)
                } else {
                    context.surfaces.launchMiniWindow(foreground, context.launchAnchor)
                }
            }
            is Action.LaunchShortcut -> {
                if (context.surfaces.launchShortcut(action.data)) {
                    ActionResult.Success()
                } else {
                    ActionResult.Failed(ActionFailure.InvalidParameter)
                }
            }
            else -> ActionResult.Failed(ActionFailure.Unsupported)
        }
    }
}
