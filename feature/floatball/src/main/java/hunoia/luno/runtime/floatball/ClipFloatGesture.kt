package hunoia.luno.runtime.floatball

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.compose.ui.geometry.Offset
import hunoia.luno.bridge.ui.hapticLongPress
import kotlin.math.hypot

/**
 * 剪贴板悬浮窗的手势仲裁：只用原始 `MotionEvent`（与悬浮球同一套思路）。
 *
 * 不画任何示意的缩放圆点，命中区由起手位置决定，一旦确定就不在拖动中途改语义：
 *
 * - 起手在四角 [CORNER_DP] 半径内 → 调尺寸（四角自由），其余 → 移窗；
 * - 位移不超过 touchSlop 就抬手 → 点按（展开 / 收起动作条，停靠态下是恢复）；
 * - 按住 [LONG_PRESS_MS] 不动 → 提起态；此后位移超过 8dp → 卡片跟手移动，
 *   松手投递；抬手不动 → 动作条；停靠态不进入提起态；
 * - 第二根手指落下 → 等比缩放，并中止长按 / 移窗 / 调尺寸 / 投递。
 */
internal class ClipFloatGesture(
    private val view: View,
    handlers: Handlers,
) {

    /**
     * 全部回调集中成一份，由卡片每次重组整体换掉。
     *
     * 单独记住某个 lambda 会冻住首次组合的旧值（面板捏合踩过：预览用旧基准算目标列数，
     * 结果中途跳回持久化值），所以这里只允许整份替换。
     */
    data class Handlers(
        val resizable: Boolean,
        /** 停靠态只露一条：不进入长按提起 / 投递。 */
        val liftable: Boolean,
        /** 动作条展开时，底部那一块归 Compose 按钮，仲裁器整段让开。 */
        val barOpen: Boolean,
        val onTap: () -> Unit,
        val onLiftStart: () -> Unit,
        val onLiftCancel: () -> Unit,
        val onLiftRelease: (Offset) -> Unit,
        val onLiftMoveStart: (Offset) -> Unit,
        val onLiftMove: (Offset) -> Unit,
        val onMoveStart: (Offset) -> Unit,
        val onMoveTo: (Offset) -> Unit,
        val onMoveEnd: () -> Unit,
        val onResizeStart: (Int, Offset) -> Unit,
        val onResizeTo: (Int, Offset) -> Unit,
        val onResizeEnd: () -> Unit,
        val onPinch: (Float) -> Unit,
        val onPinchEnd: () -> Unit,
    )

    private var handlers: Handlers = handlers

    fun update(next: Handlers) {
        handlers = next
    }

    private val hapticView = view
    private val handler = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(view.context).scaledTouchSlop.toFloat()
    private val cornerPx = CORNER_DP * view.resources.displayMetrics.density
    private val dragOutSlop = LIFT_DRAG_DP * view.resources.displayMetrics.density

    private var startRaw = Offset.Zero
    private var currentRaw = Offset.Zero
    private var lastSpan = 0f
    private var corner = CORNER_NONE
    private var ignoring = false
    private var dragging = false
    private var resizing = false
    private var lifted = false
    private var lifting = false
    private var pinching = false

    private val longPressRunnable = Runnable {
        if (!dragging && !resizing && !pinching) {
            lifted = true
            hapticView.hapticLongPress()
            handlers.onLiftStart()
        }
    }

    fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                ignoring = handlers.barOpen && event.y >= view.height - barBlockPx()
                if (ignoring) return false
                startRaw = Offset(event.rawX, event.rawY)
                currentRaw = startRaw
                dragging = false
                resizing = false
                lifted = false
                lifting = false
                pinching = false
                corner = if (handlers.resizable) cornerAt(event.x, event.y) else CORNER_NONE
                handler.removeCallbacks(longPressRunnable)
                if (corner != CORNER_NONE) {
                    // 命中角就立刻进入缩放：起点尺寸要在第一次 MOVE 之前拿到。
                    resizing = true
                    handlers.onResizeStart(corner, startRaw)
                } else if (handlers.liftable) {
                    handler.postDelayed(longPressRunnable, LONG_PRESS_MS)
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (ignoring) return false
                if (event.pointerCount >= 2) {
                    handler.removeCallbacks(longPressRunnable)
                    if (lifting || lifted) {
                        lifting = false
                        lifted = false
                        handlers.onLiftCancel()
                    }
                    if (dragging) {
                        dragging = false
                        handlers.onMoveEnd()
                    }
                    if (resizing) {
                        resizing = false
                        handlers.onResizeEnd()
                    }
                    pinching = true
                    lastSpan = span(event)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (ignoring) return false
                if (pinching) {
                    val current = span(event)
                    if (lastSpan > 0f && current > 0f) handlers.onPinch(current / lastSpan)
                    if (current > 0f) lastSpan = current
                    return true
                }
                currentRaw = Offset(event.rawX, event.rawY)
                if (lifting) {
                    handlers.onLiftMove(currentRaw)
                    return true
                }
                if (lifted) {
                    if ((currentRaw - startRaw).getDistance() > dragOutSlop) {
                        // 越过阈值的那一帧进入投递移动：卡片本身跟手，松手投递。
                        // 用按下点做 move 基准，第一帧不会补跳阈值那一段位移。
                        lifted = false
                        lifting = true
                        handlers.onLiftMoveStart(startRaw)
                        handlers.onLiftMove(currentRaw)
                    }
                    return true
                }
                if (resizing) {
                    handlers.onResizeTo(corner, currentRaw)
                    return true
                }
                if (dragging) {
                    handlers.onMoveTo(currentRaw)
                    return true
                }
                if ((currentRaw - startRaw).getDistance() > slop) {
                    handler.removeCallbacks(longPressRunnable)
                    dragging = true
                    // 越过阈值的当前点作为起点，窗口不会补跳 slop 的位移。
                    handlers.onMoveStart(currentRaw)
                    handlers.onMoveTo(currentRaw)
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (ignoring) return false
                if (pinching && event.pointerCount <= 2) {
                    pinching = false
                    handlers.onPinchEnd()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (ignoring) {
                    ignoring = false
                    return false
                }
                handler.removeCallbacks(longPressRunnable)
                val cancelled = event.actionMasked == MotionEvent.ACTION_CANCEL
                when {
                    pinching -> {
                        pinching = false
                        handlers.onPinchEnd()
                    }
                    lifting -> {
                        lifting = false
                        if (cancelled) handlers.onLiftCancel() else handlers.onLiftRelease(currentRaw)
                    }
                    lifted -> {
                        lifted = false
                        handlers.onLiftCancel()
                        if (!cancelled) handlers.onTap()
                    }
                    resizing -> {
                        resizing = false
                        handlers.onResizeEnd()
                    }
                    dragging -> {
                        dragging = false
                        handlers.onMoveEnd()
                    }
                    !cancelled &&
                        (currentRaw - startRaw).getDistance() <= slop -> handlers.onTap()
                }
                reset()
            }
        }
        return true
    }

    private fun reset() {
        startRaw = Offset.Zero
        currentRaw = Offset.Zero
        lastSpan = 0f
        corner = CORNER_NONE
        dragging = false
        resizing = false
        lifted = false
        lifting = false
        pinching = false
    }

    /** 起手位置落在哪个角的命中区内；四角优先级高于移窗，但只在起手时判定一次。 */
    private fun cornerAt(x: Float, y: Float): Int {
        val w = view.width.toFloat()
        val h = view.height.toFloat()
        if (w <= 0f || h <= 0f) return CORNER_NONE
        val nearLeft = x <= cornerPx
        val nearRight = x >= w - cornerPx
        val nearTop = y <= cornerPx
        val nearBottom = y >= h - cornerPx
        return when {
            nearLeft && nearTop -> CORNER_TL
            nearRight && nearTop -> CORNER_TR
            nearLeft && nearBottom -> CORNER_BL
            nearRight && nearBottom -> CORNER_BR
            else -> CORNER_NONE
        }
    }

    /** 动作条占用的底部高度：一行 48dp 命中区 + 上下各 8dp 边距。 */
    private fun barBlockPx(): Float = BAR_RESERVED_DP * view.resources.displayMetrics.density

    private fun span(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return hypot(dx, dy)
    }
}

internal const val CORNER_NONE = -1
internal const val CORNER_TL = 0
internal const val CORNER_TR = 1
internal const val CORNER_BL = 2
internal const val CORNER_BR = 3

/** 四角缩放的命中半径；不画任何标记，靠起手位置区分移窗。 */
private const val CORNER_DP = 36f

/** 提起后要移动这么远才进入投递移动，否则抬手就是动作条。 */
private const val LIFT_DRAG_DP = 8f

private const val BAR_RESERVED_DP = 64f

private const val LONG_PRESS_MS = 400L
