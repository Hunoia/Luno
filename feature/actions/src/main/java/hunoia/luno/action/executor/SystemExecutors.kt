package hunoia.luno.action.executor

import hunoia.luno.actions.R
import hunoia.luno.action.controller.AudioController
import hunoia.luno.action.controller.ClipboardController
import hunoia.luno.action.execution.ActionExecutor
import hunoia.luno.action.execution.ExecutorContext
import hunoia.luno.action.model.Action
import hunoia.luno.core.domain.ActionFailure
import hunoia.luno.core.domain.ActionResult
import hunoia.luno.action.model.MediaCommand
import hunoia.luno.bridge.system.FlashlightController
import hunoia.luno.bridge.system.QuickToggleController
import hunoia.luno.bridge.system.gotoAppDetailSettings
import hunoia.luno.core.domain.QuickToggleKind
import android.content.Intent
import android.provider.Settings
import hunoia.luno.action.model.SettingsPage

class SystemExecutors(
    private val audioController: AudioController,
    private val clipboardController: ClipboardController,
) : ActionExecutor {

    override val supportedTypes: Set<String> = setOf(
        "system.openSettings", "system.volume",
        "system.clipboardRandomName",
        "system.speechRecognize",
        "system.mediaPlayPause", "system.mediaNext", "system.mediaPrevious", "system.mediaMute",
        "system.flashlight", "system.toggle"
    )

    override suspend fun execute(action: Action, context: ExecutorContext): ActionResult {
        return when (action) {
            is Action.OpenSettings -> {
                val ok = openSettingsPage(context, action.page)
                if (ok) ActionResult.Success() else ActionResult.Failed(ActionFailure.ExecutionFailed)
            }
            is Action.Volume -> {
                audioController.adjustVolume(action.stream, action.direction)
                ActionResult.Success()
            }
            is Action.ClipboardRandomName -> executeRandomName(context)
            is Action.SpeechRecognize -> {
                if (context.showSpeechRecognition()) ActionResult.Success()
                else ActionResult.Failed(ActionFailure.Unsupported)
            }
            is Action.MediaPlayPause -> {
                audioController.mediaCommand(MediaCommand.PLAY_PAUSE)
                ActionResult.Success()
            }
            is Action.MediaNext -> {
                audioController.mediaCommand(MediaCommand.NEXT)
                ActionResult.Success()
            }
            is Action.MediaPrevious -> {
                audioController.mediaCommand(MediaCommand.PREVIOUS)
                ActionResult.Success()
            }
            is Action.MediaMute -> {
                audioController.mediaCommand(MediaCommand.MUTE)
                ActionResult.Success()
            }
            is Action.Flashlight -> executeFlashlight(context)
            is Action.QuickToggle -> executeQuickToggle(context, action.kind)
            else -> ActionResult.Failed(ActionFailure.Unsupported)
        }
    }

    private suspend fun executeRandomName(context: ExecutorContext): ActionResult {
        val appContext = context.appContext
        val name = clipboardController.generateRandomName()
        return when {
            name == null -> ActionResult.Failed(
                ActionFailure.ExecutionFailed,
                appContext.getString(R.string.random_name_generate_failed),
            )
            clipboardController.copyText(name) -> ActionResult.Success(name)
            else -> ActionResult.Failed(
                ActionFailure.ExecutionFailed,
                appContext.getString(R.string.random_name_copy_failed),
            )
        }
    }

    private suspend fun executeFlashlight(context: ExecutorContext): ActionResult {
        val appContext = context.appContext
        if (!FlashlightController.isSupported(appContext)) {
            return ActionResult.Failed(
                reason = ActionFailure.ExecutionFailed,
                userMessage = appContext.getString(R.string.flashlight_failed),
            )
        }
        if (!FlashlightController.hasPermission(appContext)) {
            appContext.gotoAppDetailSettings()
            return ActionResult.Failed(
                reason = ActionFailure.PermissionDenied,
                userMessage = appContext.getString(R.string.grant_camera_permission),
            )
        }
        return if (FlashlightController.toggle(appContext)) {
            ActionResult.Success()
        } else {
            ActionResult.Failed(
                reason = ActionFailure.ExecutionFailed,
                userMessage = appContext.getString(R.string.flashlight_failed),
            )
        }
    }

    /**
     * 快捷开关：能直切就切（读状态 → 取反 → 写入 → 读回），切不动就降级到系统面板/设置页。
     * 热点没有可靠的写入/读取通道，视为「打开设置页」型开关，不计失败。
     */
    private suspend fun executeQuickToggle(
        context: ExecutorContext,
        kind: QuickToggleKind,
    ): ActionResult {
        if (!QuickToggleController.canToggle(kind)) {
            return openToggleFallback(context, kind, silent = true)
        }
        val current = QuickToggleController.isOn(context.appContext, kind)
        if (current != null && QuickToggleController.setOn(context.appContext, kind, !current)) {
            return ActionResult.Success()
        }
        return openToggleFallback(context, kind, silent = false)
    }

    private fun openToggleFallback(
        context: ExecutorContext,
        kind: QuickToggleKind,
        silent: Boolean,
    ): ActionResult {
        val appContext = context.appContext
        val opened = runCatching {
            appContext.startActivity(QuickToggleController.fallbackIntent(kind))
        }.isSuccess
        if (silent && opened) return ActionResult.Success()
        return ActionResult.Failed(
            reason = ActionFailure.ExecutionFailed,
            userMessage = appContext.getString(
                if (opened) R.string.quick_toggle_fallback else R.string.quick_toggle_unavailable,
            ),
        )
    }

    private fun openSettingsPage(context: ExecutorContext, page: SettingsPage): Boolean {
        val intent = when (page) {
            SettingsPage.WIFI -> Intent(Settings.ACTION_WIFI_SETTINGS)
            SettingsPage.BLUETOOTH -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            SettingsPage.DISPLAY -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            SettingsPage.BATTERY -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            SettingsPage.APPS -> Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
            SettingsPage.NETWORK -> Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS)
            SettingsPage.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            SettingsPage.MAIN -> Intent(Settings.ACTION_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.appContext.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
