package hunoia.luno.action.controller

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN
import hunoia.luno.bridge.DensityProvider
import hunoia.luno.bridge.accessibility.Accessibility

class AccessibilityController(private val service: AccessibilityService) {

    fun back(): Boolean = service.performGlobalAction(GLOBAL_ACTION_BACK)
    fun home(): Boolean = service.performGlobalAction(GLOBAL_ACTION_HOME)
    fun recents(): Boolean = service.performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun lockScreen(): Boolean = service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    fun powerDialog(): Boolean = service.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
    fun takeScreenshot(): Boolean = service.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
    fun toggleSplitScreen(): Boolean = service.performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
    fun notificationPanel(): Boolean = service.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun quickPanel(): Boolean = service.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

    fun click(x: Int, y: Int): Boolean = Accessibility.click(service, x, y)
    fun longPress(x: Int, y: Int): Boolean = Accessibility.longPress(service, x, y)
    fun doubleTap(x: Int, y: Int): Boolean = Accessibility.doubleTap(service, x, y)

    fun scrollToTop(): Boolean = Accessibility.fastVerticalScroll(service, toTop = true)

    fun swipeUp(): Boolean {
        val w = DensityProvider.screenWidthPx
        val h = DensityProvider.screenHeightPx
        val startX = w / 2
        val startY = h * 3 / 4
        val endY = h / 4
        return dispatchSwipe(startX.toFloat(), startY.toFloat(), startX.toFloat(), endY.toFloat(), 300)
    }

    fun swipeDown(): Boolean {
        val w = DensityProvider.screenWidthPx
        val h = DensityProvider.screenHeightPx
        val startX = w / 2
        val startY = h / 4
        val endY = h * 3 / 4
        return dispatchSwipe(startX.toFloat(), startY.toFloat(), startX.toFloat(), endY.toFloat(), 300)
    }

    fun swipeLeft(): Boolean {
        val w = DensityProvider.screenWidthPx
        val h = DensityProvider.screenHeightPx
        val startY = h / 2
        val startX = w * 3 / 4
        val endX = w / 4
        return dispatchSwipe(startX.toFloat(), startY.toFloat(), endX.toFloat(), startY.toFloat(), 300)
    }

    fun swipeRight(): Boolean {
        val w = DensityProvider.screenWidthPx
        val h = DensityProvider.screenHeightPx
        val startY = h / 2
        val startX = w / 4
        val endX = w * 3 / 4
        return dispatchSwipe(startX.toFloat(), startY.toFloat(), endX.toFloat(), startY.toFloat(), 300)
    }

    private fun dispatchSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long): Boolean {
        val builder = android.accessibilityservice.GestureDescription.Builder()
        val path = android.graphics.Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        builder.addStroke(
            android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, duration)
        )
        return service.dispatchGesture(builder.build(), null, null)
    }

    fun inputText(text: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val bundle = android.os.Bundle().apply {
            putCharSequence("android.view.accessibility.EXTRA_TEXT", text)
        }
        return root.performAction(
            android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,
            bundle
        )
    }
}
