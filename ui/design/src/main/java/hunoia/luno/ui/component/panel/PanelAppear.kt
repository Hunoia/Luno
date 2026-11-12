package hunoia.luno.ui.component.panel

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import hunoia.luno.ui.theme.MotionSpec
import hunoia.luno.ui.theme.PanelAppearScale
import hunoia.luno.ui.theme.PanelFadeTailFraction
import kotlin.math.roundToInt

/**
 * 面板家族的出现 / 收口几何（ui-guidelines §3「面板」、§5.1）：绕触发点从
 * [PanelAppearScale] 展开到 1、沿 [slideDirection] 偏移 [slidePx]、alpha 只在首尾
 * [PanelFadeTailFraction] 收放。
 *
 * 网格面板、快捷启动、动作面板共用这一份。此前三处各写一套（0.25 / 0.92 / 纯淡出），
 * 同一类面板三种手感；几何与 alpha 的语义都收在这里，调用点只提供进度、锚点与位移量。
 *
 * [progress] 用 lambda 传入：它会在图形层里被读，动画帧只重画本层，不重组整个面板。
 * [slideForSize] 返回**进度 0 时的完整位移**（px，含方向与距离）：面板向触发点偏移，
 * 进度到 1 归零。方向和距离都依赖自身尺寸时（如径向菜单以触发点为中心）在 lambda 里算。
 * [fade] = false 时 alpha 交给 `AnimatedVisibility` 的 [panelFadeIn] / [panelFadeOut]
 * 驱动——不能两层同时淡，否则曲线上叠一份 double-fade。
 *
 * **曲线**：时长 / 缓动仍是家族 spec（`panelSpec()`，`FastOutSlowIn`），缩放再做一次「先快后慢」
 * 整形（[scaleAt]）：从触发点弹出时前段就有明显位移，尾段收敛；不做 overshoot（大面积面板回弹
 * 会把边缘糊掉）。退场读同一条函数的反向进度，进 / 退严格对称。
 */
fun Modifier.panelAppear(
    progress: () -> Float,
    originForSize: (Size) -> TransformOrigin,
    slideForSize: (Size) -> Offset = { Offset.Zero },
    hidden: Boolean = false,
    fade: Boolean = true,
): Modifier = graphicsLayer {
    val p = progress().coerceIn(0f, 1f)
    val remaining = 1f - p
    alpha = when {
        hidden -> 0f
        fade -> (p / PanelFadeTailFraction).coerceIn(0f, 1f)
        else -> 1f
    }
    val layerSize = Size(size.width, size.height)
    transformOrigin = originForSize(layerSize)
    val scale = PanelAppearScale + (1f - PanelAppearScale) * scaleAt(p)
    scaleX = scale
    scaleY = scale
    val slide = if (hidden) Offset.Zero else slideForSize(layerSize) * remaining
    translationX = slide.x
    translationY = slide.y
}

/**
 * 缩放整形：在家族缓动之上再「先快后慢」一次（ease-out 二次）。
 *
 * 进度本身已走 `FastOutSlowIn`，若直接线性映射到缩放，前半段几乎是「不动」的，
 * 大面积面板看起来像「先等一拍再展开」；这里让前段吃掉大部分缩放行程，尾段收敛。
 * 退场读反向进度，因此先快速缩回、末段减速，与进场对称。
 */
private fun scaleAt(progress: Float): Float {
    val remaining = 1f - progress
    return 1f - remaining * remaining
}

/**
 * 面板退场拆窗前的最后一帧屏障（ui-guidelines §5.1）：`Animatable.animateTo` 的续体在末帧
 * **提交之前**恢复，退场播完直接拆窗 / 收起组合会把收尾那一帧裁掉——真机表现是「缩到最后
 * 直接关」「淡出没播完就没了」。所有 `animateTo(0f, panelSpec...)` 之后立刻拆窗的路径都要先
 * await 这一帧。`panelTeardownDelayMillis` 那套「等时长 + 40ms」是同一个问题的定时器版本，
 * 能用帧信号的地方优先用这里。
 */
suspend fun awaitPanelExitFrame() {
    withFrameNanos { }
}

/**
 * 面板进场 alpha：首段 [PanelFadeTailFraction] 里从 0 到 1，其余保持不透明。
 * 与 [Modifier.panelAppear] 的 `fade = true` 同一条曲线，供 `AnimatedVisibility` 载体使用。
 */
fun panelFadeIn(spec: MotionSpec): EnterTransition = fadeIn(panelTailSpec(spec, entering = true))

/** 面板退场 alpha：前 1 - [PanelFadeTailFraction] 保持不透明，末段收掉。 */
fun panelFadeOut(spec: MotionSpec): ExitTransition = fadeOut(panelTailSpec(spec, entering = false))

/**
 * alpha 的尾段曲线（与 [Modifier.panelAppear] 的 `progress / PanelFadeTailFraction` 等价）：
 * 面板以缩放 + 位移为主体，alpha 只用来避免移窗 / 首帧的硬切。
 */
private fun panelTailSpec(spec: MotionSpec, entering: Boolean): FiniteAnimationSpec<Float> {
    val ramp = (spec.durationMillis * PanelFadeTailFraction).roundToInt().coerceAtLeast(1)
    return keyframes {
        durationMillis = spec.durationMillis
        if (entering) {
            0f at 0
            1f at ramp using spec.easing
        } else {
            1f at 0
            1f at (spec.durationMillis - ramp).coerceAtLeast(1)
            0f at spec.durationMillis using spec.easing
        }
    }
}
