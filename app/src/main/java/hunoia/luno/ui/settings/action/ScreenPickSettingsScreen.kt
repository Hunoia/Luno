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
import hunoia.luno.config.model.OcrScript
import hunoia.luno.config.model.ScreenPickGesture
import hunoia.luno.domain.AppGraph
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.domain.ocr.OcrModelState
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
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
    onNavToOcrModels: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val ocrModels by AppGraph.ocrModels.state.collectAsStateWithLifecycle()
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
                shape = segmentedShape(0, 5),
            )
            LunoSwitchRow(
                title = stringResource(R.string.screen_pick_keep_after_release),
                subtitle = stringResource(R.string.screen_pick_keep_after_release_hint),
                checked = screenPick.keepAfterRelease,
                onCheckedChange = { checked -> commit(screenPick.copy(keepAfterRelease = checked)) },
                shape = segmentedShape(1, 5),
            )
            ScreenPickSlider(
                value = screenPick.longPressDelayMs.toFloat(),
                range = 200f..1_000f,
                text = stringResource(R.string.screen_pick_long_press_delay),
                display = { stringResource(R.string.screen_pick_long_press_delay_value, it.roundToInt()) },
                shape = segmentedShape(2, 5),
                onCommit = { commit(screenPick.copy(longPressDelayMs = it.roundToInt())) },
            )
            ScreenPickSlider(
                value = screenPick.slopDp,
                range = 2f..40f,
                text = stringResource(R.string.screen_pick_slop),
                display = { stringResource(R.string.screen_pick_slop_value, it.roundToInt()) },
                shape = segmentedShape(3, 5),
                onCommit = { commit(screenPick.copy(slopDp = it.roundToInt().toFloat())) },
            )
            ScreenPickSlider(
                value = screenPick.triggerCooldownMs.toFloat(),
                range = 0f..3_000f,
                text = stringResource(R.string.screen_pick_trigger_interval),
                display = { stringResource(R.string.screen_pick_trigger_interval_value, it.roundToInt()) },
                shape = segmentedShape(4, 5),
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
        LunoGroup(title = stringResource(R.string.screen_pick_ocr_group)) {
            LunoSwitchRow(
                title = stringResource(R.string.screen_pick_ocr_enabled),
                subtitle = stringResource(R.string.screen_pick_ocr_enabled_hint),
                checked = screenPick.ocrEnabled,
                onCheckedChange = { checked -> commit(screenPick.copy(ocrEnabled = checked)) },
                shape = segmentedShape(0, 3),
            )
            val toggleScript: (OcrScript) -> Unit = { script ->
                val toggled = if (script in screenPick.ocrScripts) {
                    screenPick.ocrScripts - script
                } else {
                    screenPick.ocrScripts + script
                }
                // 固定枚举顺序，且不允许清空（空集合在运行时回退到中文，但 UI 上别让人以为关掉了）。
                val ordered = OcrScript.entries.filter { it in toggled }.ifEmpty {
                    listOf(OcrScript.Chinese)
                }
                commit(screenPick.copy(ocrScripts = ordered))
            }
            LunoSwitchRow(
                title = stringResource(R.string.screen_pick_ocr_chinese),
                checked = OcrScript.Chinese in screenPick.ocrScripts,
                onCheckedChange = { toggleScript(OcrScript.Chinese) },
                shape = segmentedShape(1, 3),
            )
            LunoSwitchRow(
                title = stringResource(R.string.screen_pick_ocr_korean),
                subtitle = stringResource(R.string.screen_pick_ocr_script_hint),
                checked = OcrScript.Korean in screenPick.ocrScripts,
                onCheckedChange = { toggleScript(OcrScript.Korean) },
                shape = segmentedShape(2, 3),
            )
        }
        LunoGroup(title = stringResource(R.string.screen_pick_ocr_model)) {
            LunoSettingsRow(
                title = stringResource(R.string.screen_pick_ocr_model),
                subtitle = when (ocrModels.pp.state) {
                    is OcrModelState.Ready -> stringResource(R.string.ocr_model_summary_pp_ready)
                    is OcrModelState.Failed -> stringResource(R.string.ocr_model_pp_error)
                    else -> stringResource(R.string.ocr_model_summary_pp_missing)
                },
                onClick = onNavToOcrModels,
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
