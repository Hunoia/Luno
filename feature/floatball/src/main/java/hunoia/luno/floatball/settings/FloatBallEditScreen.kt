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
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import hunoia.luno.floatball.R
import hunoia.luno.bridge.system.canDrawOverlays
import hunoia.luno.bridge.system.gotoOverlaySettings
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.config.model.FloatBallSettings
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.ui.facade.LocalFacades
import hunoia.luno.ui.component.actionTextCompose
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.sheet.ColorPickerBottomSheet
import hunoia.luno.ui.component.sheet.toColorSpec
import hunoia.luno.ui.component.sheet.toInitialSelection
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.theme.MarkColorSize
import hunoia.luno.ui.theme.toMarkColor
import kotlinx.coroutines.launch

/**
 * 悬浮球设置页。首行开关兼服务启停；拖动直接改球位，无位置调整页、无拖动延时。
 * 未授权时顶部给横幅：球不出现的原因必须在配置入口喊出来。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatBallEditScreen(
    onBack: () -> Unit,
    /** 去动作选择页配「单击」动作（导航意图由 :app 负责）。 */
    onEditTapAction: () -> Unit = {},
    onNavToNotificationBlocklist: () -> Unit = {},
) {
    val facades = LocalFacades.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val actionSettings by TuneConfig.action
        .collectAsStateWithLifecycle(initialValue = ActionSettings())
    val floatBall = actionSettings.floatBall

    // 滑杆拖动只改本地值，松手才落盘：每帧写 DataStore 会跨进程触发 :service 重组。
    var localSizeDp by remember(floatBall.sizeDp) { mutableFloatStateOf(floatBall.sizeDp) }
    var localIdleDelay by remember(floatBall.idleDimDelayMs) {
        mutableFloatStateOf(floatBall.idleDimDelayMs.toFloat())
    }
    var localCapsuleAutoHide by remember(floatBall.notificationCapsule.autoHideMs) {
        mutableFloatStateOf(floatBall.notificationCapsule.autoHideMs.toFloat())
    }
    var localCapsuleSuppressMs by remember(floatBall.notificationCapsule.dismissSuppressMs) {
        mutableFloatStateOf(floatBall.notificationCapsule.dismissSuppressMs.toFloat())
    }
    var localClipballAutoHide by remember(floatBall.clipboardBall.autoHideMs) {
        mutableFloatStateOf(floatBall.clipboardBall.autoHideMs.toFloat())
    }
    var localNotifTtl by remember(floatBall.inbox.notificationTtlMs) {
        mutableFloatStateOf(floatBall.inbox.notificationTtlMs.toFloat())
    }
    var localClipTtl by remember(floatBall.inbox.clipboardTtlMs) {
        mutableFloatStateOf(floatBall.inbox.clipboardTtlMs.toFloat())
    }
    var localInboxCapacity by remember(floatBall.inbox.capacity) {
        mutableFloatStateOf(floatBall.inbox.capacity.toFloat())
    }

    var overlayGranted by remember { mutableStateOf(context.canDrawOverlays()) }
    var notifAccessGranted by remember {
        mutableStateOf(facades.system.isNotificationAccessGranted(context))
    }
    var showColorPicker by remember { mutableStateOf(false) }

    // 从系统悬浮窗设置回来时重查（授权与撤销都要）：横幅消隐要跟手，服务也要 nudge ——
    // 授权回来服务要从无权限的隐藏态亮回；撤销时系统已拆走悬浮窗，nudge 让服务摘窗回收，
    // 否则 ballVisible 停在 true，之后重新授权也亮不回来。
    // 通知使用权同场重查（通知胶囊的授权横幅消隐）。
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

    fun toggle(enabled: Boolean) {
        scope.launch {
            runCatching {
                TuneConfig.updateAction { it.copy(floatBall = it.floatBall.copy(enabled = enabled)) }
            }
            if (enabled) facades.system.setFloatBallEnabled(context, true) else facades.system.setFloatBallEnabled(context, false)
        }
    }

    LunoScreenLazy(
        title = stringResource(R.string.float_ball),
        onBack = onBack,
        state = listState,
        sheets = {
            if (showColorPicker) {
                val scheme = MaterialTheme.colorScheme
                ColorPickerBottomSheet(
                    onDismissRequest = { showColorPicker = false },
                    onCopyColor = { hex ->
                        hunoia.luno.clipboard.ClipboardCenter.writeTextImmediate(hex, "Luno color")
                    },
                    initialSelection = floatBall.colorSpec.toInitialSelection(
                        scheme = scheme,
                        primaryFallback = true,
                    ),
                    onColorSelected = { selection ->
                        val colorSpec = selection.toColorSpec(scheme)
                        update {
                            it.copy(
                                color = colorSpec.customColor,
                                colorSource = colorSpec.source,
                                colorThemeKey = colorSpec.themeKey,
                            )
                        }
                        showColorPicker = false
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

        item(key = "toggle") {
            LunoGroup {
                LunoSwitchRow(
                    title = stringResource(R.string.float_ball_enabled),
                    checked = floatBall.enabled,
                    onCheckedChange = { toggle(it) },
                    shape = segmentedShape(0, 1),
                )
            }
        }

        item(key = "trigger") {
            LunoGroup(title = stringResource(R.string.float_ball_trigger_actions)) {
                LunoSettingsRow(
                    title = stringResource(R.string.float_ball_tap_action),
                    subtitle = floatBall.tapAction?.actionTextCompose()
                        ?: stringResource(R.string.float_ball_tap_unset),
                    icon = Icons.Default.TouchApp,
                    shape = segmentedShape(0, 1),
                    onClick = onEditTapAction,
                )
            }
        }

        item(key = "appearance") {
            LunoGroup(title = stringResource(R.string.float_ball_appearance)) {
                LunoSettingsRow(
                    title = stringResource(R.string.float_ball_color),
                    icon = Icons.Default.Palette,
                    shape = segmentedShape(0, 3),
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(MarkColorSize)
                                .background(
                                    color = floatBall.colorSpec.toMarkColor(),
                                    shape = CircleShape,
                                ),
                        )
                    },
                    onClick = { showColorPicker = true },
                )
                LunoTextSlider(
                    value = localSizeDp,
                    onValueChange = { value -> localSizeDp = value },
                    onValueChangeFinished = { update { it.copy(sizeDp = localSizeDp) } },
                    text = stringResource(R.string.float_ball_size),
                    valueDisplay = { "${it.toInt()} dp" },
                    valueRange = FloatBallDefaults.MinSizeDp..FloatBallDefaults.MaxSizeDp,
                    shape = segmentedShape(1, 3),
                )
                LunoTextSlider(
                    value = localIdleDelay,
                    onValueChange = { value -> localIdleDelay = value },
                    onValueChangeFinished = {
                        update { it.copy(idleDimDelayMs = localIdleDelay.toLong()) }
                    },
                    text = stringResource(R.string.float_ball_idle_dim_delay),
                    valueDisplay = { if (it <= 0f) stringResource(R.string.float_ball_off) else "${it.toInt()} ms" },
                    valueRange = FloatBallDefaults.IdleDimDelayMsMin.toFloat()..
                        FloatBallDefaults.IdleDimDelayMsMax.toFloat(),
                    shape = segmentedShape(2, 3),
                )
            }
        }

        item(key = "behavior") {
            LunoGroup(title = stringResource(R.string.float_ball_behavior)) {
                LunoSwitchRow(
                    title = stringResource(R.string.float_ball_follow_hand),
                    subtitle = stringResource(R.string.float_ball_follow_hand_subtitle),
                    checked = floatBall.followHand,
                    onCheckedChange = { checked -> update { it.copy(followHand = checked) } },
                    shape = segmentedShape(0, 1),
                )
            }
        }

        // 通知胶囊开了但没通知使用权：胶囊收不到任何通知，横幅必须喊出来。
        if (floatBall.notificationCapsule.enabled && !notifAccessGranted) {
            item(key = "capsule_permission") {
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

        item(key = "capsule") {
            val capsule = floatBall.notificationCapsule
            val suppress = capsule.dismissSuppressEnabled
            // 抑制时长只在开关打开时出现：条数会变，段形按实际条数算。
            val rowCount = if (suppress) 6 else 5
            LunoGroup(title = stringResource(R.string.float_ball_capsule_group)) {
                LunoSwitchRow(
                    title = stringResource(R.string.float_ball_capsule_enabled),
                    checked = capsule.enabled,
                    onCheckedChange = { checked ->
                        update { it.copy(notificationCapsule = it.notificationCapsule.copy(enabled = checked)) }
                        // 胶囊开关也要服务在跑：开 = 拉起；关 = nudge 重评（服务自决定去留）。
                        if (checked) {
                            facades.system.setFloatBallEnabled(context, true)
                        } else {
                            facades.system.setFloatBallEnabled(context, false)
                        }
                    },
                    shape = segmentedShape(0, rowCount),
                )
                LunoTextSlider(
                    value = localCapsuleAutoHide,
                    onValueChange = { value -> localCapsuleAutoHide = value },
                    onValueChangeFinished = {
                        update {
                            it.copy(
                                notificationCapsule = it.notificationCapsule.copy(
                                    autoHideMs = localCapsuleAutoHide.toLong(),
                                ),
                            )
                        }
                    },
                    text = stringResource(R.string.float_ball_capsule_auto_hide),
                    valueDisplay = { "${(it / 1000).toInt()} s" },
                    valueRange = FloatBallDefaults.CapsuleAutoHideMsMin.toFloat()..
                        FloatBallDefaults.CapsuleAutoHideMsMax.toFloat(),
                    shape = segmentedShape(1, rowCount),
                )
                LunoTextSlider(
                    value = localNotifTtl,
                    onValueChange = { value -> localNotifTtl = value },
                    onValueChangeFinished = {
                        update {
                            it.copy(inbox = it.inbox.copy(notificationTtlMs = localNotifTtl.toLong()))
                        }
                    },
                    text = stringResource(R.string.float_ball_capsule_ttl),
                    valueDisplay = { "${(it / 1000).toInt()} s" },
                    valueRange = FloatBallDefaults.InboxNotificationTtlMsMin.toFloat()..
                        FloatBallDefaults.InboxNotificationTtlMsMax.toFloat(),
                    shape = segmentedShape(2, rowCount),
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
                    shape = segmentedShape(3, rowCount),
                )
                if (suppress) {
                    LunoTextSlider(
                        value = localCapsuleSuppressMs,
                        onValueChange = { value -> localCapsuleSuppressMs = value },
                        onValueChangeFinished = {
                            update {
                                it.copy(
                                    notificationCapsule = it.notificationCapsule.copy(
                                        dismissSuppressMs = localCapsuleSuppressMs.toLong(),
                                    ),
                                )
                            }
                        },
                        text = stringResource(R.string.float_ball_capsule_suppress_duration),
                        valueDisplay = { "${(it / 1000).toInt()} s" },
                        valueRange = FloatBallDefaults.CapsuleDismissSuppressMsMin.toFloat()..
                            FloatBallDefaults.CapsuleDismissSuppressMsMax.toFloat(),
                        shape = segmentedShape(4, rowCount),
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
                    shape = segmentedShape(if (suppress) 5 else 4, rowCount),
                    onClick = onNavToNotificationBlocklist,
                )
            }
        }

        item(key = "clipboard_ball") {
            val clipball = floatBall.clipboardBall
            LunoGroup(title = stringResource(R.string.float_ball_clipball_group)) {
                LunoSwitchRow(
                    title = stringResource(R.string.float_ball_clipball_enabled),
                    subtitle = stringResource(R.string.float_ball_clipball_enabled_subtitle),
                    checked = clipball.enabled,
                    onCheckedChange = { checked ->
                        update { it.copy(clipboardBall = it.clipboardBall.copy(enabled = checked)) }
                        // 同胶囊：开关也要服务在跑；关 = nudge 重评（服务自决定去留）。
                        if (checked) {
                            facades.system.setFloatBallEnabled(context, true)
                        } else {
                            facades.system.setFloatBallEnabled(context, false)
                        }
                    },
                    shape = segmentedShape(0, 4),
                )
                LunoTextSlider(
                    value = localClipballAutoHide,
                    onValueChange = { value -> localClipballAutoHide = value },
                    onValueChangeFinished = {
                        update {
                            it.copy(
                                clipboardBall = it.clipboardBall.copy(
                                    autoHideMs = localClipballAutoHide.toLong(),
                                ),
                            )
                        }
                    },
                    text = stringResource(R.string.float_ball_clipball_auto_hide),
                    valueDisplay = { "${(it / 1000).toInt()} s" },
                    valueRange = FloatBallDefaults.ClipboardBallAutoHideMsMin.toFloat()..
                        FloatBallDefaults.ClipboardBallAutoHideMsMax.toFloat(),
                    shape = segmentedShape(1, 4),
                )
                LunoTextSlider(
                    value = localClipTtl,
                    onValueChange = { value -> localClipTtl = value },
                    onValueChangeFinished = {
                        update {
                            it.copy(inbox = it.inbox.copy(clipboardTtlMs = localClipTtl.toLong()))
                        }
                    },
                    text = stringResource(R.string.float_ball_clipball_ttl),
                    valueDisplay = { "${(it / 1000).toInt()} s" },
                    valueRange = FloatBallDefaults.InboxClipboardTtlMsMin.toFloat()..
                        FloatBallDefaults.InboxClipboardTtlMsMax.toFloat(),
                    shape = segmentedShape(2, 4),
                )
                LunoSwitchRow(
                    title = stringResource(R.string.float_ball_clipball_images),
                    checked = clipball.respondToImages,
                    onCheckedChange = { checked ->
                        update { it.copy(clipboardBall = it.clipboardBall.copy(respondToImages = checked)) }
                    },
                    shape = segmentedShape(3, 4),
                )
            }
        }

        item(key = "inbox") {
            LunoGroup(title = stringResource(R.string.float_ball_inbox_group)) {
                LunoTextSlider(
                    value = localInboxCapacity,
                    onValueChange = { value -> localInboxCapacity = value },
                    onValueChangeFinished = {
                        update { it.copy(inbox = it.inbox.copy(capacity = localInboxCapacity.toInt())) }
                    },
                    text = stringResource(R.string.float_ball_inbox_capacity),
                    valueDisplay = { "${it.toInt()} 项" },
                    valueRange = FloatBallDefaults.InboxCapacityMin.toFloat()..
                        FloatBallDefaults.InboxCapacityMax.toFloat(),
                    shape = segmentedShape(0, 1),
                )
            }
        }

    }
}
