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
import hunoia.luno.config.model.AlertCategory
import hunoia.luno.config.model.AlertStyleId
import hunoia.luno.config.model.AnchorAlertStyle
import hunoia.luno.config.model.CapsuleAlertStyle
import hunoia.luno.config.model.EdgeAlertStyle
import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.config.model.NotificationAlertSettings
import hunoia.luno.config.model.ParticleAlertStyle
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.SegmentedGroupScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 提示动效四个分类的共用件：分类 / 样式展示名、分类开关与所选样式的读取、
 * 落盘助手，以及二级页共用的旋钮滑杆。
 *
 * 展示名放这里是为了让主页面入口行与二级页标题用同一份文案；样式名仍复用
 * 「效果」那组字符串（`float_ball_capsule_alert_effect_*`），不新造同义词。
 */

/** 分类展示名（主页面入口 + 二级页标题）。 */
internal fun AlertCategory.titleRes(): Int = when (this) {
    AlertCategory.Edge -> R.string.float_ball_capsule_alert_category_edge
    AlertCategory.Particle -> R.string.float_ball_capsule_alert_category_particle
    AlertCategory.Capsule -> R.string.float_ball_capsule_alert_category_capsule
    AlertCategory.Anchor -> R.string.float_ball_capsule_alert_category_anchor
}

/** 分类一句话说明（二级页组头副标题）。 */
internal fun AlertCategory.subtitleRes(): Int = when (this) {
    AlertCategory.Edge -> R.string.float_ball_capsule_alert_category_edge_hint
    AlertCategory.Particle -> R.string.float_ball_capsule_alert_category_particle_hint
    AlertCategory.Capsule -> R.string.float_ball_capsule_alert_category_capsule_hint
    AlertCategory.Anchor -> R.string.float_ball_capsule_alert_category_anchor_hint
}

/** 样式展示名（下拉选项；选项顺序 = 枚举声明顺序）。 */
internal fun AlertStyleId.labelRes(): Int = when (this) {
    EdgeAlertStyle.EdgeGlow -> R.string.float_ball_capsule_alert_effect_edge_glow
    EdgeAlertStyle.EdgeFlow -> R.string.float_ball_capsule_alert_effect_edge_flow
    EdgeAlertStyle.EdgeRipple -> R.string.float_ball_capsule_alert_effect_edge_ripple
    EdgeAlertStyle.EdgeFireworks -> R.string.float_ball_capsule_alert_effect_edge_fireworks
    EdgeAlertStyle.EdgeBladeRise -> R.string.float_ball_capsule_alert_effect_edge_blade_rise
    ParticleAlertStyle.TopRain -> R.string.float_ball_capsule_alert_effect_particles
    ParticleAlertStyle.BottomFountain -> R.string.float_ball_capsule_alert_effect_particle_fountain
    ParticleAlertStyle.Meteor -> R.string.float_ball_capsule_alert_effect_particle_meteor
    ParticleAlertStyle.Confetti -> R.string.float_ball_capsule_alert_effect_particle_confetti
    ParticleAlertStyle.Firefly -> R.string.float_ball_capsule_alert_effect_particle_firefly
    CapsuleAlertStyle.Pulse -> R.string.float_ball_capsule_alert_effect_pulse
    CapsuleAlertStyle.Shimmer -> R.string.float_ball_capsule_alert_effect_shimmer
    CapsuleAlertStyle.BreathOutline -> R.string.float_ball_capsule_alert_effect_breath_outline
    CapsuleAlertStyle.InnerSweep -> R.string.float_ball_capsule_alert_effect_inner_sweep
    AnchorAlertStyle.Ring -> R.string.float_ball_capsule_alert_effect_ring
    AnchorAlertStyle.Ripple -> R.string.float_ball_capsule_alert_effect_full_ripple
    AnchorAlertStyle.BallBurst -> R.string.float_ball_capsule_alert_effect_ball_burst
    AnchorAlertStyle.BallHalo -> R.string.float_ball_capsule_alert_effect_ball_halo
}

/** 该分类是否开启（主页面入口行的副标题要显示「已关闭」）。 */
internal fun NotificationAlertSettings.categoryEnabled(category: AlertCategory): Boolean =
    when (category) {
        AlertCategory.Edge -> edge.enabled
        AlertCategory.Particle -> particle.enabled
        AlertCategory.Capsule -> capsule.enabled
        AlertCategory.Anchor -> anchor.enabled
    }

/** 该分类当前选中的样式（主页面入口行的副标题要显示样式名）。 */
internal fun NotificationAlertSettings.styleOf(category: AlertCategory): AlertStyleId =
    when (category) {
        AlertCategory.Edge -> edge.style
        AlertCategory.Particle -> particle.style
        AlertCategory.Capsule -> capsule.style
        AlertCategory.Anchor -> anchor.style
    }

