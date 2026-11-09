package hunoia.luno.ui.settings.action

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.miniWindowSettings
import hunoia.luno.config.model.withMiniWindowSettings
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSegmentedGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.SurfacePlacementSliders
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import kotlinx.coroutines.launch

/** 小窗设置列表：跟随触发位置 + 三个边距（当前朝向）、固定位置与大小入口。 */
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
    // 三个边距横竖屏各一份：这里编辑当前朝向那份（与网格面板编辑页同口径）。
    val landscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE

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
                shape = segmentedShape(0, 4),
            )
            SurfacePlacementSliders(
                placement = settings.placement(landscape),
                enabled = settings.followTrigger,
                shapeAt = { index -> segmentedShape(index + 1, 4) },
                onCommit = { next ->
                    scope.launch {
                        TuneConfig.updateAdvanced { current ->
                            current.withMiniWindowSettings(
                                current.miniWindowSettings().withPlacement(landscape, next)
                            )
                        }
                    }
                },
            )
        }
        LunoGroup {
            LunoSegmentedGroup {
                item { shape -> LunoSettingsRow(
                icon = Icons.Default.Crop,
                title = stringResource(R.string.mini_window_position_size),
                subtitle = stringResource(R.string.mini_window_position_size_hint),
                shape = shape,
                onClick = onAdjustBounds,
                ) }
            }
        }
    }
}
