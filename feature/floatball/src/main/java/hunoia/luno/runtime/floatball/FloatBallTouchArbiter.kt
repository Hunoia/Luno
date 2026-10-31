package hunoia.luno.runtime.floatball

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import hunoia.luno.bridge.ui.DensityProvider

/**
 * 球的触摸仲裁：
 * - 按下后移动超过 touchSlop → **直接进入拖动**（无长按门槛，球立即跟手）；
 * - 一直没超 slop 且未到长按阈值 → 抬手点按；
 * - 一直没超 slop、按住到阈值 → 长按，随后的移动不再拖动；
 * - 拖动过 / 长按过 → 点按作废。
 * 这里收 2038 窗口的原始 MotionEvent 而非 Compose pointerInput，坐标用 `rawX/rawY`（屏幕绝对），
 * 拖不动窗口时 raw 坐标不会被窗口自己的位移污染。
 */
internal class FloatBallTouchArbiter(
    private val onSlideDragged: (dx: Float, dy: Float) -> Unit,
    private val onTapped: () -> Unit,
    private val onPressChanged: (pressed: Boolean) -> Unit,
    private val onDragEnded: () -> Unit,
    private val onLongPress: (() -> Unit)? = null,
    private val longPressMs: Long = 500L,
) {
    private val handler = Handler(Looper.getMainLooper())

    private var startX = 0f
    private var startY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var dragging = false
    private var longPressFired = false

    /** [cancel] 之后仍可能收到本次手势的 ACTION_UP：必须吞掉，不能补触发点按。 */
    private var canceled = false

    private val touchSlopPx: Float by lazy {
        DensityProvider.dp2px(hunoia.luno.config.model.FloatBallDefaults.TouchSlopDp).toFloat()
    }

    private val longPressRunnable = Runnable {
        if (!dragging && !longPressFired) {
            longPressFired = true
            onLongPress?.invoke()
        }
    }

    fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                startY = event.rawY
                currentX = startX
                currentY = startY
                dragging = false
                longPressFired = false
                canceled = false
                onPressChanged(true)
                handler.postDelayed(longPressRunnable, longPressMs)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (canceled) return true
                currentX = event.rawX
                currentY = event.rawY
                if (longPressFired) return true
                val dx = currentX - startX
                val dy = currentY - startY
                if (!dragging && (dx * dx + dy * dy) > touchSlopPx * touchSlopPx) {
                    dragging = true
                    handler.removeCallbacks(longPressRunnable)
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
                handler.removeCallbacks(longPressRunnable)
                // 长按打开面板会先走 cancel()：那之后到达的 UP 只收尾，不能再当成点按。
                if (!canceled) {
                    onPressChanged(false)
                    if (dragging) {
                        onDragEnded()
                    } else if (!longPressFired) {
                        onTapped()
                    }
                }
                dragging = false
                longPressFired = false
                canceled = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                onPressChanged(false)
                if (dragging) onDragEnded()
                dragging = false
                longPressFired = false
                canceled = false
                return true
            }

            else -> return false
        }
    }

    fun cancel() {
        handler.removeCallbacks(longPressRunnable)
        onPressChanged(false)
        if (dragging) onDragEnded()
        dragging = false
        longPressFired = false
        // 本次手势作废：若系统仍把 UP 送进来，不能再触发点按。
        canceled = true
    }
}
