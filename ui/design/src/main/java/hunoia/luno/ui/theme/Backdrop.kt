package hunoia.luno.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
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
 *
 * **0 档 = 关闭**：外观页的模糊滑杆左端是 0dp，此时**不组合 haze**，改画
 * [solidBackground] 实色底（不叠 mask）。半透明底离开模糊就是「糊没关、反而更花」，
 * 所以关闭态必须不透明；实色由调用方给（顶栏按收拢比例淡入、悬浮胶囊恒不透明）。
 */
fun Modifier.appHazeEffect(
    state: HazeState?,
    background: Color,
    blurRadius: Dp = HazeBlurRadius,
    progressive: HazeProgressive? = null,
    mask: Brush? = null,
    solidBackground: Color? = null,
): Modifier {
    if (state == null) return this
    if (blurRadius <= 0.dp) {
        // 实色条不需要羽化：硬边正是「关掉磨砂」后 M3 顶栏该有的样子。
        return this.background(solidBackground ?: background)
    }
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
            solidBackground = floatingSurfaceColor(),
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
 * 容器色阶梯（ui-guidelines §2.2「容器色阶」）：页 → 卡 → 抬升 → 浮层，
 * 亮暗两套都逐级**提亮**（暗色里提亮 = 抬升），只有 page 跟随主题的 `surface`。所有层级都从主题色板取，不新增色值；页面与 feature 只读 token，不直读
 * `colorScheme.surface*`（门禁 `raw-surface-color`）。
 *
 * 以前菜单用 `surfaceContainerHigh`（亮 92）比页面（94）还暗，下拉看起来是凹进去的：
 * 抬升层必须是全阶梯最亮，菜单 / 顶栏胶囊 / sheet 都归这一层。
 */
@Composable
@ReadOnlyComposable
fun pageSurfaceColor(): Color =
    if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

/** 卡片层：组卡 / 行 / 空态 / 卡内输入底。 */
@Composable
@ReadOnlyComposable
fun cardSurfaceColor(): Color =
    if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceBright
    }

/** 抬升层：顶栏收起底、顶栏图标胶囊、下拉菜单、sheet / dialog。 */
@Composable
@ReadOnlyComposable
fun raisedSurfaceColor(): Color =
    if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }

/**
 * 浮层：底栏胶囊 / 搜索胶囊 / 面板底栏。几何与 [raisedSurfaceColor] 同层，
 * 区别只在玻璃 alpha（[GlassCapsuleHazeAlpha]）与阴影；0 档模糊时用不透明版本。
 */
@Composable
@ReadOnlyComposable
fun floatingSurfaceColor(): Color = raisedSurfaceColor()

/**
 * 窗口级玻璃（快速搜索窗、浮卡）的底色：与 [floatingSurfaceColor] 同层。
 * 模糊开着时按玻璃 alpha 半透明（透出窗口背后），0 档关闭时直接不透明 ——
 * 半透明 + 不模糊会让窗口变成一层灰雾。
 */
@Composable
@ReadOnlyComposable
fun overlayGlassColor(): Color =
    if (HazeBlurRadius > 0.dp) {
        floatingSurfaceColor().copy(alpha = GlassCapsuleHazeAlpha)
    } else {
        floatingSurfaceColor()
    }

/** 轨道层：进度轨、chip 底、图表网格。 */
@Composable
@ReadOnlyComposable
fun trackSurfaceColor(): Color = MaterialTheme.colorScheme.surfaceContainerHighest

/** 次级层：图标占位、徽标底。 */
@Composable
@ReadOnlyComposable
fun subtleSurfaceColor(): Color = MaterialTheme.colorScheme.surfaceVariant

@Composable
@ReadOnlyComposable
fun hazeSurfaceColor(alpha: Float = TopBarHazeAlpha): Color =
    raisedSurfaceColor().copy(alpha = alpha)

