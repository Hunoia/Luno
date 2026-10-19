package hunoia.luno.ui.settings.action
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness1
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.GridView
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
import hunoia.luno.config.model.QuickSearchSettings
import hunoia.luno.config.model.defaultEngine
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.ui.platform.LocalDensity
import hunoia.luno.config.model.ArcStyle
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.component.input.LunoTextSlider
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
    onNavToActionPanels: () -> Unit = {},
    onNavToGridPanels: () -> Unit = {},
    onNavToMiniWindowAdjust: () -> Unit = {},
    onNavToFloatBallSettings: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var showActionPanelStyle by remember { mutableStateOf(false) }
    var showPointer by remember { mutableStateOf(false) }
    val actionSettings by TuneConfig.action.collectAsStateWithLifecycle(initialValue = ActionSettings())
    val gestureSettings by TuneConfig.gesture.collectAsStateWithLifecycle(initialValue = GestureSettings())
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
        if (showPointer) {
                    LunoSheet(onDismissRequest = { showPointer = false }, showDragHandle = true) {
                        PointerControls(
                            pointer = gestureSettings.pointer,
                            onCommit = { next ->
                                scope.launch { TuneConfig.updateGesture { it.copy(pointer = next) } }
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
                    icon = Icons.Default.RadioButtonChecked,
                    title = stringResource(R.string.action_panels),
                    subtitle = stringResource(R.string.action_panel_items_count, actionSettings.actionPanels.panels.sumOf { it.items.size }),
                    onClick = onNavToActionPanels,
                )
                LunoSettingsRow(
                    icon = Icons.Default.GridView,
                    title = stringResource(R.string.grid_panels),
                    subtitle = stringResource(R.string.action_panel_items_count, actionSettings.actionGridPanels.panels.sumOf { it.items.size }),
                    onClick = onNavToGridPanels,
                )
                LunoSettingsRow(
                    icon = Icons.Filled.Brightness1,
                    title = stringResource(R.string.float_ball),
                    subtitle = stringResource(R.string.float_ball_entry_hint),
                    onClick = onNavToFloatBallSettings,
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
                    onClick = { onNavToMiniWindowAdjust() },
                )
            }
            LunoGroup {
                LunoSettingsRow(
                    icon = Icons.Default.TouchApp,
                    title = stringResource(R.string.pointer),
                    subtitle = stringResource(R.string.pointer_hint),
                    onClick = { showPointer = true },
                )
            }
    }
}

/** 指针手感/外观滑杆组：拖动改草稿，松手统一落盘。 */
@Composable
private fun PointerControls(
    pointer: GestureSettings.Pointer,
    onCommit: (GestureSettings.Pointer) -> Unit,
) {
    var draft by remember(pointer) { mutableStateOf(pointer) }
    LunoTextSlider(
        value = draft.sensitivityX,
        onValueChange = { draft = draft.copy(sensitivityX = it) },
        onValueChangeFinished = { onCommit(draft) },
        text = stringResource(id = R.string.pointer_sensitivity_x_plain),
        valueDisplay = { stringResource(id = R.string.pointer_sensitivity_x, it) },
        valueRange = 0.5f..4f,
    )
    LunoTextSlider(
        value = draft.sensitivityY,
        onValueChange = { draft = draft.copy(sensitivityY = it) },
        onValueChangeFinished = { onCommit(draft) },
        text = stringResource(id = R.string.pointer_sensitivity_y_plain),
        valueDisplay = { stringResource(id = R.string.pointer_sensitivity_y, it) },
        valueRange = 0.5f..4f,
    )
    LunoTextSlider(
        value = draft.acceleration,
        onValueChange = { draft = draft.copy(acceleration = it) },
        onValueChangeFinished = { onCommit(draft) },
        text = stringResource(id = R.string.pointer_acceleration_plain),
        valueDisplay = { stringResource(id = R.string.pointer_acceleration, it) },
        valueRange = 0f..2f,
    )
    // 光标大小是 Int：本地浮点过渡，松手取整提交。
    var localCursorSize by remember(pointer.cursorSizeDp) { mutableStateOf(pointer.cursorSizeDp.toFloat()) }
    LunoTextSlider(
        value = localCursorSize,
        onValueChange = { localCursorSize = it },
        onValueChangeFinished = {
            draft = draft.copy(cursorSizeDp = localCursorSize.roundToInt())
            onCommit(draft)
        },
        text = stringResource(id = R.string.pointer_cursor_size_plain),
        valueDisplay = { stringResource(id = R.string.pointer_cursor_size, it.roundToInt()) },
        valueRange = 12f..64f,
    )
    LunoTextSlider(
        value = draft.cursorAlpha,
        onValueChange = { draft = draft.copy(cursorAlpha = it) },
        onValueChangeFinished = { onCommit(draft) },
        text = stringResource(id = R.string.pointer_cursor_alpha_plain),
        valueDisplay = { stringResource(id = R.string.pointer_cursor_alpha, (it * 100).roundToInt()) },
        valueRange = 0.2f..1f,
    )
}
/** 副标题摘要：图标大小 · 图标间距 · 弧线铺开。 */
@Composable
private fun actionPanelStyleSummary(style: ArcStyle): String {
    val density = LocalDensity.current
    val itemDp = with(density) { style.itemSize.toDp().value }.roundToInt()
    val gapDp = with(density) { style.itemGapPx.toDp().value }.roundToInt()
    return "$itemDp dp · $gapDp dp · ${(style.spreadSpacing * 100).roundToInt()}%"
}
