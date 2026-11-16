package hunoia.luno.ui.settings.action

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import kotlinx.coroutines.launch

/**
 * 动作小设置聚合页：最近应用 + 手势工具（后续动作小设置并入）。
 *
 * 最近应用（黑名单 / 灵敏度 / 图标大小）与手势工具（音量 / 临时隐藏）共一页；
 * 布局（竖向/横向）不在这里：不同触钮可以绑不同方向，已拆成选择页里的两条动作。
 */
@Composable
fun RecentsGestureSettingsScreen(
    onBack: () -> Unit,
    onNavToBlocklist: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val actionSettings by TuneConfig.action.collectAsStateWithLifecycle(initialValue = ActionSettings())
    val tools = actionSettings.gestureTools

    fun update(transform: (hunoia.luno.config.model.GestureToolsSettings) -> hunoia.luno.config.model.GestureToolsSettings) {
        scope.launch { TuneConfig.updateAction { it.copy(gestureTools = transform(it.gestureTools)) } }
    }

    LunoScreen(title = stringResource(R.string.recents_gesture_settings_title), onBack = onBack) {
        LunoGroup(title = stringResource(R.string.recents_settings_motion_group)) {
            LunoSettingsRow(
                icon = Icons.Default.FormatListBulleted,
                title = stringResource(R.string.recent_apps_blocklist),
                subtitle = stringResource(
                    R.string.recents_settings_blocklist_count,
                    actionSettings.recentAppsBlocklist.size,
                ),
                shape = segmentedShape(0, 3),
                onClick = onNavToBlocklist,
            )
            LunoTextSlider(
                value = tools.recentsSensitivityPercent.toFloat(),
                onValueChange = { value ->
                    update { it.copy(recentsSensitivityPercent = value.toInt()) }
                },
                text = stringResource(R.string.recents_settings_sensitivity),
                valueDisplay = { "${it.toInt() / 100.0}x" },
                valueRange = 50f..300f,
                shape = segmentedShape(1, 3),
            )
            LunoTextSlider(
                value = tools.recentsIconSizeDp.toFloat(),
                onValueChange = { value ->
                    update { it.copy(recentsIconSizeDp = value.toInt()) }
                },
                text = stringResource(R.string.recents_settings_icon_size),
                valueDisplay = { "${it.toInt()}dp" },
                valueRange = 48f..108f,
                shape = segmentedShape(2, 3),
            )
        }
        LunoGroup(title = stringResource(R.string.gesture_tools_volume_group)) {
            LunoTextSlider(
                value = tools.volumeScrubStepThresholdDp.toFloat(),
                onValueChange = { value ->
                    update { it.copy(volumeScrubStepThresholdDp = value.toInt()) }
                },
                text = stringResource(R.string.gesture_tools_volume_scrub_sensitivity),
                valueDisplay = { "${it.toInt()}dp" },
                valueRange = 4f..64f,
                shape = segmentedShape(0, 1),
            )
        }
        LunoGroup(title = stringResource(R.string.gesture_tools_trigger_hide_group)) {
            LunoTextSlider(
                value = tools.triggerButtonHideMs.toFloat(),
                onValueChange = { value ->
                    update { it.copy(triggerButtonHideMs = value.toLong()) }
                },
                text = stringResource(R.string.gesture_tools_trigger_hide_duration),
                valueDisplay = { "${it.toInt()}ms" },
                valueRange = 500f..30000f,
                shape = segmentedShape(0, 1),
            )
        }
    }
}
