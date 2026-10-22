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
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.bridge.system.Accessibility

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
    fun longPress(x: Int, y: Int, durationMs: Long = 1000L): Boolean =
        Accessibility.longPress(service, x, y, durationMs)
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

    /**
     * 输入文本：焦点可编辑节点上写入。
     * 首选 SET_TEXT（不动剪贴板）；追加/SET_TEXT 失败时走剪贴板 + ACTION_PASTE：
     * SET_TEXT 在 Compose 输入框 / WebView 等控件上不生效甚至表现为清空，PASTE 是通用的。
     * 替换模式 = SELECT_ALL + PASTE；追加模式 = 直接 PASTE（插在光标处）。
     * 副作用：会覆盖用户剪贴板（无恢复手段，接受）。
     */
    fun inputText(text: String, append: Boolean = false): Boolean {
        if (text.isEmpty()) return false
        val root = service.rootInActiveWindow ?: return false
        val focused = root.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return false
        val editable = findEditable(focused) ?: return false
        editable.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS)
        if (!append) {
            editable.performAction(
                android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_SELECTION,
                android.os.Bundle().apply {
                    putInt(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                    putInt(
                        android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,
                        (editable.text?.length ?: 0),
                    )
                },
            )
        }
        // 替换模式：先试 SET_TEXT（干净、不碰剪贴板），失败回退 SELECT_ALL + PASTE。
        // 追加模式：直接 PASTE，插入在光标处（SET_TEXT 拼旧文本不可靠）。
        if (!append) {
            val setTextOk = editable.performAction(
                android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,
                android.os.Bundle().apply {
                    putCharSequence("android.view.accessibility.EXTRA_TEXT", text)
                },
            )
            if (setTextOk) return true
            editable.performAction(
                android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_SELECTION,
                android.os.Bundle().apply {
                    putInt(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                    putInt(
                        android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,
                        (editable.text?.length ?: 0),
                    )
                },
            )
        }
        // 回退：剪贴板 + PASTE。
        val cm = service.getSystemService(android.content.ClipboardManager::class.java) ?: return false
        cm.setPrimaryClip(android.content.ClipData.newPlainText("luno_input", text))
        if (!append) editable.performAction(
                android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_SELECTION,
                android.os.Bundle().apply {
                    putInt(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                    putInt(
                        android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,
                        (editable.text?.length ?: 0),
                    )
                },
            )
        return editable.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_PASTE)
    }

    /** 从焦点节点出发找可编辑节点：先看自身，再向上找父链，最后向下搜子树。 */
    private fun findEditable(start: android.view.accessibility.AccessibilityNodeInfo): android.view.accessibility.AccessibilityNodeInfo? {
        if (start.isEditable) return start
        var parent = start.parent
        while (parent != null) {
            if (parent.isEditable) return parent
            parent = parent.parent
        }
        val queue = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
        queue.add(start)
        var visited = 0
        while (queue.isNotEmpty() && visited < 100) {
            val node = queue.removeFirst()
            visited++
            if (node.isEditable) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return null
    }

    /** 滚动焦点元素（ACTION_SCROLL_FORWARD/BACKWARD，比固定坐标 swipe 精准）。 */
    fun scrollFocused(forward: Boolean): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val target = root.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return false
        val action = if (forward) {
            android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        return target.performAction(action)
    }
}
