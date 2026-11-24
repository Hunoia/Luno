package hunoia.luno.floatball.settings.alert

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import hunoia.luno.config.model.AlertKind
import hunoia.luno.config.model.AlertLayer
import hunoia.luno.config.model.AlertLayerCategory
import hunoia.luno.config.model.AlertLibrary
import hunoia.luno.config.model.AlertLimit
import hunoia.luno.config.model.AlertParticleShape
import hunoia.luno.config.model.ParticleArea
import hunoia.luno.config.model.ParticleHaloStyle
import hunoia.luno.config.model.ParticlePath
import hunoia.luno.config.model.AlertPreset
import hunoia.luno.config.model.BallPattern
import hunoia.luno.config.model.CapsulePattern
import hunoia.luno.config.model.EdgePattern
import hunoia.luno.config.model.NotificationAlertSettings
import hunoia.luno.config.model.RevealPattern
import hunoia.luno.config.model.ScreenPattern
import hunoia.luno.config.model.category
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.floatball.R
import hunoia.luno.runtime.floatball.alert.preset.BuiltinAlertPresets
import hunoia.luno.runtime.floatball.alert.engine.AlertPlanFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color

/**
 * 到达提示 v4 设置页（单选）与预设库 / 编辑器共用的展示件、落盘与编辑状态。
 *
 * 预设 = 1–5 个层；层类别只用于分组与上限，宿主由层自己的 [AlertKind] 决定。
 */

/** 预设播放冲突策略的展示名（预设编辑页 / 预设库）。 */
internal fun hunoia.luno.config.model.AlertPlaybackPolicy.labelRes(): Int = when (this) {
    hunoia.luno.config.model.AlertPlaybackPolicy.Skip ->
        R.string.float_ball_preset_policy_skip
    hunoia.luno.config.model.AlertPlaybackPolicy.Replace ->
        R.string.float_ball_preset_policy_replace
    hunoia.luno.config.model.AlertPlaybackPolicy.Queue ->
        R.string.float_ball_preset_policy_queue
}

/** 层类别的展示名（编辑器分组 / 预设卡片徽标）。 */
@Composable
internal fun AlertLayerCategory.titleRes(): Int = when (this) {
    AlertLayerCategory.Edge -> R.string.float_ball_alert_category_edge
    AlertLayerCategory.Particle -> R.string.float_ball_alert_category_particle
    AlertLayerCategory.Ball -> R.string.float_ball_alert_category_ball
    AlertLayerCategory.Screen -> R.string.float_ball_alert_category_screen
    AlertLayerCategory.Reveal -> R.string.float_ball_alert_category_reveal
}

/** 内置预设的本地化名称；未登记时回落内置 name（[AlertPreset.name]）。 */
internal fun builtinPresetNameRes(id: String): Int? = when (id) {
    "builtin_edge_glow" -> R.string.float_ball_preset_edge_glow
    "builtin_edge_comet" -> R.string.float_ball_preset_edge_comet
    "builtin_particle_rain" -> R.string.float_ball_preset_particle_rain
    "builtin_particle_meteor" -> R.string.float_ball_preset_particle_meteor
    "builtin_particle_confetti" -> R.string.float_ball_preset_particle_confetti
    "builtin_ball_ring" -> R.string.float_ball_preset_ball_ring
    "builtin_ball_burst" -> R.string.float_ball_preset_ball_burst
    "builtin_capsule_pulse" -> R.string.float_ball_preset_capsule_pulse
    "builtin_screen_blackout" -> R.string.float_ball_preset_screen_blackout
    "builtin_screen_crt_off" -> R.string.float_ball_preset_screen_crt_off
    "builtin_screen_shatter" -> R.string.float_ball_preset_screen_shatter
    "builtin_screen_blinds" -> R.string.float_ball_preset_screen_blinds
    "builtin_screen_iris" -> R.string.float_ball_preset_screen_iris
    "builtin_ball_ripple" -> R.string.float_ball_preset_ball_ripple
    "builtin_particle_point_burst" -> R.string.float_ball_preset_particle_point_burst
    "builtin_particle_spark" -> R.string.float_ball_preset_particle_spark
    "builtin_screen_flash" -> R.string.float_ball_preset_screen_flash
    "builtin_screen_vignette" -> R.string.float_ball_preset_screen_vignette
    "builtin_path_comet" -> R.string.float_ball_preset_path_comet
    "builtin_path_spiral" -> R.string.float_ball_preset_path_spiral
    "builtin_edge_lightning" -> R.string.float_ball_preset_edge_lightning
    "builtin_capsule_sweep" -> R.string.float_ball_preset_capsule_sweep
    "builtin_ball_orbit" -> R.string.float_ball_preset_ball_orbit
    "builtin_capsule_flip" -> R.string.float_ball_preset_capsule_flip
    "builtin_reveal_text" -> R.string.float_ball_preset_reveal_text
    "builtin_reveal_icon" -> R.string.float_ball_preset_reveal_icon
    else -> null
}

