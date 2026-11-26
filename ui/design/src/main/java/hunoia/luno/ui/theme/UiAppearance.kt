package hunoia.luno.ui.theme

import androidx.compose.material3.Shapes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import hunoia.luno.config.model.AlertPalette
import hunoia.luno.config.model.GlassSetting
import hunoia.luno.config.model.ShapeMode
import hunoia.luno.config.model.ShapeSetting
import hunoia.luno.config.model.ShadowStrength
import hunoia.luno.config.model.UiAppearancePresets
import hunoia.luno.config.model.UiAppearanceSettings
import sv.lib.squircleshape.CornerSmoothing
import sv.lib.squircleshape.SquircleShape
import kotlin.math.floor

/**
 * 形状角色（ui-guidelines §2.1.1）的**组件族**细分。
 *
 * 一个 [ShapeRole] 只覆盖同一高度族的容器：预算 = 该族最矮容器高，半径档位由它推出
 * （r ≤ h / (2(1+s))，§2.1.2 第 3 条）。以前所有容器共用一个 `LargeShape`，44dp 工具条
 * 与 112dp 卡同桌，改一组半径必然越界；拆细后每组各自合法。
 */
enum class ShapeRole(
    /** 该族最矮容器高；胶囊用百分比半径，预算无意义记 0。 */
    val budgetDp: Int,
    /** 同族内派生档的半径偏移（中档 +4、选中块 −8，保持同心 / 层级关系）。 */
    val radiusOffsetDp: Int = 0,
) {
    /** 列表与卡：行、组卡、分段组、输入框、空态、横幅。 */
    List(56),

    /** 菜单项与 sheet 按钮（48dp）。 */
    Menu(48),

    /** 紧凑工具条（标注页，抬到 48dp 后与菜单同族）。 */
    Toolbar(48),

    /** 小元件（图标底 / 缩略图 / badge，32dp）。 */
    Compact(32),

    /** 小元件中档（小档 + 4，用于 48dp 级图标格）。 */
    CompactMedium(48, radiusOffsetDp = 4),

    /** 菜单选中高亮块：容器圆角 − 8dp 边距（同心，§4.1）。 */
    MenuInner(48, radiusOffsetDp = -8),

    /** 弹层与大卡（sheet / dialog / OCR 卡，112dp）。 */
    Sheet(112),

    /** Sheet 顶部圆角、底部直角。 */
    SheetTop(112),

    /** 胶囊：百分比半径，随高度自适应。 */
    Capsule(0),
}

/** 容器平滑度（百分比）：连续 48 / 标准 0。 */
internal fun containerSmoothing(mode: ShapeMode): Int =
    if (mode == ShapeMode.Standard) CornerSmoothing.None else CornerSmoothing.Medium

/** 胶囊平滑度：连续 20 / 标准 0（标准 = 半圆端，仍是胶囊）。 */
internal fun capsuleSmoothing(mode: ShapeMode): Int =
    if (mode == ShapeMode.Standard) CornerSmoothing.None else CornerSmoothing.Small

/** 该角色在当前形态下的合法半径档位（升序）。 */
fun ShapeRole.radiusOptions(mode: ShapeMode): List<Int> {
    if (this == ShapeRole.Capsule) return emptyList()
    val smoothing = containerSmoothing(mode) / 100f
    val cap = floor(budgetDp.toFloat() / (2f * (1f + smoothing))).toInt()
    val all = if (mode == ShapeMode.Standard) {
        UiAppearancePresets.StandardRadius
    } else {
        UiAppearancePresets.ContinuousRadius
    }
    return all.filter { it <= cap }
}

/**
 * 进程级外观设置。App 启动时订阅配置流写入；形状与尺寸 token 直接读这里。
 *
 * 读点分两类：
 * - **形状**：在 `createOutline`（layout 阶段）读 [settings]，订阅随布局走，改设置自动重排；
 * - **尺寸 / 玻璃**：`Dimension.kt` 的 token 改成 `get()`，组合期读 snapshot state，自动重组。
 */
object UiAppearance {

    var settings: UiAppearanceSettings by mutableStateOf(UiAppearanceSettings())

    /**
     * 到达提示素材库里的用户自定义色板（由 `App` 订阅配置流写入）。
     *
     * 主题只存 id、不复制定义：色板编辑立即反映到主题；删除时由 [LunoPaletteScheme.resolve]
     * 回退 System。`:ui:design` 不依赖 `:core:data`，这里只持有纯契约模型。
     */
    var customPalettes: List<AlertPalette> by mutableStateOf(emptyList())

