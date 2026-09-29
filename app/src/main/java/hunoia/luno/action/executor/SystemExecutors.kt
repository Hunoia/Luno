package hunoia.luno.action.executor

import hunoia.luno.action.controller.AudioController
import hunoia.luno.action.controller.ClipboardController
import hunoia.luno.action.controller.SystemController
import hunoia.luno.action.controller.VibrateController
import hunoia.luno.action.execution.ActionExecutor
import hunoia.luno.action.execution.ExecutorContext
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionFailure
import hunoia.luno.action.model.ActionResult
import android.content.Intent
import android.provider.Settings
import hunoia.luno.action.model.SettingsPage

class SystemExecutors(
    private val audioController: AudioController,
    private val clipboardController: ClipboardController,
    private val systemController: SystemController,
    private val vibrateController: VibrateController,
) : ActionExecutor {

    override val supportedTypes: Set<String> = setOf(
        "system.openSettings", "system.volume", "system.vibrate",
        "system.clipboard", "system.media", "system.flashlight"
    )

    override suspend fun execute(action: Action, context: ExecutorContext): ActionResult {
        return when (action) {
            is Action.OpenSettings -> {
                val ok = openSettingsPage(context, action.page)
                if (ok) ActionResult.Success else ActionResult.Failed(ActionFailure.ExecutionFailed)
            }
            is Action.Volume -> {
                audioController.adjustVolume(action.stream, action.direction)
                ActionResult.Success
            }
            is Action.Vibrate -> {
                vibrateController.vibrateWithPattern(action.pattern)
                ActionResult.Success
            }
            is Action.Clipboard -> clipboardController.copyClipboard(action)
            is Action.Media -> {
                audioController.mediaCommand(action.command)
                ActionResult.Success
            }
            is Action.Flashlight -> systemController.toggleFlashlight()
            else -> ActionResult.Failed(ActionFailure.Unsupported)
        }
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
