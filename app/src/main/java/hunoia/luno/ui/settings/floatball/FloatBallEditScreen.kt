package hunoia.luno.ui.settings.floatball

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.bridge.system.canDrawOverlays
import hunoia.luno.bridge.system.gotoOverlaySettings
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.config.model.FloatBallSettings
import hunoia.luno.domain.system.FloatBallUseCase
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.ui.component.actionTextCompose
import hunoia.luno.ui.component.gestureButtonMarkColor
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.sheet.ColorPickerBottomSheet
import hunoia.luno.ui.component.sheet.ColorSelection
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.navigation.ActionSelect
import hunoia.luno.ui.navigation.FloatBallSlot
import hunoia.luno.ui.theme.MarkColorSize
import hunoia.luno.ui.theme.resolveColor
import kotlinx.coroutines.launch

/**
 * 悬浮球设置页。首行开关兼服务启停；拖动直接改球位，无位置调整页、无拖动延时。
 * 未授权时顶部给横幅：球不出现的原因必须在配置入口喊出来。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatBallEditScreen(
    onBack: () -> Unit,
    onNavToActionSelect: (ActionSelect) -> Unit = {},
) {
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
            if (enabled) FloatBallUseCase.setEnabled(context, true) else FloatBallUseCase.setEnabled(context, false)
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
                    initialColor = Color(floatBall.color),
                    onColorSelected = { selection ->
                        when (selection) {
                            is ColorSelection.Custom -> update { it.copy(color = selection.color.toArgb()) }
                            is ColorSelection.Theme ->
                                update { it.copy(color = selection.key.resolveColor(scheme).toArgb()) }
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
                        subtitle = stringResource(R.string.float_ball_permission_hint),
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
                    onClick = { onNavToActionSelect(ActionSelect(floatBallSlot = FloatBallSlot.TAP)) },
                )
            }
        }

        item(key = "appearance") {
            LunoGroup(title = stringResource(R.string.float_ball_appearance)) {
                LunoTextSlider(
                    value = localSizeDp,
                    onValueChange = { value -> localSizeDp = value },
                    onValueChangeFinished = { update { it.copy(sizeDp = localSizeDp) } },
                    text = stringResource(R.string.float_ball_size),
                    valueDisplay = { "${it.toInt()} dp" },
                    valueRange = FloatBallDefaults.MinSizeDp..FloatBallDefaults.MaxSizeDp,
                    shape = segmentedShape(0, 2),
                )
                LunoSettingsRow(
                    title = stringResource(R.string.float_ball_color),
                    icon = Icons.Default.Palette,
                    shape = segmentedShape(1, 2),
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(MarkColorSize)
                                .background(
                                    color = gestureButtonMarkColor(floatBall.color),
                                    shape = CircleShape,
                                ),
                        )
                    },
                    onClick = { showColorPicker = true },
                )
            }
        }

        item(key = "idle") {
            LunoGroup {
                LunoTextSlider(
                    value = localIdleDelay,
                    onValueChange = { value -> localIdleDelay = value },
                    onValueChangeFinished = {
                        update { it.copy(idleDimDelayMs = localIdleDelay.toLong()) }
                    },
                    text = stringResource(R.string.float_ball_idle_dim_delay),
                    valueDisplay = { if (it <= 0f) stringResource(R.string.float_ball_off) else "${it.toInt()} ms" },
                    valueRange = 0f..8000f,
                    shape = segmentedShape(0, 1),
                )
            }
        }

        item(key = "behavior") {
            LunoGroup(title = stringResource(R.string.float_ball_behavior)) {
                LunoSwitchRow(
                    title = stringResource(R.string.float_ball_mirror),
                    subtitle = stringResource(R.string.float_ball_mirror_subtitle),
                    checked = floatBall.mirrorBall,
                    onCheckedChange = { checked -> update { it.copy(mirrorBall = checked) } },
                    shape = segmentedShape(0, 1),
                )
            }
        }
    }
}