    // 形状按（角色, 形态, 半径）缓存：设置不变时与固定 Shape 等价，改设置才重建。
    private val cache = HashMap<ShapeKey, Shape>()

    private data class ShapeKey(val role: ShapeRole, val mode: ShapeMode, val radiusDp: Int)

    /** 该角色实际生效的半径：越界时取该形态最大合法档（设置页会同步显示）。 */
    fun resolvedRadius(role: ShapeRole): Int {
        val setting = settings.settingOf(role)
        val options = role.radiusOptions(setting.mode)
        if (options.isEmpty()) return 0
        val wanted = (setting.radiusDp + role.radiusOffsetDp).coerceAtLeast(0)
        return if (wanted in options) wanted else options.last()
    }

    /** 角色 → 当前生效形状。 */
    fun shapeOf(role: ShapeRole): Shape {
        val setting = settings.settingOf(role)
        val radius = resolvedRadius(role)
        val key = ShapeKey(role, setting.mode, radius)
        return cache.getOrPut(key) { build(role, setting.mode, radius) }
    }

    private fun build(role: ShapeRole, mode: ShapeMode, radiusDp: Int): Shape = when (role) {
        ShapeRole.Capsule -> PercentSquircleShape(
            percent = 50f / (1f + capsuleSmoothing(mode) / 100f),
            smoothing = capsuleSmoothing(mode),
        )
        ShapeRole.SheetTop -> SquircleShape(
            topStart = radiusDp.dp,
            topEnd = radiusDp.dp,
            bottomStart = 0.dp,
            bottomEnd = 0.dp,
            smoothing = containerSmoothing(mode),
        )
        else -> SquircleShape(radiusDp.dp, containerSmoothing(mode))
    }

    /**
     * M3 `Shapes`：小/中 → 小元件，大 → 列表与卡，特大 → 弹层。
     *
     * M3 要求 `CornerBasedShape`，不能用委托 `Shape`，所以这里直接构造 `SquircleShape`；
     * `AppTheme` 在组合里读 [settings]，设置一变整棵主题的 M3 形状跟着重建。
     */
    fun materialShapes(): Shapes = Shapes(
        extraSmall = SquircleShape(
            resolvedRadius(ShapeRole.Compact).dp,
            containerSmoothing(settings.compact.mode),
        ),
        small = SquircleShape(
            resolvedRadius(ShapeRole.Compact).dp,
            containerSmoothing(settings.compact.mode),
        ),
        medium = SquircleShape(
            resolvedRadius(ShapeRole.CompactMedium).dp,
            containerSmoothing(settings.compact.mode),
        ),
        large = SquircleShape(
            resolvedRadius(ShapeRole.List).dp,
            containerSmoothing(settings.list.mode),
        ),
        extraLarge = SquircleShape(
            resolvedRadius(ShapeRole.Sheet).dp,
            containerSmoothing(settings.sheet.mode),
        ),
    )

    // ---- 尺寸 / 玻璃（token 的 get() 读这里） ----

    val pageGutter: Dp get() = settings.spacing.pageGutterDp.dp
    val listSpacing: Dp get() = settings.spacing.listSpacingDp.dp

    val topBarBlur: Dp get() = settings.topBarGlass.blurDp.dp
    val topBarAlpha: Float get() = settings.topBarGlass.alpha

    val floatingBlur: Dp get() = settings.floatingGlass.blurDp.dp
    val floatingAlpha: Float get() = settings.floatingGlass.alpha
    val floatingShadow: ShadowStrength get() = settings.floatingGlass.shadow

    val topBarGlass: GlassSetting get() = settings.topBarGlass
    val floatingGlass: GlassSetting get() = settings.floatingGlass
}

/** 角色 → 所属设置项。 */
fun UiAppearanceSettings.settingOf(role: ShapeRole): ShapeSetting = when (role) {
    ShapeRole.List -> list
    ShapeRole.Menu, ShapeRole.Toolbar, ShapeRole.MenuInner -> menu
    ShapeRole.Compact, ShapeRole.CompactMedium -> compact
    ShapeRole.Sheet, ShapeRole.SheetTop -> sheet
    // 胶囊只有形态；半径不参与，返回占位值。
    ShapeRole.Capsule -> ShapeSetting(mode = capsule.mode, radiusDp = 0)
}

/** 形状委托：每次 layout 读一次设置，设置变化即触发重排（无需调用点传参）。 */
internal class DelegatingShape(private val role: ShapeRole) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = UiAppearance.shapeOf(role).createOutline(size, layoutDirection, density)
}