/** 非 Composable 场景（搜索 / Toast / 对话框）里的展示名：内置走系统语言资源。 */
internal fun AlertPreset.displayNameString(context: android.content.Context): String {
    val res = builtinPresetNameRes(id)
    return if (res != null) context.getString(res) else name.ifBlank { id }
}

/** 预设展示名（选择页 / 预设库 / 编辑共用）。 */
@Composable
internal fun AlertPreset.displayName(): String {
    val builtin = builtinPresetNameRes(id)
    if (builtin != null) return stringResource(builtin)
    return name.takeIf { it.isNotBlank() } ?: stringResource(R.string.float_ball_preset_unnamed)
}

/** 预设总层数展示：0 层 = 待补素材的空草稿，1 层 = 素材，多层 = 组合。 */
@Composable
internal fun AlertPreset.layerSummary(): String = when (layers.size) {
    0 -> stringResource(R.string.float_ball_preset_layers_zero)
    1 -> stringResource(R.string.float_ball_preset_layers_one)
    else -> stringResource(R.string.float_ball_preset_layers_many, layers.size)
}

/** 空预设不能试播：播放端会解析成空计划，这里给 UI 一个统一判断。 */
internal fun AlertPreset.isPlayable(): Boolean = layers.isNotEmpty()

/** 当前选中的预设（未选 / 已被删 → null）。 */
internal fun NotificationAlertSettings.selectedPreset(library: AlertLibrary): AlertPreset? {
    val id = presetId ?: return null
    return AlertPlanFactory.presets(library)[id]
}

/** 可选预设：内置素材 + 用户单层 / 组合。 */
internal fun AlertLibrary.pickerPresets(): List<AlertPreset> =
    AlertPlanFactory.presetsForPicker(this)

/** 用户预设（素材库里的非内置项）。 */
internal fun AlertLibrary.userPresets(): List<AlertPreset> = presets.filter { !it.builtin }

/**
 * 编辑 / 校验用查找：**包含空层预设**。
 *
 * 播放与选择器必须走 [pickerPresets]（过滤空层）；但删光层的预设仍是可编辑草稿，
 * 若这里也过滤，编辑页会拿到 null 立刻 `onGone()` 弹回，用户看到的就是“点击没反应”。
 */
internal fun AlertLibrary.presetById(id: String?): AlertPreset? {
    val presetId = id ?: return null
    BuiltinAlertPresets.all.firstOrNull { it.id == presetId }?.let { return it }
    return presets.firstOrNull { it.id == presetId }
}

// ---------------------------------------------------------------- 层展示

/** 层的兜底显示名：来源素材名优先，否则「类别 · 形态」。 */
@Composable
internal fun AlertLayer.displayName(): String {
    if (name.isNotBlank()) return name
    val source = sourcePresetId?.let { builtinPresetNameRes(it) }
    if (source != null) return stringResource(source)
    val pattern = patternLabel()
    return if (pattern == null) {
        stringResource(kind.category.titleRes())
    } else {
        "${stringResource(kind.category.titleRes())} · $pattern"
    }
}

