package hunoia.luno.action.controller

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.LunoLog as Log

/**
 * 把剪贴板里当前的图片粘进落点下（或焦点）的输入框。
 *
 * 与 [TextInserter] 共用同一套节点定位，但图片没有 `ACTION_SET_TEXT`，只能依赖目标
 * 编辑器自己支持 `ACTION_PASTE`（聊天 / 笔记类通常支持，纯文本输入框会拒绝）。
 * 因此调用方必须先把图片写进系统剪贴板；这里只负责找框、聚焦、粘贴。
 *
 * 失败不是异常路径：找不到输入框或不支持粘贴时静默返回，由 UI 决定是否提示。
 */
object ImageInserter {

    enum class Result {
        Pasted,
        NoTarget,
        Failed,
    }

    /** 粘到当前焦点输入框。 */
    fun pasteFocused(service: AccessibilityService): Result {
        repeat(TextInserter.FindAttempts) { attempt ->
            val node = TextInserter.focusedInput(service)
                ?: TextInserter.activeRootInput(service, 0, 0)
                ?: TextInserter.singleInputInTopWindow(service)
            if (node != null) return paste(service, node)
            if (attempt < TextInserter.FindAttempts - 1) SystemClock.sleep(TextInserter.FindRetryMs)
        }
        return Result.NoTarget
    }

    /** 粘到拖拽落点下（或最近的）输入框。 */
    fun pasteAt(service: AccessibilityService, x: Int, y: Int): Result {
        repeat(TextInserter.FindAttempts) { attempt ->
            val node = TextInserter.editableNodeAt(service, x, y)
                ?: TextInserter.focusedInput(service)
                ?: TextInserter.activeRootInput(service, x, y)
                ?: TextInserter.singleInputInTopWindow(service)
            if (node != null) {
                Log.i(LOG_TAG, "image paste at ($x,$y): target pkg=${node.packageName} attempt=$attempt")
                return paste(service, node)
            }
            if (attempt < TextInserter.FindAttempts - 1) SystemClock.sleep(TextInserter.FindRetryMs)
        }
        Log.i(LOG_TAG, "image paste at ($x,$y): no editable node, windows=${TextInserter.describeWindows(service)}")
        return Result.NoTarget
    }

    private fun paste(service: AccessibilityService, node: AccessibilityNodeInfo): Result {
        if (!node.isFocused) {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            if (!node.isFocused) node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            // 焦点切换异步生效，等一拍再粘贴。
            SystemClock.sleep(TextInserter.FocusSettleMs)
        }
        repeat(2) { attempt ->
            if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) return Result.Pasted
            if (attempt == 0) SystemClock.sleep(TextInserter.PasteRetryMs)
        }
        Log.i(LOG_TAG, "image paste: ACTION_PASTE rejected")
        return Result.Failed
    }
}
