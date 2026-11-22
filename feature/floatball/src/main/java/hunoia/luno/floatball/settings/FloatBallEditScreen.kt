package hunoia.luno.floatball.settings


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import hunoia.luno.config.model.BallCustomIcon
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
 * 悬浮球设置页：只负责主球自身外观与触发动作（单击 / 长按 / 拖入判定区）。
 *
 * 通知胶囊与结果胶囊各自独立成页，从动作设置的「功能」组并列进入；
 * 三颗球的手势能力互不混合。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatBallEditScreen(
    onBack: () -> Unit,
    /** 去动作选择页配「单击」动作（导航意图由 :app 负责）。 */
    onEditTapAction: () -> Unit = {},
    /** 去动作选择页配「长按」动作。 */
    onEditLongPressAction: () -> Unit = {},
    /** 去动作选择页配「拖入判定区松手」动作。 */
    onEditDropAction: () -> Unit = {},
    /** 去判定区调整页（位置/大小，横竖屏各一份）。 */
    onAdjustDropZone: () -> Unit = {},
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
    var localLongPressMs by remember(floatBall.longPressMs) {
        mutableFloatStateOf(floatBall.longPressMs.toFloat())
    }

    var overlayGranted by remember { mutableStateOf(context.canDrawOverlays()) }
    var showColorPicker by remember { mutableStateOf(false) }
    // 自定义图标：选图 → 裁剪 → 落盘统一由 BallCustomIconSetting 托管。
    val iconPicker = rememberBallIconPickerState()

    // 从系统悬浮窗设置回来时重查（授权与撤销都要）：横幅消隐要跟手，服务也要 nudge ——
    // 授权回来服务要从无权限的隐藏态亮回；撤销时系统已拆走悬浮窗，nudge 让服务摘窗回收，
    // 否则 ballVisible 停在 true，之后重新授权也亮不回来。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = context.canDrawOverlays()
                if (granted != overlayGranted) {
                    overlayGranted = granted
                    facades.system.refreshFloatBall(context)
                }
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

    /** 主球的扁平图标字段 ↔ 共用图标模型：内容球的同名字段各自独立，互不跟随。 */
    val mainIcon = BallCustomIcon(
        path = floatBall.customIconPath,
        scale = floatBall.customIconScale,
        offsetX = floatBall.customIconOffsetX,
        offsetY = floatBall.customIconOffsetY,
        alpha = floatBall.customIconAlpha,
    )

    fun writeMainIcon(icon: BallCustomIcon) {
        update {
            it.copy(
                customIconPath = icon.path,
                customIconScale = icon.scale,
                customIconOffsetX = icon.offsetX,
                customIconOffsetY = icon.offsetY,
                customIconAlpha = icon.alpha,
            )
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
            BallCustomIconSheet(
                context = context,
                picker = iconPicker,
                current = mainIcon,
                onChange = ::writeMainIcon,
            )
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
            LunoGroup(title = stringResource(R.string.float_ball)) {
                LunoSwitchRow(
                    title = stringResource(R.string.float_ball_enabled),
                    checked = floatBall.enabled,
                    onCheckedChange = { enabled ->
                        update { it.copy(enabled = enabled) }
                        facades.system.setFloatBallEnabled(context, enabled)
                    },
                    shape = segmentedShape(0, 1),
                )
            }
        }

        item(key = "trigger") {
            LunoGroup(title = stringResource(R.string.float_ball_trigger_actions)) {
                // 判定区振动提示行只在配了拖入动作时出现：无彩蛋时该开关无意义。
                val triggerRows = 5 + if (floatBall.dropAction != null) 1 else 0
                LunoSettingsRow(
                    title = stringResource(R.string.float_ball_tap_action),
                    subtitle = floatBall.tapAction?.actionTextCompose()
                        ?: stringResource(R.string.float_ball_tap_unset),
                    icon = Icons.Default.TouchApp,
                    shape = segmentedShape(0, triggerRows),
                    onClick = onEditTapAction,
                )
                LunoSettingsRow(
                    title = stringResource(R.string.float_ball_long_press_action),
                    subtitle = floatBall.longPressAction?.actionTextCompose()
                        ?: stringResource(R.string.float_ball_tap_unset),
                    icon = Icons.Default.Timer,
                    shape = segmentedShape(1, triggerRows),
                    onClick = onEditLongPressAction,
                )
                LunoTextSlider(
                    value = localLongPressMs,
                    onValueChange = { value -> localLongPressMs = value },
                    onValueChangeFinished = {
                        update { it.copy(longPressMs = localLongPressMs.toLong()) }
                    },
                    text = stringResource(R.string.float_ball_long_press_ms),
                    valueDisplay = { "${it.toInt()} ms" },
                    valueRange = FloatBallDefaults.LongPressMsMin.toFloat()..
                        FloatBallDefaults.LongPressMsMax.toFloat(),
                    shape = segmentedShape(2, triggerRows),
                )
                LunoSettingsRow(
                    title = stringResource(R.string.float_ball_drop_action),
                    subtitle = floatBall.dropAction?.actionTextCompose()
                        ?: stringResource(R.string.float_ball_tap_unset),
                    icon = Icons.Default.CenterFocusStrong,
                    shape = segmentedShape(3, triggerRows),
                    onClick = onEditDropAction,
                )
                LunoSettingsRow(
                    title = stringResource(R.string.float_ball_drop_zone_adjust),
                    subtitle = stringResource(R.string.float_ball_drop_zone_adjust_subtitle),
                    icon = Icons.Default.OpenWith,
                    shape = segmentedShape(4, triggerRows),
                    onClick = onAdjustDropZone,
                )
                if (floatBall.dropAction != null) {
                    LunoSwitchRow(
                        title = stringResource(R.string.float_ball_drop_zone_vibrate),
                        checked = floatBall.dropZoneVibrate,
                        onCheckedChange = { enabled ->
                            update { it.copy(dropZoneVibrate = enabled) }
                        },
                        shape = segmentedShape(5, triggerRows),
                    )
                }
            }
        }

        item(key = "appearance") {
            LunoGroup(title = stringResource(R.string.float_ball_appearance)) {
                val hasCustomIcon = floatBall.customIconPath != null
                // 行序：图标 → [恢复默认] → 颜色 → 大小 → 静置 → [透明度] → [静置暂停]。
                val extraRows = 2 + (if (hasCustomIcon) 1 else 0)
                val count = 4 + extraRows
                val afterIcon = 1 + if (hasCustomIcon) 1 else 0
                var localAlpha by remember {
                    mutableFloatStateOf(floatBall.customIconAlpha)
                }
                BallCustomIconRows(
                    context = context,
                    icon = mainIcon,
                    startIndex = 0,
                    rowCount = count,
                    onPick = iconPicker::pick,
                    onReset = { resetBallIcon(scope, mainIcon, ::writeMainIcon) },
                )
                LunoSettingsRow(
                    title = stringResource(R.string.float_ball_color),
                    icon = Icons.Default.Palette,
                    shape = segmentedShape(afterIcon, count),
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
                    shape = segmentedShape(afterIcon + 1, count),
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
                    shape = segmentedShape(afterIcon + 2, count),
                )
                // 透明度只在有自定义图标时有意义（默认图标自带 alpha 语义）。
                if (hasCustomIcon) {
                    LunoTextSlider(
                        value = localAlpha,
                        onValueChange = { value -> localAlpha = value },
                        onValueChangeFinished = {
                            update { it.copy(customIconAlpha = localAlpha) }
                        },
                        text = stringResource(R.string.float_ball_custom_icon_alpha),
                        valueDisplay = { "${(it * 100).toInt()}%" },
                        valueRange = FloatBallDefaults.CustomIconAlphaMin..FloatBallDefaults.CustomIconAlphaMax,
                        shape = segmentedShape(afterIcon + 3, count),
                    )
                    LunoSwitchRow(
                        title = stringResource(R.string.float_ball_custom_icon_pause_when_idle),
                        subtitle = stringResource(R.string.float_ball_custom_icon_pause_when_idle_subtitle),
                        checked = floatBall.customIconPauseWhenIdle,
                        onCheckedChange = { checked ->
                            update { it.copy(customIconPauseWhenIdle = checked) }
                        },
                        shape = segmentedShape(afterIcon + 4, count),
                    )
                }
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
    }
}

/** 自定义图标落盘目录（相对 filesDir）：与内容球共用，见 [BALL_ICON_DIR]。 */
