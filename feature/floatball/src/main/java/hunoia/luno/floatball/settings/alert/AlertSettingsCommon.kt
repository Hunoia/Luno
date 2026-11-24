package hunoia.luno.floatball.settings.alert

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import hunoia.luno.config.model.AlertLibrary
import hunoia.luno.config.model.AlertPreset
import hunoia.luno.config.model.AlertSlot
import hunoia.luno.config.model.AlertSlotOverrides
import hunoia.luno.config.model.AlertSlotSettings
import hunoia.luno.config.model.NotificationAlertSettings
import hunoia.luno.config.model.defaultPresetId
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.floatball.R
import hunoia.luno.runtime.floatball.alert.engine.AlertSlotPlanFactory
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.SegmentedGroupScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 到达提示 v3 设置页的共用件：槽位展示名、预设名、槽位读写、本地即显编辑状态与滑杆。
 *
 * 槽位只是设置页分组；预设与宿主由播放计划决定（见 `AlertEffectModel` / 引擎）。
 */

/** 槽位展示名（主页面入口 + 二级页标题）。 */
internal fun AlertSlot.titleRes(): Int = when (this) {
    AlertSlot.Edge -> R.string.float_ball_alert_slot_edge
    AlertSlot.Particle -> R.string.float_ball_alert_slot_particle
    AlertSlot.Ball -> R.string.float_ball_alert_slot_ball
    AlertSlot.Screen -> R.string.float_ball_alert_slot_screen
    AlertSlot.Path -> R.string.float_ball_alert_slot_path
}

/** 槽位一句话说明（二级页组头副标题）。 */
internal fun AlertSlot.subtitleRes(): Int = when (this) {
    AlertSlot.Edge -> R.string.float_ball_alert_slot_edge_hint
    AlertSlot.Particle -> R.string.float_ball_alert_slot_particle_hint
    AlertSlot.Ball -> R.string.float_ball_alert_slot_ball_hint
    AlertSlot.Screen -> R.string.float_ball_alert_slot_screen_hint
    AlertSlot.Path -> R.string.float_ball_alert_slot_path_hint
}

/** 内置预设的本地化名称；未登记时回落内置 name（[AlertPreset.name]）。 */
internal fun builtinPresetNameRes(id: String): Int? = when (id) {
    "builtin_edge_glow" -> R.string.float_ball_preset_edge_glow
    "builtin_edge_flow" -> R.string.float_ball_preset_edge_flow
    "builtin_edge_ripple" -> R.string.float_ball_preset_edge_ripple
    "builtin_edge_comet" -> R.string.float_ball_preset_edge_comet
    "builtin_particle_rain" -> R.string.float_ball_preset_particle_rain
    "builtin_particle_fountain" -> R.string.float_ball_preset_particle_fountain
    "builtin_particle_meteor" -> R.string.float_ball_preset_particle_meteor
    "builtin_particle_confetti" -> R.string.float_ball_preset_particle_confetti
    "builtin_particle_firefly" -> R.string.float_ball_preset_particle_firefly
    "builtin_ball_ring" -> R.string.float_ball_preset_ball_ring
    "builtin_ball_ripple" -> R.string.float_ball_preset_ball_ripple
    "builtin_ball_burst" -> R.string.float_ball_preset_ball_burst
    "builtin_ball_shockwave" -> R.string.float_ball_preset_ball_shockwave
    "builtin_capsule_pulse" -> R.string.float_ball_preset_capsule_pulse
    "builtin_screen_flash" -> R.string.float_ball_preset_screen_flash
    "builtin_screen_vignette" -> R.string.float_ball_preset_screen_vignette
    "builtin_screen_zoom" -> R.string.float_ball_preset_screen_zoom
    "builtin_path_comet" -> R.string.float_ball_preset_path_comet
    "builtin_path_orbit" -> R.string.float_ball_preset_path_orbit
    "builtin_path_spiral" -> R.string.float_ball_preset_path_spiral
    "builtin_edge_ribbon" -> R.string.float_ball_preset_edge_ribbon
    "builtin_edge_lightning" -> R.string.float_ball_preset_edge_lightning
    "builtin_ball_spiral" -> R.string.float_ball_preset_ball_spiral
    "builtin_ball_orbit" -> R.string.float_ball_preset_ball_orbit
    "builtin_capsule_flip" -> R.string.float_ball_preset_capsule_flip
    "builtin_capsule_chase" -> R.string.float_ball_preset_capsule_chase
    "builtin_particle_petals" -> R.string.float_ball_preset_particle_petals
    "builtin_particle_snow" -> R.string.float_ball_preset_particle_snow
    "builtin_reveal_text" -> R.string.float_ball_preset_reveal_text
    "builtin_reveal_icon" -> R.string.float_ball_preset_reveal_icon
    else -> null
}

