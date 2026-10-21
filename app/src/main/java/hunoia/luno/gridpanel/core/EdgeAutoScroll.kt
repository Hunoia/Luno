package hunoia.luno.gridpanel.core

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 拖拽到可视区上下沿时的自动滚动：按越界深度定速；消费量要回写坐标系，
 * 否则手指不动时目标格不动、抬手 glide 差半格；到底保活（消费为 0 时 delay
 * 而非 break），再滑要立刻响应。重启判据只有 (ScrollState, 方向, 页)，
 * 同向按住不重启协程，深度变化只更新步长。
 */
@Stable
class EdgeAutoScroll(
    private val scope: CoroutineScope,
    private val state: GridPanelState,
    private val bandPx: Float,
    private val maxStepPx: Float,
) {
    private var job: Job? = null
    private var target: ScrollState? = null
    private var direction = 0
    private var page = -1
    private var currentStep = 0f

    /**
     * [pointerY] 是手指在可视区坐标里的 y（内容坐标先减去 scroll.value）。
     * 每帧都要调用（含位移为零的帧），否则按住不动时边缘滚动会停。
     */
    fun update(scroll: ScrollState, pointerY: Float, pageIndex: Int) {
        val viewport = scroll.viewportSize
        if (viewport <= 0 || bandPx <= 0f) {
            stop()
            return
        }
        val dir = when {
            pointerY < bandPx -> -1
            pointerY > viewport - bandPx -> 1
            else -> 0
        }
        if (dir == 0) {
            stop()
            return
        }
        val depth = if (dir < 0) {
            bandPx - pointerY
        } else {
            pointerY - (viewport - bandPx)
        }
        currentStep = maxStepPx * (depth / bandPx).coerceIn(0.25f, 1f)
        if (target === scroll && direction == dir && page == pageIndex) return
        job?.cancel()
        target = scroll
        direction = dir
        page = pageIndex
        job = scope.launch {
            while (true) {
                // dispatchRawDelta 正好返回实际消费量（被 0..maxValue 夹过后的值）。
                val consumed = scroll.dispatchRawDelta(direction * currentStep)
                if (consumed != 0f) {
                    state.onAutoScroll(Offset(0f, consumed))
                }
                // 每帧都要让出：dispatchRawDelta 是同步的，否则 while 会以协程调度
                // 速度把内容一口气滚到底。消费为 0（到底/到顶）也保活，回滑要立刻响应。
                delay(if (consumed != 0f) 16L else 32L)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        target = null
        direction = 0
        page = -1
        currentStep = 0f
    }
}