/** 层参数里的形态名（用于编辑器行副标题）。 */
@Composable
internal fun AlertLayer.patternLabel(): String? = when (kind) {
    AlertKind.Edge -> edge?.pattern?.labelRes()?.let { stringResource(it) }
    AlertKind.Particle -> stringResource(R.string.float_ball_layer_particle_engine)
    AlertKind.Ball -> ball?.pattern?.labelRes()?.let { stringResource(it) }
    AlertKind.Capsule -> capsule?.pattern?.labelRes()?.let { stringResource(it) }
    AlertKind.Screen -> screen?.pattern?.labelRes()?.let { stringResource(it) }
    AlertKind.Reveal -> reveal?.pattern?.labelRes()?.let { stringResource(it) }
}

internal fun EdgePattern.labelRes(): Int = when (this) {
    EdgePattern.Glow -> R.string.float_ball_layer_edge_glow
    EdgePattern.Flow -> R.string.float_ball_layer_edge_flow
    EdgePattern.Ripple -> R.string.float_ball_layer_edge_ripple
    EdgePattern.Fireworks -> R.string.float_ball_layer_edge_fireworks
    EdgePattern.Blade -> R.string.float_ball_layer_edge_blade
    EdgePattern.Comet -> R.string.float_ball_layer_edge_comet
    EdgePattern.Ribbon -> R.string.float_ball_layer_edge_ribbon
    EdgePattern.Lightning -> R.string.float_ball_layer_edge_lightning
}

internal fun BallPattern.labelRes(): Int = when (this) {
    BallPattern.Ring -> R.string.float_ball_layer_ball_ring
    BallPattern.Ripple -> R.string.float_ball_layer_ball_ripple
    BallPattern.Burst -> R.string.float_ball_layer_ball_burst
    BallPattern.Halo -> R.string.float_ball_layer_ball_halo
    BallPattern.Shockwave -> R.string.float_ball_layer_ball_shockwave
    BallPattern.Spiral -> R.string.float_ball_layer_ball_spiral
    BallPattern.Orbit3D -> R.string.float_ball_layer_ball_orbit
}

internal fun CapsulePattern.labelRes(): Int = when (this) {
    CapsulePattern.Pulse -> R.string.float_ball_layer_capsule_pulse
    CapsulePattern.Shimmer -> R.string.float_ball_layer_capsule_shimmer
    CapsulePattern.BreathOutline -> R.string.float_ball_layer_capsule_breath
    CapsulePattern.InnerSweep -> R.string.float_ball_layer_capsule_sweep
    CapsulePattern.Flip3D -> R.string.float_ball_layer_capsule_flip
    CapsulePattern.DualChase -> R.string.float_ball_layer_capsule_chase
}

internal fun ScreenPattern.labelRes(): Int = when (this) {
    ScreenPattern.Flash -> R.string.float_ball_layer_screen_flash
    ScreenPattern.Vignette -> R.string.float_ball_layer_screen_vignette
    ScreenPattern.ZoomPulse -> R.string.float_ball_layer_screen_zoom
    ScreenPattern.CenterBlackout -> R.string.float_ball_layer_screen_blackout
    ScreenPattern.CrtOff -> R.string.float_ball_layer_screen_crt_off
    ScreenPattern.Shatter -> R.string.float_ball_layer_screen_shatter
    ScreenPattern.Blinds -> R.string.float_ball_layer_screen_blinds
    ScreenPattern.Iris -> R.string.float_ball_layer_screen_iris
}

internal fun hunoia.luno.config.model.SwipeIndicatorStyle.labelRes(): Int = when (this) {
    hunoia.luno.config.model.SwipeIndicatorStyle.Pill ->
        R.string.swipe_indicator_style_pill
    hunoia.luno.config.model.SwipeIndicatorStyle.Droplet ->
        R.string.swipe_indicator_style_droplet
    hunoia.luno.config.model.SwipeIndicatorStyle.Wave ->
        R.string.swipe_indicator_style_wave
    hunoia.luno.config.model.SwipeIndicatorStyle.Glow ->
        R.string.swipe_indicator_style_glow
    hunoia.luno.config.model.SwipeIndicatorStyle.Ripple ->
        R.string.swipe_indicator_style_ripple
}

