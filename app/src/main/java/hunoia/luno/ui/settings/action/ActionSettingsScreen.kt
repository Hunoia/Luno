package hunoia.luno.ui.settings.action
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.domain.search.SearchCenter
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.QuickSearchSettings
import hunoia.luno.config.model.defaultEngine
import hunoia.luno.config.model.miniWindowSettings
import hunoia.luno.config.model.withMiniWindowSettings
import kotlinx.coroutines.launch
import hunoia.luno.service.DispatchClient
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.ui.platform.LocalDensity
import hunoia.luno.config.model.ArcStyle
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.settings.gesture.style.ActionPanelStyleConfigContent
import kotlin.math.roundToInt
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.screen.LunoScreen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSettingsScreen(
    onBack: () -> Unit,
    onNavToSearchEngines: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var showActionPanelStyle by remember { mutableStateOf(false) }
    val actionSettings by TuneConfig.action.collectAsStateWithLifecycle(initialValue = ActionSettings())
    val advancedSettings by TuneConfig.advanced.collectAsStateWithLifecycle(initialValue = AdvancedSettings())
    val quickSearchSettings by SearchCenter.search.collectAsStateWithLifecycle(initialValue = QuickSearchSettings())
    LunoScreen(
        title = stringResource(R.string.action_settings),
        onBack = onBack,
        sheets = {
        if (showActionPanelStyle) {
                    LunoSheet(onDismissRequest = { showActionPanelStyle = false }, showDragHandle = true) {
                        ActionPanelStyleConfigContent(
                            currentStyle = actionSettings.actionPanelStyle,
                            onStyleChanged = { next ->
                                scope.launch { TuneConfig.updateAction { it.copy(actionPanelStyle = next) } }
                            },
                        )
                    }
                }
                

        },
    ) {

            LunoGroup {
                LunoSettingsRow(
                    icon = Icons.Default.Search,
                    title = stringResource(R.string.search_engine_settings),
                    subtitle = quickSearchSettings.defaultEngine().name,
                    onClick = onNavToSearchEngines,
                )
            }
            LunoGroup {
                LunoSettingsRow(
                    icon = Icons.Default.Gesture,
                    title = stringResource(R.string.action_panel_style),
                    subtitle = actionPanelStyleSummary(actionSettings.actionPanelStyle),
                    onClick = { showActionPanelStyle = true },
                )
            }
            LunoGroup {
                LunoSettingsRow(
                    icon = Icons.Default.Crop,
                    title = stringResource(R.string.mini_window_position_short),
                    subtitle = stringResource(R.string.mini_window_position_hint),
                    onClick = {
                        DispatchClient.showMiniWindowAdjust(
                            initial = advancedSettings.miniWindowSettings(),
                            onCommit = { next ->
                                scope.launch {
                                    TuneConfig.updateAdvanced { it.withMiniWindowSettings(next) }
                                }
                            },
                            onDismiss = {},
                        )
                    },
                )
            }
    }
}
/** 副标题摘要：图标大小 · 图标间距 · 弧线铺开。 */
@Composable
private fun actionPanelStyleSummary(style: ArcStyle): String {
    val density = LocalDensity.current
    val itemDp = with(density) { style.itemSize.toDp().value }.roundToInt()
    val gapDp = with(density) { style.itemGapPx.toDp().value }.roundToInt()
    return "$itemDp dp · $gapDp dp · ${(style.spreadSpacing * 100).roundToInt()}%"
}
