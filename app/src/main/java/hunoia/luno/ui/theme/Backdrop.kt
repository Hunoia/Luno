package hunoia.luno.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

val HazeBlurRadius: Dp = 25.dp

/** 模糊半径 token（旧 GlassBlurRadius 调用点同名兼容）。 */
val GlassBlurRadius: Dp = HazeBlurRadius

/** 顶栏 Haze 底色透明度：旧液态玻璃是透明容器＋采样糊，降低它去“奶白”。 */
const val TopBarHazeAlpha = 0.5f

/** 屏幕级 Haze 状态：内容层 hazeSource，TopBar/底栏 hazeEffect，共用同一实例。 */
@Composable
fun rememberAppHazeState(): HazeState = rememberHazeState()

/** 内容容器：作为模糊源。 */
fun Modifier.appHazeSource(state: HazeState): Modifier = hazeSource(state)

/**
 * 悬浮胶囊/顶栏的背后模糊（替代液态玻璃）。
 * 无 lens/vibrancy/highlight，只做 blur + 半透明底 + 可选羽化。
 *
 * 注意：[progressive] 只羽 blur 强度，[mask] 才羽整个 effect（含底色）。
 * 要还原旧 DstIn“糊＋底一起渐隐”，用 [mask]。
 */
fun Modifier.appHazeEffect(
    state: HazeState?,
    background: Color,
    blurRadius: Dp = HazeBlurRadius,
    progressive: HazeProgressive? = null,
    mask: Brush? = null,
): Modifier {
    if (state == null) return this
    return hazeEffect(state) {
        this.blurRadius = blurRadius
        backgroundColor = background
        this.progressive = progressive
        this.mask = mask
    }
}

/** 底部羽化蒙版：糊＋底一起从顶部全显渐隐到底部透明，还原旧 DstIn 渐隐。 */
@Composable
fun topBarMask(): Brush {
    val fade = remember {
        listOf(Color.White, Color.White.copy(alpha = 0f))
    }
    return remember(fade) {
        Brush.verticalGradient(colors = fade)
    }
}

@Composable
fun hazeSurfaceColor(alpha: Float = TopBarHazeAlpha): Color =
    MaterialTheme.colorScheme.surfaceContainer.copy(alpha = alpha)

