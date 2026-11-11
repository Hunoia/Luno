package hunoia.luno.ui.settings.action

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.ListStyle
import hunoia.luno.config.model.MotionSpeed
import hunoia.luno.config.model.MotionTuningSettings
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.ui.component.MotionTuningPreview
import hunoia.luno.ui.component.row.LunoDropdownRow
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.theme.toProfile
import kotlinx.coroutines.launch

/**
 * 外观设置页：列表风格与三类动效档位。
 *
 * 独立页面而非设置根页的一段：动效档位和预览都是可操作项，
 * 放在根页会把「功能入口」列表拉长，也会让预览在滚动中反复重播。
 * 每项都用下拉菜单（[LunoDropdownRow]）：档位标签较长，分段按钮会挤成省略号。
 */
@Composable
fun AppearanceSettingsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val advanced by TuneConfig.advanced
        .collectAsStateWithLifecycle(initialValue = AdvancedSettings())

    fun updateMotion(transform: (MotionTuningSettings) -> MotionTuningSettings) {
        scope.launch {
            TuneConfig.updateAdvanced { it.copy(motion = transform(it.motion)) }
        }
    }

    LunoScreen(title = stringResource(R.string.action_settings_appearance_group), onBack = onBack) {
        LunoGroup(title = stringResource(R.string.appearance_group_list)) {
            LunoDropdownRow(
                title = stringResource(R.string.action_settings_list_style),
                options = listOf(
                    stringResource(R.string.list_style_segmented),
                    stringResource(R.string.list_style_card_divided),
                ),
                selectedIndex = advanced.listStyle.ordinal,
                onSelect = { index ->
                    scope.launch {
                        TuneConfig.updateAdvanced { it.copy(listStyle = ListStyle.entries[index]) }
                    }
                },
                shape = segmentedShape(0, 1),
            )
        }
        LunoGroup(title = stringResource(R.string.action_settings_motion_group)) {
            LunoDropdownRow(
                title = stringResource(R.string.motion_panel_speed),
                options = motionSpeedOptions(),
                selectedIndex = advanced.motion.panel.ordinal,
                onSelect = { index ->
                    updateMotion { it.copy(panel = MotionSpeed.entries[index]) }
                },
                shape = segmentedShape(0, 3),
            )
            LunoDropdownRow(
                title = stringResource(R.string.motion_overlay_speed),
                options = motionSpeedOptions(),
                selectedIndex = advanced.motion.overlay.ordinal,
                onSelect = { index ->
                    updateMotion { it.copy(overlay = MotionSpeed.entries[index]) }
                },
                shape = segmentedShape(1, 3),
            )
            LunoDropdownRow(
                title = stringResource(R.string.motion_floating_speed),
                options = motionSpeedOptions(),
                selectedIndex = advanced.motion.floating.ordinal,
                onSelect = { index ->
                    updateMotion { it.copy(floating = MotionSpeed.entries[index]) }
                },
                shape = segmentedShape(2, 3),
            )
        }
        MotionTuningPreview(profile = advanced.motion.toProfile())
    }
}

@Composable
private fun motionSpeedOptions(): List<String> = listOf(
    stringResource(R.string.motion_speed_faster),
    stringResource(R.string.motion_speed_standard),
    stringResource(R.string.motion_speed_slower),
    stringResource(R.string.motion_speed_slowest),
)
