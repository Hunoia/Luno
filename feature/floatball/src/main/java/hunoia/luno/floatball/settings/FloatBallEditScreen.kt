package hunoia.luno.floatball.settings


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
 * 悬浮球设置页：只负责主球自身外观与单击动作。
 *
 * 通知胶囊与建议胶囊各自独立成页，从动作设置的「功能」组并列进入。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatBallEditScreen(
    onBack: () -> Unit,
    /** 去动作选择页配「单击」动作（导航意图由 :app 负责）。 */
    onEditTapAction: () -> Unit = {},
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

    var overlayGranted by remember { mutableStateOf(context.canDrawOverlays()) }
    var showColorPicker by remember { mutableStateOf(false) }

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
    }
}