/** 当前槽位的预设展示名：内置走本地化，用户预设用自身名称；未选 = 槽位默认预设。 */
@Composable
internal fun slotPresetName(slot: AlertSlot, settings: AlertSlotSettings, library: AlertLibrary): String {
    val id = settings.presetId ?: slot.defaultPresetId()
    val builtin = builtinPresetNameRes(id)
    if (builtin != null) return stringResource(builtin)
    val preset = library.presets.firstOrNull { it.id == id }
    return preset?.name?.takeIf { it.isNotBlank() } ?: id
}

/** 预设展示名（预设选择页、试播提示共用）。 */
@Composable
internal fun AlertPreset.displayName(): String {
    val builtin = builtinPresetNameRes(id)
    if (builtin != null) return stringResource(builtin)
    return name.takeIf { it.isNotBlank() } ?: id
}

/** 该槽的预设候选（内置 + 用户；只保留提供可用宿主变体的）。 */
internal fun presetsForSlot(slot: AlertSlot, library: AlertLibrary): List<AlertPreset> =
    AlertSlotPlanFactory.presetsForSlot(slot, library)

// ---------------------------------------------------------------- 槽位读写

internal fun NotificationAlertSettings.slotSettings(slot: AlertSlot): AlertSlotSettings =
    slots.of(slot)

internal fun NotificationAlertSettings.withSlot(
    slot: AlertSlot,
    transform: (AlertSlotSettings) -> AlertSlotSettings,
): NotificationAlertSettings = copy(slots = slots.with(slot, transform(slots.of(slot))))

internal fun AlertSlotSettings.withOverrides(
    transform: (AlertSlotOverrides) -> AlertSlotOverrides,
): AlertSlotSettings = copy(overrides = transform(overrides))

/** 槽位是否开启了自定义颜色（素材库调色板或逐色）。 */
internal fun AlertSlotOverrides.hasColor(): Boolean =
    paletteId != null || colors.isNotEmpty()

/** 全部槽位（设置页顺序）。 */
internal val AlertSlotOrder: List<AlertSlot> = AlertSlot.entries

// ---------------------------------------------------------------- 落盘

/**
 * 二级页的落盘助手（**仅持久化**，不做同屏刷新）：只改 `notificationCapsule.alert` 子树。
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

/** 素材库落盘（调色板 / 用户预设）：与 `alert` 子树平级，v3 重置不动这里。 */
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
 * 槽位页 / 预设页共用的编辑态：本地即显 + 异步落盘。
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

/** 每槽旋钮区的最后一行：把当前槽位的覆盖恢复默认（不动预设与颜色）。 */
@Composable
internal fun SegmentedGroupScope.AlertResetOverridesRow(onClick: () -> Unit) {
    item { shape ->
        LunoSettingsRow(
            title = stringResource(R.string.float_ball_alert_reset_overrides),
            subtitle = stringResource(R.string.float_ball_alert_reset_overrides_subtitle),
            icon = Icons.Default.Refresh,
            shape = shape,
            onClick = onClick,
        )
    }
}

/** 把槽位的覆盖恢复默认：强度 / 速度 100，颜色跟随预设。 */
internal fun AlertSlotSettings.withDefaultOverrides(): AlertSlotSettings =
    copy(overrides = AlertSlotOverrides())