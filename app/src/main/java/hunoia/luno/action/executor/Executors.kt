package hunoia.luno.action.executor

import hunoia.luno.action.controller.AccessibilityController
import hunoia.luno.action.controller.IntentController
import hunoia.luno.action.controller.PackageController
import hunoia.luno.action.controller.SettingsController
import hunoia.luno.action.controller.ShellController
import hunoia.luno.action.execution.ActionExecutor
import hunoia.luno.action.execution.ExecutorContext
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionFailure
import hunoia.luno.action.model.ActionResult
import hunoia.luno.action.model.ButtonTarget
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.OpenAppOrUrlData
import hunoia.luno.config.model.OpenUrlQueryParameter
import hunoia.luno.config.model.VolumeScrubConfig
import hunoia.luno.config.model.effectiveForAction
import hunoia.luno.config.model.miniWindowSettings
import hunoia.luno.quicklaunch.QuickLaunchFacade
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AccessibilityExecutors(private val controller: AccessibilityController) : ActionExecutor {

    override val supportedTypes: Set<String> = setOf(
        "accessibility.back", "accessibility.home", "accessibility.recents",
        "accessibility.tap", "accessibility.longPress", "accessibility.swipe",
        "accessibility.inputText", "accessibility.screenshot", "accessibility.splitScreen",
        "accessibility.powerButton", "accessibility.notificationPanel",
        "accessibility.quickPanel", "accessibility.lockScreen"
    )

    override suspend fun execute(action: Action, context: ExecutorContext): ActionResult {
        return when (action) {
            is Action.Back -> executionResult(controller.back())
            is Action.Home -> executionResult(controller.home())
            is Action.Recents -> executionResult(controller.recents())
            is Action.Tap -> {
                val (x, y) = context.touchPosition
                    ?: return ActionResult.Failed(ActionFailure.ExecutionFailed)
                context.hideGestureButton(250L)
                delay(80)
                executionResult(controller.click(x, y))
            }
            is Action.LongPress -> {
                val (x, y) = context.touchPosition
                    ?: return ActionResult.Failed(ActionFailure.ExecutionFailed)
                context.hideGestureButton(250L)
                delay(80)
                executionResult(controller.longPress(x, y))
            }
            is Action.Swipe -> {
                val performed = when (action.direction) {
                    hunoia.luno.action.model.SwipeDirection.UP -> controller.swipeUp()
                    hunoia.luno.action.model.SwipeDirection.DOWN -> controller.swipeDown()
                    hunoia.luno.action.model.SwipeDirection.LEFT -> controller.swipeLeft()
                    hunoia.luno.action.model.SwipeDirection.RIGHT -> controller.swipeRight()
                    hunoia.luno.action.model.SwipeDirection.TO_TOP -> controller.scrollToTop()
                }
                executionResult(performed)
            }
            is Action.InputText -> executionResult(controller.inputText(action.text))
            is Action.Screenshot -> {
                context.scope.launch {
                    delay(200)
                    controller.takeScreenshot()
                }
                ActionResult.Success()
            }
            is Action.SplitScreen -> executionResult(controller.toggleSplitScreen())
            is Action.PowerButton -> executionResult(controller.powerDialog())
            is Action.NotificationPanel -> executionResult(controller.notificationPanel())
            is Action.QuickPanel -> executionResult(controller.quickPanel())
            is Action.LockScreen -> executionResult(controller.lockScreen())
            else -> ActionResult.Failed(ActionFailure.Unsupported)
        }
    }

    private fun executionResult(performed: Boolean): ActionResult =
        if (performed) ActionResult.Success() else ActionResult.Failed(ActionFailure.ExecutionFailed)
}

class IntentExecutors(private val intentController: IntentController) : ActionExecutor {

    override val supportedTypes: Set<String> = setOf(
        "intent.openUrl", "intent.shareText", "intent.shareFile", "intent.assist"
    )

    override suspend fun execute(action: Action, context: ExecutorContext):ActionResult {
        return when (action) {
            is Action.OpenUrl -> {
                val adv = context.advancedSettings.effectiveForAction(action)
                val data = OpenAppOrUrlData(
                    type = OpenAppOrUrlData.TYPE_URL,
                    url = action.url,
                    queryParameters = action.queryParameters.map {
                        OpenUrlQueryParameter(name = it.name, value = it.value, enabled = it.enabled)
                    },
                )
                val ok = QuickLaunchFacade.launchUrlDirect(
                    context = context.appContext,
                    data = data,
                    miniWindowSettings = adv.miniWindowSettings(),
                )
                if (ok) ActionResult.Success() else ActionResult.Failed(ActionFailure.ExecutionFailed)
            }
            is Action.ShareText -> intentController.shareText(action.text, action.mimeType)
            is Action.ShareFile -> intentController.shareFile()
            is Action.Assist -> intentController.launchAssist()
            else -> ActionResult.Failed(ActionFailure.Unsupported)
        }
    }
}

class InternalExecutors : ActionExecutor {