internal fun RevealPattern.labelRes(): Int = when (this) {
    RevealPattern.Text -> R.string.float_ball_layer_reveal_text
    RevealPattern.Icon -> R.string.float_ball_layer_reveal_icon
}

internal fun ParticleArea.labelRes(): Int = when (this) {
    ParticleArea.TopEdge -> R.string.float_ball_layer_area_top
    ParticleArea.BottomEdge -> R.string.float_ball_layer_area_bottom
    ParticleArea.ScreenRect -> R.string.float_ball_layer_area_screen
    ParticleArea.EdgePerimeter -> R.string.float_ball_layer_area_perimeter
    ParticleArea.Point -> R.string.float_ball_layer_area_point
    ParticleArea.BallRing -> R.string.float_ball_layer_area_ball
}

internal fun ParticlePath.labelRes(): Int = when (this) {
    ParticlePath.Bezier -> R.string.float_ball_layer_path_bezier
    ParticlePath.Arc -> R.string.float_ball_layer_path_arc
    ParticlePath.Sine -> R.string.float_ball_layer_path_sine
    ParticlePath.ZigZag -> R.string.float_ball_layer_path_zigzag
}

internal fun AlertParticleShape.labelRes(): Int = when (this) {
    AlertParticleShape.Dot -> R.string.float_ball_layer_shape_dot
    AlertParticleShape.Square -> R.string.float_ball_layer_shape_square
    AlertParticleShape.Triangle -> R.string.float_ball_layer_shape_triangle
    AlertParticleShape.Star -> R.string.float_ball_layer_shape_star
    AlertParticleShape.Hexagon -> R.string.float_ball_layer_shape_hexagon
    AlertParticleShape.Ring -> R.string.float_ball_layer_shape_ring
    AlertParticleShape.Hollow -> R.string.float_ball_layer_shape_hollow
    AlertParticleShape.Line -> R.string.float_ball_layer_shape_line
    AlertParticleShape.Heart -> R.string.float_ball_layer_shape_heart
    AlertParticleShape.Petal -> R.string.float_ball_layer_shape_petal
    AlertParticleShape.Snowflake -> R.string.float_ball_layer_shape_snowflake
    AlertParticleShape.Leaf -> R.string.float_ball_layer_shape_leaf
    AlertParticleShape.Emoji -> R.string.float_ball_layer_shape_emoji
    AlertParticleShape.Text -> R.string.float_ball_layer_shape_text
    AlertParticleShape.CustomImage -> R.string.float_ball_layer_shape_image
    AlertParticleShape.AppIcon -> R.string.float_ball_layer_shape_app
}

internal fun ParticleHaloStyle.labelRes(): Int = when (this) {
    ParticleHaloStyle.Off -> R.string.float_ball_layer_halo_off
    ParticleHaloStyle.Soft -> R.string.float_ball_layer_halo_soft
    ParticleHaloStyle.Shape -> R.string.float_ball_layer_halo_shape
}

/**
 * 筛选器（预设选择 sheet / 预设库共用）：全部 + 五类，**单行横向滚动**。
 *
 * 这是“过滤器”而不是“设置值”，所以仍用 PressFilterChip（ui-guidelines §4.4）；
 * 单行不换行，36 个素材按类别收窄时不会把标题区撑成两排。
 */
@Composable
internal fun AlertCategoryFilter(
    selected: AlertLayerCategory?,
    onSelect: (AlertLayerCategory?) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            hunoia.luno.ui.component.PressFilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = {
                    androidx.compose.material3.Text(
                        stringResource(R.string.float_ball_alert_filter_all),
                    )
                },
            )
        }
        AlertLayerCategory.entries.forEach { category ->
            item(key = category.name) {
                hunoia.luno.ui.component.PressFilterChip(
                    selected = selected == category,
                    onClick = { onSelect(category) },
                    label = {
                        androidx.compose.material3.Text(stringResource(category.titleRes()))
                    },
                )
            }
        }
    }
}

