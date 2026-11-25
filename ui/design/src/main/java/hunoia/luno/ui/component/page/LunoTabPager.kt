package hunoia.luno.ui.component.page

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import hunoia.luno.ui.theme.MotionPageEnter
import hunoia.luno.ui.theme.MotionPageExit
import hunoia.luno.ui.theme.MotionSwapOut

/**
 * 一级 tab 的切换容器：方向感知（序号变大从左 / 变小从右，RTL 镜像），
 * 进 / 退分别走 `MotionPageEnter` / `MotionPageExit`，不做尺寸过渡。
 *
 * 只负责切页动画，不持有选中态：选中态仍由页面（如 HomeScreen 的 `mainTab`）持有。
 */
@Composable
fun <T> LunoTabPager(
    tabs: List<T>,
    selected: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    AnimatedContent(
        targetState = selected,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            val forward = tabs.indexOf(targetState) >= tabs.indexOf(initialState)
            val direction = when {
                rtl -> if (forward) -1f else 1f
                forward -> 1f
                else -> -1f
            }
            val enter = slideInHorizontally(
                animationSpec = tween(MotionPageEnter, easing = FastOutSlowInEasing),
            ) { full -> (full * direction / 6f).toInt() } +
                fadeIn(animationSpec = tween(MotionPageEnter))
            val exit = slideOutHorizontally(
                animationSpec = tween(MotionPageExit, easing = FastOutSlowInEasing),
            ) { full -> (-full * direction / 6f).toInt() } +
                fadeOut(animationSpec = tween(MotionSwapOut))
            enter togetherWith exit
        },
        contentKey = { it },
        label = "lunoTabPager",
    ) { tab ->
        content(tab)
    }
}