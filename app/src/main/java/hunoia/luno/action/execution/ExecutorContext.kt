package hunoia.luno.action.execution

import android.accessibilityservice.AccessibilityService
import android.content.Context
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionResult
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AdvancedSettings
import kotlinx.coroutines.CoroutineScope

data class ExecutorContext(
    val accessibilityService: AccessibilityService,
    val appContext: Context,
    val scope: CoroutineScope,
    val actionSettings: ActionSettings = ActionSettings(),
    val advancedSettings: AdvancedSettings = AdvancedSettings(),
    val showToast: (String) -> Unit = {},
    val showLongToast: (String) -> Unit = {},
    val currentPackageName: () -> String? = { null },
    val nowInLauncher: () -> Boolean = { false },
    val requestEnableDisabledPackage: (String, (Boolean) -> Unit) -> Unit = { _, onResult -> onResult(false) },
    val toggleQuickAppLauncher: () -> Unit = {},
    val showVolumeScrub: () -> Boolean = { false },
    val toggleKeepScreenOn: () -> Unit = {},
    val hideGestureButton: (Long) -> Unit = {},
    val showVersionTooLowToast: (Int) -> Unit = {},
    val previousApp: suspend () -> Unit = {},
)