/**
 * 调色板单选下拉：预设级（组合调色板）/ 层级（颜色）共用。
 *
 * 第一项是“跟随”（预设级 = 跟随各层；层级 = 跟随预设），后面是素材库全部调色板。
 */
@Composable
internal fun AlertPaletteRow(
    title: String,
    library: AlertLibrary,
    selectedId: String?,
    followLabel: String,
    shape: Shape,
    onSelect: (String?) -> Unit,
) {
    val palettes = library.paletteChoices()
    val index = palettes.indexOfFirst { it.id == selectedId }
    hunoia.luno.ui.component.row.LunoDropdownRow(
        title = title,
        options = listOf(followLabel) + palettes.map { it.name },
        selectedIndex = if (index < 0) 0 else index + 1,
        onSelect = { selected -> onSelect(if (selected == 0) null else palettes[selected - 1].id) },
        shape = shape,
    )
}

/** 预设卡片上的类别集合（按顺序去重）。 */
internal fun AlertPreset.categories(): List<AlertLayerCategory> =
    layers.map { it.kind.category }.distinct()

// ---------------------------------------------------------------- 落盘

/**
 * 设置页的落盘助手（**仅持久化**，不做同屏刷新）：只改 `notificationCapsule.alert` 子树。
 *
 * 页面持有本地即显状态（用户操作立即反映到 UI），这里只把同一 transform 写回配置；
 * 同屏刷新由页内本地状态负责，不依赖配置流「活跃推送」。
 */
internal suspend fun persistAlertNow(
    transform: (NotificationAlertSettings) -> NotificationAlertSettings,
) {
    TuneConfig.updateAction { current ->
        val capsule = current.floatBall.notificationCapsule
        current.copy(
            floatBall = current.floatBall.copy(
                notificationCapsule = capsule.copy(alert = transform(capsule.alert)),
            ),
        )
    }
}

/** [persistAlertNow] 的页面便捷写法（页面留在前台时用；要立刻导航走请用 `persistAlertNow`）。 */
internal fun persistAlert(
    scope: CoroutineScope,
    transform: (NotificationAlertSettings) -> NotificationAlertSettings,
) {
    scope.launch { persistAlertNow(transform) }
}

/** 素材库落盘（调色板 / 用户预设）：与 `alert` 子树平级，v4 重置不动这里。 */
internal suspend fun persistLibraryNow(
    transform: (AlertLibrary) -> AlertLibrary,
) {
    TuneConfig.updateAction { current ->
        val capsule = current.floatBall.notificationCapsule
        current.copy(
            floatBall = current.floatBall.copy(
                notificationCapsule = capsule.copy(alertLibrary = transform(capsule.alertLibrary)),
            ),
        )
    }
}

internal fun persistLibrary(
    scope: CoroutineScope,
    transform: (AlertLibrary) -> AlertLibrary,
) {
    scope.launch { persistLibraryNow(transform) }
}

/**
 * 设置页 / 预设库 / 编辑器共用的编辑态：本地即显 + 异步落盘。
 *
 * 不直接读配置流的原因见 `persistAlert` 的注释：MultiProcessDataStore 写盘后不保证
 * 通知活跃订阅者，同屏切换预设 / 覆盖会换不出来。resume 时再从真实配置同步一次。
 */
@Stable
internal class AlertEditorState internal constructor(private val scope: CoroutineScope) {

    var alert: NotificationAlertSettings by mutableStateOf(
        TuneConfig.latestAction().floatBall.notificationCapsule.alert,
    )
        private set

    var library: AlertLibrary by mutableStateOf(
        TuneConfig.latestAction().floatBall.notificationCapsule.alertLibrary,
    )
        private set

    fun update(transform: (NotificationAlertSettings) -> NotificationAlertSettings) {
        alert = transform(alert)
        persistAlert(scope, transform)
    }

