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
 * 拖拽到可视区上下沿时的自动滚动。
 *
 * 三个必须同时成立的点（旧实现只做了第一条）：
 * 1. **按越界深度定速**：刚进边带慢、压到底快，不是恒定 9dp/帧；
 * 2. **消费量回写坐标系**：滚了多少就补偿多少 dragVisual，否则手指不动时
 *    目标格不动（观感是卡住），抬手按旧坐标算 glide 会差半格；
 * 3. **到底保活**：消费为 0 时 delay 而不是 break —— 到底后手指不离开再滑一次
 *    必须立刻响应，旧实现 break 后再也不会重启。
 *
 * 重启判据只有 (ScrollState, 方向, 页) 三元组：同一方向持续按住不重启协程，
 * 否则滚动会被打断成一顿一顿的。深度变化只更新步长，不重启。
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
     * [pointerY] 是手指在**可视区**坐标里的 y（内容坐标先减去 scroll.value）。
     * 每帧调用（包括位移为零的帧），否则按住不动时边缘滚动会停。
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
                // **每帧都要让出**：dispatchRawDelta 是同步的，不让出的话 while
                // 会以协程调度速度把内容一口气滚到底，观感就是「直接跳到底」。
                // 消费为 0（到底/到顶）也同样保活：手指不离开再往回滑要立刻响应。
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
