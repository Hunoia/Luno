package hunoia.luno.gridpanel.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset

/** 让位动画：干脆、约 220ms 落定；参数偏软会显得「先延迟一下再飘过去」。 */
private val TargetSpring = spring<Offset>(dampingRatio = 0.82f, stiffness = 480f)

/**
 * 每项常驻的位置动画。
 *
 * 拖拽中 base 冻结在拖起格：被拖项的视觉位置由渲染层用 `base + dragVisual`
 * 拼出来。松手后的「视觉位 → 落格位」残差**不在这里做**：残差由
 * [hunoia.luno.gridpanel.core.GridPanelState] 以普通状态保存并逐帧衰减，
 * 渲染层直接画 `target + glide`。用 Animatable 承载残差需要在 LaunchedEffect
 * 里 snapTo(glide)，首帧会先画出「base(旧格) + residual(0)」再跳到手指位置，
 * 表现为松手瞬间闪回原位。
 */
@Composable
fun rememberItemPlacement(
    itemId: String,
    target: Offset,
    dragging: Boolean,
    /** 该项正在松手归格：此时把 base 直接对齐落格（视觉由 glide 单独叠加）。 */
    gliding: Boolean,
): Offset {
    val base = remember(itemId) {
        Animatable(target, Offset.VectorConverter)
    }
    LaunchedEffect(target, dragging) {
        if (!dragging) {
            if (gliding) {
                base.snapTo(target)
            } else {
                // 只 animateTo，不要 snapTo：snapTo 是同步跳变，会把正在跑的
                // 插值直接拽到终点，整页让位就变成瞬移。
                base.animateTo(target, TargetSpring)
            }
        }
    }
    return base.value
}