    fun updateLibrary(transform: (AlertLibrary) -> AlertLibrary) {
        library = transform(library)
        persistLibrary(scope, transform)
    }

    /**
     * 结构改动（新建 / 删除 / 加层 / 改名）走这条：本地即显 → 等写盘 → 对齐兜底缓存。
     *
     * 调用方在同一协程里 await 后再导航，避免「返回上一页读到旧素材库」。
     */
    suspend fun commitLibrary(transform: (AlertLibrary) -> AlertLibrary) {
        val next = transform(library)
        library = next
        persistLibraryNow { transform(it) }
        TuneConfig.syncActionSnapshot()
    }

    /** 单选预设等 alert 子树的提交：同上，写完再导航。 */
    suspend fun commitAlert(transform: (NotificationAlertSettings) -> NotificationAlertSettings) {
        val next = transform(alert)
        alert = next
        persistAlertNow { transform(it) }
        TuneConfig.syncActionSnapshot()
    }

    fun refresh() {
        val capsule = TuneConfig.latestAction().floatBall.notificationCapsule
        alert = capsule.alert
        library = capsule.alertLibrary
    }
}

@Composable
internal fun rememberAlertEditor(): AlertEditorState {
    val scope = rememberCoroutineScope()
    val editor = remember { AlertEditorState(scope) }
    LifecycleResumeEffect(Unit) {
        editor.refresh()
        onPauseOrDispose { }
    }
    return editor
}

/**
 * 二级页的旋钮滑杆：拖动只改本地预览，松手一次性落盘（滑杆交互口径）。
 */
@Composable
internal fun AlertKnobSlider(
    label: String,
    value: Int,
    range: IntRange,
    display: @Composable (Float) -> String,
    shape: Shape,
    onCommit: (Int) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    hunoia.luno.ui.component.input.LunoTextSlider(
        value = local,
        onValueChange = { local = it },
        onValueChangeFinished = { onCommit(local.roundToInt()) },
        text = label,
        valueDisplay = display,
        valueRange = range.first.toFloat()..range.last.toFloat(),
        shape = shape,
    )
}

/** 预设卡片上的调色板色点：预设级调色板优先，否则取层的逐色引用。 */
@Composable
internal fun AlertPreset.swatchColors(library: AlertLibrary, fallback: Color): List<Color> {
    val paletteId = paletteId
    if (paletteId != null) {
        library.palettes.firstOrNull { it.id == paletteId }?.let { palette ->
            if (palette.colors.isNotEmpty()) {
                return palette.colors.map { Color(it) }
            }
        }
    }
    val colors = layers.firstOrNull { it.color.colors.isNotEmpty() }?.color?.colors
        ?: layers.firstNotNullOfOrNull { layer ->
            layer.color.paletteId?.let { id ->
                library.palettes.firstOrNull { it.id == id }?.colors
            }
        }
    return (colors ?: emptyList()).map { Color(it) }.ifEmpty { listOf(fallback) }
}

/** 编辑器里给新层用的稳定 id（时间戳 + 计数，避免同一预设内重复）。 */
internal fun newLayerId(seed: Long = System.nanoTime()): String = "layer_$seed"
/** 上限原因 → 提示文案（添加层 / 组合编辑器共用）。 */
internal fun AlertLimit.reasonRes(): Int = when (this) {
    AlertLimit.TooManyEnabledLayers -> R.string.float_ball_preset_limit_layers
    AlertLimit.TooManyStoredLayers -> R.string.float_ball_preset_limit_stored
    AlertLimit.TooManyInCategory -> R.string.float_ball_preset_limit_category
    AlertLimit.TooManyCapsuleLayers -> R.string.float_ball_preset_limit_capsule
    AlertLimit.TooManyScreenLayers -> R.string.float_ball_preset_limit_screen
    AlertLimit.TooManyRevealLayers -> R.string.float_ball_preset_limit_reveal
    AlertLimit.EmptyPreset -> R.string.float_ball_preset_no_layer
    AlertLimit.MissingParams -> R.string.float_ball_preset_limit_params
}
