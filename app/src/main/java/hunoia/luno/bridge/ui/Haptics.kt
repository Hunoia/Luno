package hunoia.luno.bridge.ui

import android.view.HapticFeedbackConstants
import android.view.View

/** 面板/球窗的触感反馈：走 view 层常量（服务进程托管的 ComposeView 没有 LocalHapticFeedback 宿主）。 */
fun View.hapticTap() = performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

fun View.hapticLongPress() = performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

fun View.hapticMove() = performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
