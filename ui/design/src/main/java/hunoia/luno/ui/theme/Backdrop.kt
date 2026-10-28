package hunoia.luno.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

val HazeBlurRadius: Dp = 25.dp

/** 顶栏 Haze 底色透明度；过高会发奶白。 */
const val TopBarHazeAlpha = 0.5f

/** 玻璃胶囊描边透明度：只补轮廓，不抢内容。 */
const val GlassOutlineAlpha = 0.35f

/** 屏幕级 Haze 状态：内容层 hazeSource，TopBar/底栏 hazeEffect，共用同一实例。 */
@Composable
fun rememberAppHazeState(): HazeState = rememberHazeState()

fun Modifier.appHazeSource(state: HazeState): Modifier = hazeSource(state)

/**
 * 悬浮胶囊/顶栏的背后模糊：只做 blur + 半透明底 + 可选羽化，
 * 无 lens/vibrancy/highlight。
 *
 * [progressive] 只羽 blur 强度，[mask] 才羽整个 effect（含底色）。
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

/**
 * 玻璃胶囊发丝描边：Haze 叠在滚动内容上时边缘容易糊掉，补一圈极淡的轮廓。
 * 主页底栏与动作选择页底栏共用，保证两颗胶囊观感一致。
 */
@Composable
fun Modifier.appGlassOutline(shape: Shape): Modifier = border(
    width = 1.dp,
    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = GlassOutlineAlpha),
    shape = shape,
)

/** 玻璃胶囊底色透明度：悬浮胶囊共用（与主页悬浮底栏同一套，自身不再叠底色，双层会发白）。 */
const val GlassCapsuleHazeAlpha = 0.65f

/**
 * 悬浮玻璃胶囊容器（CircleShape）：阴影 + 裁形 + 毛玻璃 + 发丝描边 + 内边距一条龙。
 * hazeEffect 必须画在 hazeSource 的**兄弟**节点上（调用方保证层级，见 ui-guidelines §5）。
 * 动作选择页底栏与页面级悬浮搜索胶囊共用，两处观感由同一份代码渲染。
 */
@Composable
fun Modifier.glassCapsule(hazeState: HazeState?): Modifier {
    return dropShadow(
        shape = CircleShape,
        shadow = Shadow(
            radius = TileInnerPadding * 2.5f,
            color = Color.Black,
            alpha = if (isSystemInDarkTheme()) 0.2f else 0.1f,
        ),
    )
        // clip 在 haze 之外：方形糊输出裁成胶囊（只裁绘制，不影响采样）。
        .clip(CircleShape)
        // 底色/透明度对齐主页底栏；用 surfaceContainerHighest 而不是 surfaceContainer，
        // 否则叠在列表上过淡，看起来像内容自己浮着、没有胶囊容器。
        .appHazeEffect(
            hazeState,
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = GlassCapsuleHazeAlpha),
        )
        .appGlassOutline(CircleShape)
        .padding(TileInnerPadding)
}

/** 底部羽化蒙版：糊＋底一起从顶部全显渐隐到底部透明。 */
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

