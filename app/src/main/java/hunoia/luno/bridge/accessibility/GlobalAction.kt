package hunoia.luno.bridge.accessibility

import android.accessibilityservice.AccessibilityService

object GlobalAction {

    fun lockScreen(service: AccessibilityService): Boolean {
        return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
    }

    fun powerDialog(service: AccessibilityService): Boolean {
        return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
    }

    fun takeScreenshot(service: AccessibilityService): Boolean {
        return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
    }

    fun toggleSplitScreen(service: AccessibilityService): Boolean {
        return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
    }
}
