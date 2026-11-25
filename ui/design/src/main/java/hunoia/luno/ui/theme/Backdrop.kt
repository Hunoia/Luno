package hunoia.luno.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import hunoia.luno.config.model.ShadowStrength
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import hunoia.luno.ui.theme.CapsuleShape

val HazeBlurRadius: Dp get() = UiAppearance.floatingBlur

/**
 * 顶栏专用模糊半径 / 底色透明度：比悬浮胶囊更实，滚动内容几乎看不清，贴近 FlClash 磨砂
 * 顶栏（ui-guidelines §4.7 顶栏条目）。胶囊族仍用 [HazeBlurRadius] / [GlassCapsuleHazeAlpha]。
 * 三个值都可被用户覆盖（§2.5 玻璃组）。
 */
val TopBarBlurRadius: Dp get() = UiAppearance.topBarBlur
val TopBarHazeAlpha: Float get() = UiAppearance.topBarAlpha

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
    // Haze 2.x：effect 由「输入 + 模糊样式」组成，Backdrop 表示采当前窗口的 source 快照。
    // 样式 scope 里 blurRadius / backgroundColor / progressive / mask 都是函数，
    // 与入参同名，先取到局部值避免调用歧义。
    val radius = blurRadius
    val tint = background
    val blend = progressive
    val brush = mask
    return hazeBlur(
        HazeInput.Sources(state),
        HazeBlurStyle {
            blurRadius(radius)
            backgroundColor(tint)
            // 关掉 haze 2.1 默认的噪点（noiseFactor）：项目玻璃只做 blur + 半透明底 + 羽化，
            // 不留颗粒；否则顶栏未收起（blur=0、底 alpha=0）时仍有一层颗粒。
            noiseFactor(0f)
            progressive(blend)
            mask(brush)
        },
    )
}

/** 玻璃胶囊底色透明度：悬浮胶囊共用（与主页悬浮底栏同一套，自身不再叠底色，双层会发白）。 */
val GlassCapsuleHazeAlpha: Float get() = UiAppearance.floatingAlpha

/** 玻璃阴影强度 → 阴影参数（§2.5 悬浮玻璃组的「阴影」档）。 */
private fun ShadowStrength.shadowRadius(): Dp = when (this) {
    ShadowStrength.None -> 0.dp
    ShadowStrength.Subtle -> TileInnerPadding * 1.5f
    ShadowStrength.Strong -> TileInnerPadding * 3f
}

private fun ShadowStrength.shadowAlpha(dark: Boolean): Float = when (this) {
    ShadowStrength.None -> 0f
    ShadowStrength.Subtle -> if (dark) 0.12f else 0.06f
    ShadowStrength.Strong -> if (dark) 0.24f else 0.14f
}

/**
 * 悬浮玻璃胶囊**装饰背景层**（连续曲率）：阴影 + 裁形 + 毛玻璃 + 发丝描边，**不含内容与内边距**。
 * 供内容需要溢出不裁切的场景（如底栏内嵌索引条，其按压气泡朝上溢出胶囊）：
 * 背景层负责裁切与玻璃视觉，内容层叠加其上且不被裁。
 * [glassCapsule] 委托本函数 + 内边距；调用 [glassCapsule] 即内容可裁的常规场景。
 */
@Composable
fun Modifier.glassCapsuleBackground(hazeState: HazeState?): Modifier {
    val capsuleShape = CapsuleShape
    val shadow = UiAppearance.floatingShadow
    val dark = isSystemInDarkTheme()
    return dropShadow(
        shape = capsuleShape,
        shadow = Shadow(
            radius = shadow.shadowRadius(),
            color = Color.Black,
            alpha = shadow.shadowAlpha(dark),
        ),
    )
        // clip 在 haze 之外：方形糊输出裁成连续曲率胶囊（只裁绘制，不影响采样）。
        .clip(capsuleShape)
        // 底色对齐 FlClash 浮动层（亮 surfaceBright / 暗 surfaceContainerHigh）：底栏胶囊
        // 与列表行同一层灰，看起来是列表的一部分而不是另一张浮卡；不描边（边缘靠阴影与底色差表达）。
        .appHazeEffect(
            hazeState,
            floatingSurfaceColor().copy(alpha = GlassCapsuleHazeAlpha),
        )
}

/**
 * 悬浮玻璃胶囊容器（连续曲率）：阴影 + 裁形 + 毛玻璃 + 发丝描边 + 内边距一条龙。
 * hazeEffect 必须画在 hazeSource 的**兄弟**节点上（调用方保证层级，见 ui-guidelines §5）。
 * 动作选择页底栏与页面级悬浮搜索胶囊共用，两处观感由同一份代码渲染。
 * 内容需要溢出不裁切时（底栏内嵌索引条）改用 [glassCapsuleBackground] 作背景层、
 * 内容层单独叠加；否则调用本函数即可。
 */
@Composable
fun Modifier.glassCapsule(hazeState: HazeState?): Modifier =
    glassCapsuleBackground(hazeState).padding(TileInnerPadding)

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

/**
 * FlClash `SurfaceLevel` 明暗映射（`prism_ui/surface.dart`）：浮动层（底栏 / 搜索胶囊）
 * 亮色 = `surfaceBright`(card) / 暗色 = `surfaceContainerHigh`(block)。
 */
@Composable
fun floatingSurfaceColor(): Color =
    if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceBright
    }

/** 抬升层（顶栏）：亮色 = `surfaceBright`(card) / 暗色 = `surfaceContainer`。 */
@Composable
fun raisedSurfaceColor(): Color =
    if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceBright
    }

/** 页面底（FlClash `page`）：亮色 = `surfaceContainer` / 暗色 = `surface`。 */
@Composable
fun pageSurfaceColor(): Color =
    if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

@Composable
fun hazeSurfaceColor(alpha: Float = TopBarHazeAlpha): Color =
    raisedSurfaceColor().copy(alpha = alpha)

