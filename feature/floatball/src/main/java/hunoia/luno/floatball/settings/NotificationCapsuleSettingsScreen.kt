package hunoia.luno.floatball.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.bridge.system.canDrawOverlays
import hunoia.luno.bridge.system.gotoOverlaySettings
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.CapsuleGestureBehavior
import hunoia.luno.config.model.ColorSpec
import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.config.model.FloatBallSettings
import hunoia.luno.config.model.NotificationAlertEffect
import hunoia.luno.config.model.NotificationAlertSettings
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.row.LunoDropdownRow
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.component.sheet.ColorPickerBottomSheet
import hunoia.luno.ui.component.sheet.toColorSpec
import hunoia.luno.ui.component.sheet.toInitialSelection
import hunoia.luno.ui.facade.LocalFacades
import hunoia.luno.ui.theme.MarkColorSize
import hunoia.luno.ui.theme.toMarkColor
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 通知胶囊设置页：显示/跟手/停留时长/队列/同屏条数与黑名单。
 *
 * 与悬浮球、结果胶囊并列，只负责通知球自己的行为。
 */
@Composable
fun NotificationCapsuleSettingsScreen(
    onBack: () -> Unit,
    onNavToNotificationBlocklist: () -> Unit = {},
) {
    val facades = LocalFacades.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val actionSettings by TuneConfig.action
        .collectAsStateWithLifecycle(initialValue = ActionSettings())
    val floatBall = actionSettings.floatBall
    val capsule = floatBall.notificationCapsule

    var localAutoHide by remember(capsule.autoHideMs) {
        mutableFloatStateOf(capsule.autoHideMs.toFloat())
    }
    var localIdleDim by remember(capsule.idleDimDelayMs) {
        mutableFloatStateOf(capsule.idleDimDelayMs.toFloat())
    }
    var localSuppressMs by remember(capsule.dismissSuppressMs) {
        mutableFloatStateOf(capsule.dismissSuppressMs.toFloat())
    }
    var localTtl by remember(floatBall.inbox.notificationTtlMs) {
        mutableFloatStateOf(floatBall.inbox.notificationTtlMs.toFloat())
    }
    var localCapacity by remember(floatBall.inbox.notificationCapacity, floatBall.inbox.capacity) {
        mutableFloatStateOf((floatBall.inbox.notificationCapacity ?: floatBall.inbox.capacity).toFloat())
    }
    var localVisibleRows by remember(capsule.visibleRows) {
        mutableFloatStateOf(capsule.visibleRows.toFloat())
    }

    var overlayGranted by remember { mutableStateOf(context.canDrawOverlays()) }
    // 通知球自己的图标：未设置时仍显示通知应用图标 / 铃铛，不再继承主球。
    val iconPicker = rememberBallIconPickerState()
    var showBallColorPicker by remember { mutableStateOf(false) }
    var showBackgroundColorPicker by remember { mutableStateOf(false) }
    var showAlertColorPicker by remember { mutableStateOf(false) }
    var notifAccessGranted by remember {
        mutableStateOf(facades.system.isNotificationAccessGranted(context))
    }

    // 授权与撤销都要跟手：撤销时服务已摘窗，需要 nudge 才能回收可见状态。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = context.canDrawOverlays()
                if (granted != overlayGranted) {
                    overlayGranted = granted
                    facades.system.refreshFloatBall(context)
                }
                val notifGranted = facades.system.isNotificationAccessGranted(context)
                if (notifGranted != notifAccessGranted) notifAccessGranted = notifGranted
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun update(transform: (FloatBallSettings) -> FloatBallSettings) {
        scope.launch {
            TuneConfig.updateAction { current ->
                current.copy(floatBall = transform(current.floatBall))
            }
        }
    }

    LunoScreenLazy(
        title = stringResource(R.string.float_ball_notification_group),
        onBack = onBack,
        state = listState,
        sheets = {
            BallCustomIconSheet(
                context = context,
                picker = iconPicker,
                current = capsule.customIcon,
                onChange = { icon ->
                    update { it.copy(notificationCapsule = it.notificationCapsule.copy(customIcon = icon)) }
                },
            )
            if (showBallColorPicker) {
                val scheme = MaterialTheme.colorScheme
                ColorPickerBottomSheet(
                    onDismissRequest = { showBallColorPicker = false },
                    onCopyColor = { hex ->
                        hunoia.luno.clipboard.ClipboardCenter.writeTextImmediate(hex, "Luno color")
                    },
                    initialSelection = capsule.ballColor.toInitialSelection(
                        scheme = scheme,
                        primaryFallback = true,
                    ),
                    onColorSelected = { selection ->
                        update {
                            it.copy(
                                notificationCapsule = it.notificationCapsule.copy(
                                    ballColor = selection.toColorSpec(scheme),
                                ),
                            )
                        }
                        showBallColorPicker = false
                    },
                )
            }
            if (showBackgroundColorPicker) {
                val scheme = MaterialTheme.colorScheme
                ColorPickerBottomSheet(
                    onDismissRequest = { showBackgroundColorPicker = false },
                    onCopyColor = { hex ->
                        hunoia.luno.clipboard.ClipboardCenter.writeTextImmediate(hex, "Luno color")
                    },
                    initialSelection = capsule.backgroundColor.toInitialSelection(
                        scheme = scheme,
                        primaryFallback = true,
                    ),
                    onColorSelected = { selection ->
                        update {
                            it.copy(
                                notificationCapsule = it.notificationCapsule.copy(
                                    backgroundColor = selection.toColorSpec(scheme),
                                ),
                            )
                        }
                        showBackgroundColorPicker = false
                    },
                )
            }
            if (showAlertColorPicker) {
                val scheme = MaterialTheme.colorScheme
                ColorPickerBottomSheet(
                    onDismissRequest = { showAlertColorPicker = false },
                    onCopyColor = { hex ->
                        hunoia.luno.clipboard.ClipboardCenter.writeTextImmediate(hex, "Luno color")
                    },
                    initialSelection = capsule.alert.color.toInitialSelection(
                        scheme = scheme,
                        primaryFallback = true,
                    ),
                    onColorSelected = { selection ->
                        update {
                            it.copy(
                                notificationCapsule = it.notificationCapsule.copy(
                                    alert = it.notificationCapsule.alert.copy(
                                        color = selection.toColorSpec(scheme),
                                    ),
                                ),
                            )
                        }
                        showAlertColorPicker = false
                    },
                )
            }
        },
    ) {
        if (!overlayGranted) {
            item(key = "permission") {
                LunoGroup {
                    LunoSettingsRow(
                        title = stringResource(R.string.float_ball_permission_required),
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        shape = segmentedShape(0, 1),
                        onClick = {
                            context.gotoOverlaySettings()
                            overlayGranted = context.canDrawOverlays()
                        },
                    )
                }
            }
        }

        if (capsule.enabled && !notifAccessGranted) {
            item(key = "notification_access") {
                LunoGroup {
                    LunoSettingsRow(
                        title = stringResource(R.string.float_ball_capsule_need_permission),
                        subtitle = stringResource(R.string.float_ball_capsule_need_permission_hint),
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        shape = segmentedShape(0, 1),
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                            )
                        },
                    )
                }
            }
        }

        item(key = "icon") {
            val hasIcon = capsule.customIcon.path != null
            // 外观组行数 = 图标行（1–2）+ 颜色行（球色 / 背景色各 2–3 行），段形随之。
            val iconRows = if (hasIcon) 2 else 1
            val colorRows = capsuleColorRowCount(capsule.ballColor, capsule.backgroundColor)
            LunoGroup(title = stringResource(R.string.float_ball_appearance)) {
                BallCustomIconRows(
                    context = context,
                    icon = capsule.customIcon,
                    startIndex = 0,
                    rowCount = iconRows + colorRows,
                    onPick = iconPicker::pick,
                    onReset = {
                        resetBallIcon(scope, capsule.customIcon) { icon ->
                            update {
                                it.copy(notificationCapsule = it.notificationCapsule.copy(customIcon = icon))
                            }
                        }
                    },
                )
                CapsuleColorRows(
                    ballSpec = capsule.ballColor,
                    backgroundSpec = capsule.backgroundColor,
                    defaultColor = MaterialTheme.colorScheme.primary,
                    startIndex = iconRows,
                    rowCount = iconRows + colorRows,
                    onBallClick = { showBallColorPicker = true },
                    onBackgroundClick = { showBackgroundColorPicker = true },
                    onBallReset = {
                        update {
                            it.copy(notificationCapsule = it.notificationCapsule.copy(ballColor = ColorSpec.Unset))
                        }
                    },
                    onBackgroundReset = {
                        update {
                            it.copy(
                                notificationCapsule = it.notificationCapsule.copy(
                                    backgroundColor = ColorSpec.Unset,
                                ),
                            )
                        }
                    },
                )
            }
        }

        item(key = "behavior") {
            val suppress = capsule.dismissSuppressEnabled
            // 抑制时长只在开关打开时出现：条数会变，段形按实际条数算。
            val rowCount = if (suppress) 11 else 10
            LunoGroup(title = stringResource(R.string.float_ball_notification_group)) {
                LunoSwitchRow(
                    title = stringResource(R.string.float_ball_notification_enabled),
                    subtitle = stringResource(R.string.float_ball_notification_enabled_subtitle),
                    checked = capsule.enabled,
                    onCheckedChange = { checked ->
                        update { it.copy(notificationCapsule = it.notificationCapsule.copy(enabled = checked)) }
                        // 开关兼服务启停：开 = 拉起，关 = nudge 重评。
                        facades.system.setFloatBallEnabled(context, checked)
                    },
                    shape = segmentedShape(0, rowCount),
                )
                LunoSwitchRow(
                    title = stringResource(R.string.float_ball_notification_follow_hand),
                    subtitle = stringResource(R.string.float_ball_notification_follow_hand_subtitle),
                    checked = capsule.followHand,
                    onCheckedChange = { checked ->
                        update {
                            it.copy(notificationCapsule = it.notificationCapsule.copy(followHand = checked))
                        }
                    },
                    shape = segmentedShape(1, rowCount),
                )
                LunoSwitchRow(
                    title = stringResource(R.string.float_ball_capsule_hide_system),
                    subtitle = stringResource(R.string.float_ball_capsule_hide_system_subtitle),
                    checked = capsule.hideSystemNotification,
                    onCheckedChange = { checked ->
                        update {
                            it.copy(
                                notificationCapsule = it.notificationCapsule.copy(
                                    hideSystemNotification = checked,
                                ),
                            )
                        }
                    },
                    shape = segmentedShape(2, rowCount),
                )
                LunoTextSlider(
                    value = localIdleDim,
                    onValueChange = { value -> localIdleDim = value },
                    onValueChangeFinished = {
                        update {
                            it.copy(
                                notificationCapsule = it.notificationCapsule.copy(
                                    idleDimDelayMs = localIdleDim.toLong(),
                                ),
                            )
                        }
                    },
                    text = stringResource(R.string.float_ball_idle_dim_delay),
                    valueDisplay = { if (it <= 0f) stringResource(R.string.float_ball_off) else "${it.toInt()} ms" },
                    valueRange = FloatBallDefaults.IdleDimDelayMsMin.toFloat()..
                        FloatBallDefaults.IdleDimDelayMsMax.toFloat(),
                    shape = segmentedShape(3, rowCount),
                )
                LunoTextSlider(
                    value = localAutoHide,
                    onValueChange = { value -> localAutoHide = value },
                    onValueChangeFinished = {
                        update {
                            it.copy(
                                notificationCapsule = it.notificationCapsule.copy(
                                    autoHideMs = localAutoHide.toLong(),
                                ),
                            )
                        }
                    },
                    text = stringResource(R.string.float_ball_capsule_auto_hide),
                    valueDisplay = { "${(it / 1000).toInt()} s" },
                    valueRange = FloatBallDefaults.CapsuleAutoHideMsMin.toFloat()..
                        FloatBallDefaults.CapsuleAutoHideMsMax.toFloat(),
                    shape = segmentedShape(4, rowCount),
                )
                LunoTextSlider(
                    value = localTtl,
                    onValueChange = { value -> localTtl = value },
                    onValueChangeFinished = {
                        update {
                            it.copy(inbox = it.inbox.copy(notificationTtlMs = localTtl.toLong()))
                        }
                    },
                    text = stringResource(R.string.float_ball_notification_ttl),
                    valueDisplay = { "${(it / 1000).toInt()} s" },
                    valueRange = FloatBallDefaults.InboxNotificationTtlMsMin.toFloat()..
                        FloatBallDefaults.InboxNotificationTtlMsMax.toFloat(),
                    shape = segmentedShape(5, rowCount),
                )
                LunoTextSlider(
                    value = localCapacity,
                    onValueChange = { value -> localCapacity = value },
                    onValueChangeFinished = {
                        update {
                            it.copy(
                                inbox = it.inbox.copy(
                                    notificationCapacity = localCapacity.toInt(),
                                ),
                            )
                        }
                    },
                    text = stringResource(R.string.float_ball_notification_capacity),
                    valueDisplay = { "${it.toInt()}" },
                    valueRange = FloatBallDefaults.InboxCapacityMin.toFloat()..
                        FloatBallDefaults.InboxCapacityMax.toFloat(),
                    shape = segmentedShape(6, rowCount),
                )
                LunoTextSlider(
                    value = localVisibleRows,
                    onValueChange = { value -> localVisibleRows = value },
                    onValueChangeFinished = {
                        update {
                            it.copy(
                                notificationCapsule = it.notificationCapsule.copy(
                                    visibleRows = localVisibleRows.toInt(),
                                ),
                            )
                        }
                    },
                    text = stringResource(R.string.float_ball_capsule_visible_rows),
                    valueDisplay = { "${it.toInt()}" },
                    valueRange = FloatBallDefaults.CapsuleVisibleRowsMin.toFloat()..
                        FloatBallDefaults.CapsuleVisibleRowsMax.toFloat(),
                    shape = segmentedShape(7, rowCount),
                )
                LunoSwitchRow(
                    title = stringResource(R.string.float_ball_capsule_suppress_dismiss),
                    checked = suppress,
                    onCheckedChange = { checked ->
                        update {
                            it.copy(
                                notificationCapsule = it.notificationCapsule.copy(
                                    dismissSuppressEnabled = checked,
                                ),
                            )
                        }
                    },
                    shape = segmentedShape(8, rowCount),
                )
                if (suppress) {
                    LunoTextSlider(
                        value = localSuppressMs,
                        onValueChange = { value -> localSuppressMs = value },
                        onValueChangeFinished = {
                            update {
                                it.copy(
                                    notificationCapsule = it.notificationCapsule.copy(
                                        dismissSuppressMs = localSuppressMs.toLong(),
                                    ),
                                )
                            }
                        },
                        text = stringResource(R.string.float_ball_capsule_suppress_duration),
                        valueDisplay = { "${(it / 1000).toInt()} s" },
                        valueRange = FloatBallDefaults.CapsuleDismissSuppressMsMin.toFloat()..
                            FloatBallDefaults.CapsuleDismissSuppressMsMax.toFloat(),
                        shape = segmentedShape(9, rowCount),
                    )
                }
                LunoSettingsRow(
                    title = stringResource(R.string.float_ball_capsule_blocklist),
                    trailing = {
                        if (capsule.blocklist.isNotEmpty()) {
                            Text(
                                text = stringResource(
                                    R.string.float_ball_capsule_blocklist_count,
                                    capsule.blocklist.size,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    shape = segmentedShape(if (suppress) 10 else 9, rowCount),
                    onClick = onNavToNotificationBlocklist,
                )
            }
        }

        // 手势：球长按与行级手势仅在内置行为间互选；默认值 = 出厂行为（零破坏）。
        item(key = "gestures") {
            val rows = 4
            LunoGroup(title = stringResource(R.string.float_ball_capsule_gestures)) {
                GestureBehaviorRow(
                    title = stringResource(R.string.float_ball_ball_long_press),
                    current = capsule.ballLongPressBehavior,
                    options = listOf(
                        CapsuleGestureBehavior.NONE,
                        CapsuleGestureBehavior.CLEAR_QUEUE,
                    ),
                    onSelect = { value ->
                        update {
                            it.copy(
                                notificationCapsule = it.notificationCapsule.copy(
                                    ballLongPress = value,
                                ),
                            )
                        }
                    },
                    shape = segmentedShape(0, rows),
                )
                GestureBehaviorRow(
                    title = stringResource(R.string.float_ball_row_long_press),
                    current = capsule.rowLongPressBehavior,
                    options = listOf(
                        CapsuleGestureBehavior.NONE,
                        CapsuleGestureBehavior.IGNORE_ROW,
                    ),
                    onSelect = { value ->
                        update {
                            it.copy(
                                notificationCapsule = it.notificationCapsule.copy(
                                    rowLongPress = value,
                                ),
                            )
                        }
                    },
                    shape = segmentedShape(1, rows),
                )
                GestureBehaviorRow(
                    title = stringResource(R.string.float_ball_row_swipe_away),
                    current = capsule.rowSwipeAwayBehavior,
                    options = listOf(
                        CapsuleGestureBehavior.IGNORE_ROW,
                        CapsuleGestureBehavior.COLLAPSE,
                        CapsuleGestureBehavior.NONE,
                    ),
                    onSelect = { value ->
                        update {
                            it.copy(
                                notificationCapsule = it.notificationCapsule.copy(
                                    rowSwipeAway = value,
                                ),
                            )
                        }
                    },
                    shape = segmentedShape(2, rows),
                )
                GestureBehaviorRow(
                    title = stringResource(R.string.float_ball_row_swipe_toward),
                    current = capsule.rowSwipeTowardBehavior,
                    options = listOf(
                        CapsuleGestureBehavior.COLLAPSE,
                        CapsuleGestureBehavior.IGNORE_ROW,
                        CapsuleGestureBehavior.NONE,
                    ),
                    onSelect = { value ->
                        update {
                            it.copy(
                                notificationCapsule = it.notificationCapsule.copy(
                                    rowSwipeToward = value,
                                ),
                            )
                        }
                    },
                    shape = segmentedShape(3, rows),
                )
            }
        }

        // 到达提示动效：只在胶囊开启时有意义；默认关（老用户升级零感知）。
        if (capsule.enabled) {
            item(key = "alert") {
                val alert = capsule.alert
                val scheme = MaterialTheme.colorScheme
                fun updateAlert(transform: (NotificationAlertSettings) -> NotificationAlertSettings) {
                    update {
                        it.copy(
                            notificationCapsule = it.notificationCapsule.copy(
                                alert = transform(it.notificationCapsule.alert),
                            ),
                        )
                    }
                }
                // 行数动态：开关 1 +（开启时：效果 1 + 所选效果旋钮 + 动效色 1 + 重置 + 试播 1）。
                val knobCount = when (alert.effect) {
                    NotificationAlertEffect.CapsulePulse -> 2
                    else -> 3
                }
                val colorRows = 1 + if (alert.color.isUnset) 0 else 1
                val alertRows = 1 + if (alert.enabled) 1 + knobCount + colorRows + 1 else 0
                LunoGroup(title = stringResource(R.string.float_ball_capsule_alert_group)) {
                    var row = 0
                    LunoSwitchRow(
                        title = stringResource(R.string.float_ball_capsule_alert_enabled),
                        subtitle = stringResource(R.string.float_ball_capsule_alert_enabled_subtitle),
                        checked = alert.enabled,
                        onCheckedChange = { checked ->
                            updateAlert { it.copy(enabled = checked) }
                        },
                        shape = segmentedShape(row++, alertRows),
                    )
                    if (alert.enabled) {
                        val effects = NotificationAlertEffect.entries
                        LunoDropdownRow(
                            title = stringResource(R.string.float_ball_capsule_alert_effect),
                            options = effects.map { stringResource(it.labelRes()) },
                            selectedIndex = effects.indexOf(alert.effect).coerceAtLeast(0),
                            onSelect = { index ->
                                updateAlert { it.copy(effect = effects[index]) }
                            },
                            shape = segmentedShape(row++, alertRows),
                        )
                        // 专属旋钮按所选效果渲染，不共用（需求确认口径）；顺序 = 宽度/量 → 亮度 → 次数。
                        when (alert.effect) {
                            NotificationAlertEffect.EdgeGlow -> {
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_band_width),
                                    value = alert.edgeGlow.bandWidthDp,
                                    range = FloatBallDefaults.AlertEdgeGlowBandWidthDpMin..
                                        FloatBallDefaults.AlertEdgeGlowBandWidthDpMax,
                                    display = { "${it.toInt()} dp" },
                                    index = row++,
                                    rowCount = alertRows,
                                    onCommit = { v -> updateAlert { it.copy(edgeGlow = it.edgeGlow.copy(bandWidthDp = v)) } },
                                )
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_brightness),
                                    value = alert.edgeGlow.brightness,
                                    range = FloatBallDefaults.AlertBrightnessMin..FloatBallDefaults.AlertBrightnessMax,
                                    display = { "${it.toInt()}%" },
                                    index = row++,
                                    rowCount = alertRows,
                                    onCommit = { v -> updateAlert { it.copy(edgeGlow = it.edgeGlow.copy(brightness = v)) } },
                                )
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_cycles),
                                    value = alert.edgeGlow.cycles,
                                    range = FloatBallDefaults.AlertCyclesMin..FloatBallDefaults.AlertCyclesMax,
                                    display = { "${it.toInt()} ×" },
                                    index = row++,
                                    rowCount = alertRows,
                                    onCommit = { v -> updateAlert { it.copy(edgeGlow = it.edgeGlow.copy(cycles = v)) } },
                                )
                            }

                            NotificationAlertEffect.TopParticles -> {
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_particle_count),
                                    value = alert.particles.count,
                                    range = FloatBallDefaults.AlertParticlesCountMin..
                                        FloatBallDefaults.AlertParticlesCountMax,
                                    display = { it.toInt().toString() },
                                    index = row++,
                                    rowCount = alertRows,
                                    onCommit = { v -> updateAlert { it.copy(particles = it.particles.copy(count = v)) } },
                                )
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_brightness),
                                    value = alert.particles.brightness,
                                    range = FloatBallDefaults.AlertBrightnessMin..FloatBallDefaults.AlertBrightnessMax,
                                    display = { "${it.toInt()}%" },
                                    index = row++,
                                    rowCount = alertRows,
                                    onCommit = { v -> updateAlert { it.copy(particles = it.particles.copy(brightness = v)) } },
                                )
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_cycles),
                                    value = alert.particles.waves,
                                    range = FloatBallDefaults.AlertCyclesMin..FloatBallDefaults.AlertCyclesMax,
                                    display = { "${it.toInt()} ×" },
                                    index = row++,
                                    rowCount = alertRows,
                                    onCommit = { v -> updateAlert { it.copy(particles = it.particles.copy(waves = v)) } },
                                )
                            }

                            NotificationAlertEffect.BallRing -> {
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_travel),
                                    value = alert.ring.travel,
                                    range = FloatBallDefaults.AlertRingTravelMin..FloatBallDefaults.AlertRingTravelMax,
                                    display = { "${it.toInt()}%" },
                                    index = row++,
                                    rowCount = alertRows,
                                    onCommit = { v -> updateAlert { it.copy(ring = it.ring.copy(travel = v)) } },
                                )
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_brightness),
                                    value = alert.ring.brightness,
                                    range = FloatBallDefaults.AlertBrightnessMin..FloatBallDefaults.AlertBrightnessMax,
                                    display = { "${it.toInt()}%" },
                                    index = row++,
                                    rowCount = alertRows,
                                    onCommit = { v -> updateAlert { it.copy(ring = it.ring.copy(brightness = v)) } },
                                )
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_cycles),
                                    value = alert.ring.cycles,
                                    range = FloatBallDefaults.AlertCyclesMin..FloatBallDefaults.AlertCyclesMax,
                                    display = { "${it.toInt()} ×" },
                                    index = row++,
                                    rowCount = alertRows,
                                    onCommit = { v -> updateAlert { it.copy(ring = it.ring.copy(cycles = v)) } },
                                )
                            }

                            NotificationAlertEffect.CapsulePulse -> {
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_brightness),
                                    value = alert.pulse.brightness,
                                    range = FloatBallDefaults.AlertBrightnessMin..FloatBallDefaults.AlertBrightnessMax,
                                    display = { "${it.toInt()}%" },
                                    index = row++,
                                    rowCount = alertRows,
                                    onCommit = { v -> updateAlert { it.copy(pulse = it.pulse.copy(brightness = v)) } },
                                )
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_cycles),
                                    value = alert.pulse.cycles,
                                    range = FloatBallDefaults.AlertCyclesMin..FloatBallDefaults.AlertCyclesMax,
                                    display = { "${it.toInt()} ×" },
                                    index = row++,
                                    rowCount = alertRows,
                                    onCommit = { v -> updateAlert { it.copy(pulse = it.pulse.copy(cycles = v)) } },
                                )
                            }

                            NotificationAlertEffect.CapsuleShimmer -> {
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_band_width),
                                    value = alert.shimmer.bandWidth,
                                    range = FloatBallDefaults.AlertShimmerBandWidthMin..
                                        FloatBallDefaults.AlertShimmerBandWidthMax,
                                    display = { "${it.toInt()}%" },
                                    index = row++,
                                    rowCount = alertRows,
                                    onCommit = { v -> updateAlert { it.copy(shimmer = it.shimmer.copy(bandWidth = v)) } },
                                )
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_brightness),
                                    value = alert.shimmer.brightness,
                                    range = FloatBallDefaults.AlertBrightnessMin..FloatBallDefaults.AlertBrightnessMax,
                                    display = { "${it.toInt()}%" },
                                    index = row++,
                                    rowCount = alertRows,
                                    onCommit = { v -> updateAlert { it.copy(shimmer = it.shimmer.copy(brightness = v)) } },
                                )
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_cycles),
                                    value = alert.shimmer.cycles,
                                    range = FloatBallDefaults.AlertCyclesMin..FloatBallDefaults.AlertCyclesMax,
                                    display = { "${it.toInt()} ×" },
                                    index = row++,
                                    rowCount = alertRows,
                                    onCommit = { v -> updateAlert { it.copy(shimmer = it.shimmer.copy(cycles = v)) } },
                                )
                            }
                        }
                        // 动效独立颜色：默认主色（与胶囊 / 悬浮球解耦），已自定义时给重置行。
                        LunoSettingsRow(
                            title = stringResource(R.string.float_ball_capsule_alert_color),
                            icon = Icons.Default.Palette,
                            subtitle = stringResource(
                                if (alert.color.isUnset) {
                                    R.string.float_ball_capsule_color_default
                                } else {
                                    R.string.float_ball_capsule_color_custom
                                },
                            ),
                            shape = segmentedShape(row++, alertRows),
                            leading = {
                                Box(
                                    modifier = Modifier
                                        .size(MarkColorSize)
                                        .background(
                                            color = if (alert.color.isUnset) {
                                                scheme.primary
                                            } else {
                                                alert.color.toMarkColor()
                                            },
                                            shape = CircleShape,
                                        ),
                                )
                            },
                            onClick = { showAlertColorPicker = true },
                        )
                        if (!alert.color.isUnset) {
                            LunoSettingsRow(
                                title = stringResource(R.string.float_ball_capsule_color_reset),
                                icon = Icons.Default.Refresh,
                                shape = segmentedShape(row++, alertRows),
                                onClick = { updateAlert { it.copy(color = ColorSpec.Unset) } },
                            )
                        }
                        // 试播：按当前配置立即播一次，不等真通知（用户显式点击即授权）。
                        LunoSettingsRow(
                            title = stringResource(R.string.float_ball_capsule_alert_preview),
                            subtitle = stringResource(R.string.float_ball_capsule_alert_preview_subtitle),
                            icon = Icons.Default.PlayArrow,
                            shape = segmentedShape(row, alertRows),
                            onClick = { facades.system.previewFloatBallAlert(context) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 提示动效的专属旋钮滑杆：拖动只改本地预览，松手一次性落盘（滑杆交互口径）。
 * [range] 来自 FloatBallDefaults 的滑杆约束常量。
 */
@Composable
private fun AlertKnobSlider(
    label: String,
    value: Int,
    range: IntRange,
    display: @Composable (Float) -> String,
    index: Int,
    rowCount: Int,
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
        shape = segmentedShape(index, rowCount),
    )
}

/** 提示动效各形态的展示名（单选下拉用，选项顺序 = 枚举声明顺序）。 */
private fun NotificationAlertEffect.labelRes(): Int = when (this) {
    NotificationAlertEffect.EdgeGlow -> R.string.float_ball_capsule_alert_effect_edge_glow
    NotificationAlertEffect.TopParticles -> R.string.float_ball_capsule_alert_effect_particles
    NotificationAlertEffect.CapsulePulse -> R.string.float_ball_capsule_alert_effect_pulse
    NotificationAlertEffect.BallRing -> R.string.float_ball_capsule_alert_effect_ring
    NotificationAlertEffect.CapsuleShimmer -> R.string.float_ball_capsule_alert_effect_shimmer
}
