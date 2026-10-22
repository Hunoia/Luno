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

/** 松手归格：带一点落定回压。 */
private val SettleSpring = spring<Offset>(dampingRatio = 0.70f, stiffness = 520f)

/**
 * 每项常驻的位置动画。base 只按 itemId 记忆一次，松手残差由 residual 单独
 * 承载并衰减到 0。落格时 base 直接对齐落格、residual 从「视觉位 − 落格基位」
 * 衰减，两者相加恒等于松手瞬间的视觉位；不能让 base 和 residual 叠加走两遍。
 */
@Composable
fun rememberItemPlacement(
    itemId: String,
    target: Offset,
    dragging: Boolean,
    glide: Offset?,
): Offset {
    val base = remember(itemId) {
        Animatable(target, Offset.VectorConverter)
    }
    LaunchedEffect(target, dragging) {
        if (!dragging) {
            if (glide != null) {
                base.snapTo(target)
            } else {
                // 只 animateTo，不要 snapTo：snapTo 是同步跳变，会把正在跑的
                // 插值直接拽到终点，整页让位就变成瞬移。
                base.animateTo(target, TargetSpring)
            }
        }
    }
    val residual = remember(itemId) {
        Animatable(Offset.Zero, Offset.VectorConverter)
    }
    LaunchedEffect(glide) {
        if (glide == null) {
            if (residual.value != Offset.Zero) residual.snapTo(Offset.Zero)
        } else {
            residual.snapTo(glide)
            residual.animateTo(Offset.Zero, SettleSpring)
        }
    }
    return base.value + residual.value
}
