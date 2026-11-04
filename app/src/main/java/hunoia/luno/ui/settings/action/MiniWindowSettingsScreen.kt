package hunoia.luno.ui.settings.action

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.miniWindowSettings
import hunoia.luno.config.model.withMiniWindowSettings
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** 小窗设置列表：跟随触发位置、跟随边距、固定位置与大小入口。 */
@Composable
fun MiniWindowSettingsScreen(
    onBack: () -> Unit,
    onAdjustBounds: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val advanced by TuneConfig.advanced.collectAsStateWithLifecycle(
        initialValue = AdvancedSettings()
    )
    val settings = advanced.miniWindowSettings()
    var gapDp by remember(settings.followTriggerGapDp) {
        mutableFloatStateOf(settings.followTriggerGapDp.toFloat())
    }

    LunoScreen(title = stringResource(R.string.mini_window_settings_title), onBack = onBack) {
        LunoGroup(title = stringResource(R.string.mini_window_settings_group)) {
            LunoSwitchRow(
                title = stringResource(R.string.mini_window_follow_trigger),
                subtitle = stringResource(R.string.mini_window_follow_trigger_hint),
                checked = settings.followTrigger,
                onCheckedChange = { enabled ->
                    scope.launch {
                        TuneConfig.updateAdvanced { current ->
                            current.withMiniWindowSettings(
                                current.miniWindowSettings().copy(followTrigger = enabled)
                            )
                        }
                    }
                },
                shape = segmentedShape(0, 2),
            )
            LunoTextSlider(
                value = gapDp,
                onValueChange = { gapDp = it },
                onValueChangeFinished = {
                    scope.launch {
                        TuneConfig.updateAdvanced { current ->
                            current.withMiniWindowSettings(
                                current.miniWindowSettings().copy(
                                    followTriggerGapDp = gapDp.roundToInt(),
                                )
                            )
                        }
                    }
                },
                text = stringResource(R.string.mini_window_follow_gap),
                valueDisplay = {
                    stringResource(R.string.mini_window_follow_gap_value, it.roundToInt())
                },
                valueRange = 0f..48f,
                enabled = settings.followTrigger,
                shape = segmentedShape(1, 2),
            )
        }
        LunoGroup {
            LunoSettingsRow(
                icon = Icons.Default.Crop,
                title = stringResource(R.string.mini_window_position_size),
                subtitle = stringResource(R.string.mini_window_position_size_hint),
                shape = segmentedShape(0, 1),
                onClick = onAdjustBounds,
            )
        }
    }
}
