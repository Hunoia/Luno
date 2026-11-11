package hunoia.luno.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import hunoia.luno.ui.theme.PressFeedbackDebounceMillis
import hunoia.luno.ui.theme.PressScaleCompactThresholdDp
import hunoia.luno.ui.theme.PressScaleCompact
import hunoia.luno.ui.theme.PressScaleLarge
import kotlinx.coroutines.delay

/**
 * 点击反馈：**按压缩放**，全站统一，不用 ripple（ui-guidelines §5.3）。
 *
 * 为什么不用 ripple：ripple 按节点矩形绘制，没套 `clip` 就是方角高亮（面板里踩过）；
 * 而按压缩放与容器形状无关、不需要每个调用点记得 clip，也符合"面板 / 浮层不做整层高亮"的口径。
 *
 * 两档按**实际尺寸**自动选：最小边 ≤ [PressScaleCompactThresholdDp] 的紧凑目标（图标按钮、
 * 圆点、chip）缩 [PressScaleCompact]，其余（行、卡、键）缩 [PressScaleLarge]。
 * 调用点不需要也不应该自己传数值。
 *
 * 防抖：[PressFeedbackDebounceMillis] 内松手不缩放——列表里快速滑动（按下即被滚动抢走）
 * 不会闪一下再回弹。
 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    debounceMillis: Long = PressFeedbackDebounceMillis,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    var engaged by remember(interactionSource) { mutableStateOf(false) }
    LaunchedEffect(pressed, debounceMillis) {
        if (pressed) {
            if (debounceMillis > 0) delay(debounceMillis)
            engaged = true
        } else {
            engaged = false
        }
    }
    // 只动画 0→1 的进度：目标缩放依赖实测尺寸，放在绘制阶段取，避免测量期间回写状态。
    val progress by animateFloatAsState(
        targetValue = if (engaged) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "pressScale",
    )
    val compactThresholdPx = with(LocalDensity.current) { PressScaleCompactThresholdDp.toPx() }
    return graphicsLayer {
        val target = if (size.minDimension <= compactThresholdPx) PressScaleCompact else PressScaleLarge
        val scale = 1f + (target - 1f) * progress
        scaleX = scale
        scaleY = scale
    }
}

/**
 * [Modifier.clickable] 的替代：同样的点击语义 + 按压缩放，**不画 ripple**（`indication = null`）。
 *
 * 全站的可点条目一律用它；需要长按的用 [pressCombinedClickable]。
 * `onClick` 放最后，调用点可写 `Modifier.pressClickable { ... }`。
 */
@Composable
fun Modifier.pressClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    debounceMillis: Long = PressFeedbackDebounceMillis,
    onClick: () -> Unit,
): Modifier = pressScale(interactionSource, debounceMillis)
    .clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onClick = onClick,
    )

/** [Modifier.combinedClickable] 的替代：点击 + 长按，按压缩放，不画 ripple。 */
@Composable
fun Modifier.pressCombinedClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    debounceMillis: Long = PressFeedbackDebounceMillis,
    onClick: () -> Unit,
): Modifier = pressScale(interactionSource, debounceMillis)
    .combinedClickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onLongClickLabel = onLongClickLabel,
        onLongClick = onLongClick,
        onClick = onClick,
    )

/** 全局关掉 ripple 后给仍然会走 `LocalIndication` 的组件用的空指示（不画任何东西）。 */
val NoOpIndication: Indication = object : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): androidx.compose.ui.node.DelegatableNode =
        object : androidx.compose.ui.Modifier.Node() {}

    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = System.identityHashCode(this)
}
