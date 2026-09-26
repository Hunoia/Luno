package hunoia.luno.action.api

import android.accessibilityservice.AccessibilityService
import android.content.Context
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.GestureSettings
import kotlinx.coroutines.CoroutineScope

data class ActionHandlerContext(
    val accessibilityService: AccessibilityService,
    val appContext: Context,
    val scope: CoroutineScope,
    val actionSettings: ActionSettings,
    val advancedSettings: AdvancedSettings,
    val gestureSettings: GestureSettings,
    val showToast: (String) -> Unit,
    val showLongToast: (String) -> Unit,
    val currentPackageName: () -> String? = { null },
    val nowInLauncher: () -> Boolean = { false },
    val requestEnableFrozenPackage: (String, (Boolean) -> Unit) -> Unit = { _, onResult -> onResult(false) },
    val toggleQuickAppLauncher: () -> Unit = {},
    val showPointer: (Boolean?) -> Boolean = { false },
    val showVolumeScrub: () -> Boolean = { false },
    val toggleKeepScreenOn: () -> Unit = {},
    val hideGestureButton: (Long) -> Unit = {},
    val showVersionTooLowToast: (Int) -> Unit = {},
    val previousApp: suspend () -> Unit = {},
)
