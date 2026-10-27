package hunoia.luno.runtime.floatball

import android.view.MotionEvent
import hunoia.luno.bridge.ui.DensityProvider

/**
 * 球的触摸仲裁：
 * - 按下后移动超过 touchSlop → **直接进入拖动**（无长按门槛，球立即跟手）；
 * - 一直没超 slop 就抬手 → 点按；
 * - 拖动过 → 点按作废。
 * 这里收 2038 窗口的原始 MotionEvent 而非 Compose pointerInput，坐标用 `rawX/rawY`（屏幕绝对），
 * 拖不动窗口时 raw 坐标不会被窗口自己的位移污染。
 */
internal class FloatBallTouchArbiter(
    private val onSlideDragged: (dx: Float, dy: Float) -> Unit,
    private val onTapped: () -> Unit,
    private val onPressChanged: (pressed: Boolean) -> Unit,
    private val onDragEnded: () -> Unit,
) {
    private var startX = 0f
    private var startY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var dragging = false

    private val touchSlopPx: Float by lazy {
        DensityProvider.dp2px(hunoia.luno.config.model.FloatBallDefaults.TouchSlopDp).toFloat()
    }

    fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                startY = event.rawY
                currentX = startX
                currentY = startY
                dragging = false
                onPressChanged(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                currentX = event.rawX
                currentY = event.rawY
                val dx = currentX - startX
                val dy = currentY - startY
                if (!dragging && (dx * dx + dy * dy) > touchSlopPx * touchSlopPx) {
                    dragging = true
                }
                if (dragging) {
                    // 每帧上报**本帧增量**（窗口侧做累加）。基准不能只在进入拖动时
                    // 记一次：那会让每帧都报「自进入点起的总距离」，窗口侧再累加
                    // 就是平方级膨胀 —— 真机上表现为球狂奔、松手弹到屏幕边。
                    startX = currentX
                    startY = currentY
                    onSlideDragged(dx, dy)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                onPressChanged(false)
                if (dragging) onDragEnded() else onTapped()
                dragging = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                onPressChanged(false)
                if (dragging) onDragEnded()
                dragging = false
                return true
            }

            else -> return false
        }
    }

    fun cancel() {
        onPressChanged(false)
        if (dragging) onDragEnded()
        dragging = false
    }
}
