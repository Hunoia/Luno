package hunoia.luno.ui.settings.action

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.ScreenPickGesture
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 屏幕识别设置页：全屏长按识屏与幽灵卡参数。
 *
 * 动作设置根页只留一个入口行；参数多，单独一页避免根页变成参数堆。
 */
@Composable
fun ScreenPickSettingsScreen(
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val initialSettings by GestureConfig.initial.collectAsStateWithLifecycle(
        initialValue = InitialSettings(),
    )
    val advancedSettings by TuneConfig.advanced.collectAsStateWithLifecycle(
        initialValue = AdvancedSettings(),
    )
    val screenPick = initialSettings.screenPickGesture
    val commit: (ScreenPickGesture) -> Unit = { next ->
        scope.launch { GestureConfig.updateInitial { it.copy(screenPickGesture = next) } }
    }

    LunoScreen(title = stringResource(R.string.screen_pick_settings_title), onBack = onBack) {
        LunoGroup(title = stringResource(R.string.screen_pick_settings_group)) {
            LunoSwitchRow(
                title = stringResource(R.string.screen_pick_global_long_press),
                subtitle = stringResource(R.string.screen_pick_global_long_press_hint),
                checked = screenPick.enabled,
                onCheckedChange = { checked -> commit(screenPick.copy(enabled = checked)) },
                shape = segmentedShape(0, 6),
            )
            LunoSwitchRow(
                title = stringResource(R.string.screen_pick_keep_after_release),
                subtitle = stringResource(R.string.screen_pick_keep_after_release_hint),
                checked = screenPick.keepAfterRelease,
                onCheckedChange = { checked -> commit(screenPick.copy(keepAfterRelease = checked)) },
                shape = segmentedShape(1, 6),
            )
            ScreenPickSlider(
                value = screenPick.longPressDelayMs.toFloat(),
                range = 200f..1_000f,
                text = stringResource(R.string.screen_pick_long_press_delay),
                display = { stringResource(R.string.screen_pick_long_press_delay_value, it.roundToInt()) },
                shape = segmentedShape(2, 6),
                onCommit = { commit(screenPick.copy(longPressDelayMs = it.roundToInt())) },
            )
            ScreenPickSlider(
                value = screenPick.slopDp,
                range = 2f..40f,
                text = stringResource(R.string.screen_pick_slop),
                display = { stringResource(R.string.screen_pick_slop_value, it.roundToInt()) },
                shape = segmentedShape(3, 6),
                onCommit = { commit(screenPick.copy(slopDp = it.roundToInt().toFloat())) },
            )
            ScreenPickSlider(
                value = screenPick.dragStartTimeoutMs.toFloat(),
                range = 0f..5_000f,
                text = stringResource(R.string.screen_pick_drag_start_timeout),
                display = {
                    if (it.roundToInt() <= 0) {
                        stringResource(R.string.screen_pick_drag_start_timeout_off)
                    } else {
                        stringResource(R.string.screen_pick_drag_start_timeout_value, it.roundToInt())
                    }
                },
                shape = segmentedShape(4, 6),
                onCommit = { commit(screenPick.copy(dragStartTimeoutMs = it.roundToInt())) },
            )
            ScreenPickSlider(
                value = screenPick.triggerCooldownMs.toFloat(),
                range = 0f..3_000f,
                text = stringResource(R.string.screen_pick_trigger_interval),
                display = { stringResource(R.string.screen_pick_trigger_interval_value, it.roundToInt()) },
                shape = segmentedShape(5, 6),
                onCommit = { commit(screenPick.copy(triggerCooldownMs = it.roundToInt())) },
            )
        }
        LunoGroup(title = stringResource(R.string.screen_pick_settings_image_group)) {
            LunoSwitchRow(
                title = stringResource(R.string.screen_pick_save_to_gallery),
                subtitle = stringResource(R.string.screen_pick_save_to_gallery_hint),
                checked = advancedSettings.screenPickSaveToGallery,
                onCheckedChange = { checked ->
                    scope.launch {
                        TuneConfig.updateAdvanced { it.copy(screenPickSaveToGallery = checked) }
                    }
                },
                shape = segmentedShape(0, 1),
            )
        }
    }
}

@Composable
private fun ScreenPickSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    text: String,
    display: @Composable (Float) -> String,
    shape: Shape,
    onCommit: (Float) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value) }
    LunoTextSlider(
        value = draft,
        onValueChange = { draft = it },
        onValueChangeFinished = { onCommit(draft) },
        text = text,
        valueDisplay = { display(it) },
        valueRange = range,
        shape = shape,
    )
}