/**
 * 二级页的落盘助手（**仅持久化**，不做同屏刷新）：只改 `notificationCapsule.alert` 这棵子树。
 *
 * 四个分类设置页持有本地即显状态（用户操作立即反映到 UI），这里只把同一 transform
 * 写回配置；同屏刷新由页内本地状态负责，不依赖配置流「活跃推送」。
 * 调色板页（独立目的地）不持本地状态，也走这里落盘，分类页返回时经 resume 兜底同步。
 * 写入走 [TuneConfig.updateAction] 的串行队列（拖滑杆松手才调用一次，不逐帧写盘）。
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

/**
 * 四类设置页共用的编辑态：本地即显 + 异步落盘。
 *
 * 不直接读配置流的原因见 `persistAlert` 的注释：MultiProcessDataStore 写盘后不保证
 * 通知活跃订阅者，同屏切样式会换不出旋钮。这里用 `latestAction()` 起步、页面内立即反映
 * 用户操作，resume 时再从真实配置同步一次（兜底调色板页返回 / 外部写入）。
 *
 * 状态归属：一屏一个状态类型（架构 §8.4），这里就是页面状态；`update` 是唯一写入口，
 * 页面只读 [alert]、只发 `transform`。
 */
@Stable
internal class AlertEditorState internal constructor(private val scope: CoroutineScope) {

    var alert: NotificationAlertSettings by mutableStateOf(
        TuneConfig.latestAction().floatBall.notificationCapsule.alert,
    )
        private set

    /** 本地即显 + 异步落盘：transform 先应用到本地（UI 立即反映），再写回配置。 */
    fun update(transform: (NotificationAlertSettings) -> NotificationAlertSettings) {
        alert = transform(alert)
        persistAlert(scope, transform)
    }

    /** resume 兜底：从真实配置同步一次。 */
    fun refresh() {
        alert = TuneConfig.latestAction().floatBall.notificationCapsule.alert
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
 * [range] 来自 `FloatBallDefaults` 的滑杆约束常量；行形状由 [LunoSegmentedGroup] 传入。
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
    LunoTextSlider(
        value = local,
        onValueChange = { local = it },
        onValueChangeFinished = { onCommit(local.roundToInt()) },
        text = label,
        valueDisplay = display,
        valueRange = range.first.toFloat()..range.last.toFloat(),
        shape = shape,
    )
}

/** 样式下拉的选项文案：顺序与 [styles] 一致。 */
@Composable
internal fun styleLabels(styles: List<AlertStyleId>): List<String> =
    styles.map { stringResource(it.labelRes()) }

/**
 * 新样式共用的「速度 / 亮度 / 次数」三滑杆：多数新样式沿用同一组通用旋钮，
 * 各自只加差异项。回调由各页写到自己的样式设置块。
 */
@Composable
internal fun SegmentedGroupScope.AlertSpeedBrightnessCycles(
    speed: Int,
    brightness: Int,
    cycles: Int,
    onSpeed: (Int) -> Unit,
    onBrightness: (Int) -> Unit,
    onCycles: (Int) -> Unit,
) {
    item { shape ->
        AlertKnobSlider(
            label = stringResource(R.string.float_ball_capsule_alert_speed),
            value = speed,
            range = FloatBallDefaults.AlertSpeedPercentMin..FloatBallDefaults.AlertSpeedPercentMax,
            display = { "${it.toInt()}%" },
            shape = shape,
            onCommit = onSpeed,
        )
    }
    item { shape ->
        AlertKnobSlider(
            label = stringResource(R.string.float_ball_capsule_alert_brightness),
            value = brightness,
            range = FloatBallDefaults.AlertBrightnessMin..FloatBallDefaults.AlertBrightnessMax,
            display = { "${it.toInt()}%" },
            shape = shape,
            onCommit = onBrightness,
        )
    }
    item { shape ->
        AlertKnobSlider(
            label = stringResource(R.string.float_ball_capsule_alert_cycles),
            value = cycles,
            range = FloatBallDefaults.AlertCyclesMin..FloatBallDefaults.AlertCyclesMax,
            display = { "${it.toInt()} ×" },
            shape = shape,
            onCommit = onCycles,
        )
    }
}

/**
 * 每样式旋钮区的最后一行：把当前样式的旋钮恢复默认。
 * 只重置样式自己的设置块，不动调色板、开关、形状与图片引用（副标题写清楚）。
 */
@Composable
internal fun SegmentedGroupScope.AlertResetStyleRow(onClick: () -> Unit) {
    item { shape ->
        LunoSettingsRow(
            title = stringResource(R.string.float_ball_capsule_alert_reset_style),
            subtitle = stringResource(R.string.float_ball_capsule_alert_reset_style_subtitle),
            icon = Icons.Default.Refresh,
            shape = shape,
            onClick = onClick,
        )
    }
}