    override val supportedTypes: Set<String> = setOf(
        "internal.none", "internal.subGesture", "internal.hideGestureButton",
        "internal.buttonsVisibility",
        "internal.quickAppLauncher", "internal.quickSearch", "internal.volumeScrub",
        "internal.previousApp"
    )

    override suspend fun execute(action: Action, context: ExecutorContext): ActionResult {
        return when (action) {
            is Action.None -> ActionResult.Success()
            is Action.SubGesture -> ActionResult.Success()
            is Action.HideGestureButton -> {
                context.hideGestureButton(action.delayMs)
                ActionResult.Success()
            }
            is Action.ButtonsVisibility -> executeButtonsVisibility(action)
            is Action.QuickAppLauncher -> {
                context.toggleQuickAppLauncher()
                ActionResult.Success()
            }
            is Action.QuickSearch -> {
                context.showQuickSearch()
                ActionResult.Success()
            }
            is Action.VolumeScrub -> {
                val config = VolumeScrubConfig(action.horizontalEnabled, action.stepThresholdDp)
                if (context.showVolumeScrub(config)) ActionResult.Success() else ActionResult.Failed(ActionFailure.ExecutionFailed)
            }
            is Action.PreviousApp -> {
                context.previousApp(action.excludePackageNames)
                ActionResult.Success()
            }
            else -> ActionResult.Failed(ActionFailure.Unsupported)
        }
    }

    /**
     * 触钮显隐：直接改按钮 enabled 存量（DataStore 更新自带顺序，后写覆盖先写，
     * 即规则列表顺序=优先级）。失败吞掉报 Failed，不抛。
     */
    private suspend fun executeButtonsVisibility(action: Action.ButtonsVisibility): ActionResult {
        val ok = runCatching {
            ConfigProvider.updateGestureButtons { list ->
                val ids = when (action.target) {
                    ButtonTarget.ALL -> list.map { it.id }.toSet()
                    ButtonTarget.EXCEPT -> list.map { it.id }
                        .filterNot { it in action.buttonIds }.toSet()
                    ButtonTarget.ONLY -> action.buttonIds.toSet()
                }
                list.map { button ->
                    if (button.id in ids) button.copy(enabled = action.visible) else button
                }
            }
            true
        }.getOrDefault(false)
        return if (ok) ActionResult.Success() else ActionResult.Failed(ActionFailure.ExecutionFailed)
    }
}

class PackageExecutors(private val controller: PackageController) : ActionExecutor {

    override val supportedTypes: Set<String> = setOf(
        "package.enable", "package.disable", "package.forceStop",
        "package.clearData", "package.uninstall"
    )

    override suspend fun execute(action: Action, context: ExecutorContext): ActionResult {
        return when (action) {
            is Action.PackageEnable -> controller.enable(action.packageName)
            is Action.PackageDisable -> controller.disable(action.packageName)
            is Action.PackageForceStop -> controller.forceStop(action.packageName)
            is Action.PackageClearData -> controller.clearData(action.packageName)
            is Action.PackageUninstall -> controller.uninstall(action.packageName)
            else -> ActionResult.Failed(ActionFailure.Unsupported)
        }
    }
}

class SettingsExecutors(private val controller: SettingsController) : ActionExecutor {

    override val supportedTypes: Set<String> = setOf("settings.get", "settings.put")

    override suspend fun execute(action: Action, context: ExecutorContext): ActionResult {
        return when (action) {
            is Action.SettingsGet -> controller.get(action.namespace, action.key)
            is Action.SettingsPut -> controller.put(action.namespace, action.key, action.value)
            else -> ActionResult.Failed(ActionFailure.Unsupported)
        }
    }
}

class ShellExecutor(private val shellController: ShellController) : ActionExecutor {

    override val supportedTypes: Set<String> = setOf("shell.custom")

    override suspend fun execute(action: Action, context: ExecutorContext): ActionResult {
        return when (action) {
            is Action.CustomCommand -> {
                val command = action.command.trim()
                val result = shellController.runCommand(command)
                val message = if (action.showToast) {
                    if (result.success) {
                        result.output.ifBlank { "No output" }
                    } else {
                        result.error ?: result.output.ifBlank { "Unknown error" }
                    }.take(500)
                } else {
                    null
                }
                if (result.success) ActionResult.Success(message) else ActionResult.Failed(ActionFailure.ExecutionFailed, message)
            }
            else -> ActionResult.Failed(ActionFailure.Unsupported)
        }
    }
}

class SystemCmdExecutors(private val shellController: ShellController) : ActionExecutor {

    override val supportedTypes: Set<String> = setOf("systemCmd.reboot", "systemCmd.shutdown")

    override suspend fun execute(action: Action, context: ExecutorContext): ActionResult {
        return when (action) {
            is Action.Reboot -> {
                val result = shellController.runCommand("reboot")
                if (result.success) ActionResult.Success() else ActionResult.Failed(ActionFailure.ExecutionFailed)
            }
            is Action.Shutdown -> {
                val result = shellController.runCommand("shutdown -p")
                if (result.success) ActionResult.Success() else ActionResult.Failed(ActionFailure.ExecutionFailed)
            }
            else -> ActionResult.Failed(ActionFailure.Unsupported)
        }
    }
}
