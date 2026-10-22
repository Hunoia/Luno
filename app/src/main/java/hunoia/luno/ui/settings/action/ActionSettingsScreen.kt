package hunoia.luno.ui.settings.action
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.config.model.ActionSettings
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.History
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import hunoia.luno.config.model.ArcStyle
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.settings.gesture.style.ActionPanelStyleConfigContent
import kotlin.math.roundToInt
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.theme.ContentBottom
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSettingsScreen(
    onBack: () -> Unit,
    onNavToActionPanels: () -> Unit = {},
    onNavToGridPanels: () -> Unit = {},
    onNavToRecentAppsBlocklist: () -> Unit = {},
    onNavToMiniWindowAdjust: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var showActionPanelStyle by remember { mutableStateOf(false) }
    var showPointer by remember { mutableStateOf(false) }
    val actionSettings by TuneConfig.action.collectAsStateWithLifecycle(initialValue = ActionSettings())
    val gestureSettings by TuneConfig.gesture.collectAsStateWithLifecycle(initialValue = GestureSettings())
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

            LunoGroup(title = stringResource(R.string.action_settings_panel_group)) {
                LunoSettingsRow(
                    icon = Icons.Default.RadioButtonChecked,
                    title = stringResource(R.string.action_panels),
                    subtitle = stringResource(R.string.action_panel_count, actionSettings.actionPanels.panels.size),
                    shape = segmentedShape(0, 3),
                    onClick = onNavToActionPanels,
                )
                LunoSettingsRow(
                    icon = Icons.Default.GridView,
                    title = stringResource(R.string.grid_panels),
                    subtitle = stringResource(R.string.action_panel_count, actionSettings.actionGridPanels.panels.size),
                    shape = segmentedShape(1, 3),
                    onClick = onNavToGridPanels,
                )
                LunoSettingsRow(
                    icon = Icons.Default.Gesture,
                    title = stringResource(R.string.action_panel_style),
                    subtitle = actionPanelStyleSummary(actionSettings.actionPanelStyle),
                    shape = segmentedShape(2, 3),
                    onClick = { showActionPanelStyle = true },
                )
            }
            LunoGroup(title = stringResource(R.string.action_settings_other_group)) {
                LunoSettingsRow(
                    icon = Icons.Default.Crop,
                    title = stringResource(R.string.mini_window_position_short),
                    subtitle = stringResource(R.string.mini_window_position_hint),
                    shape = segmentedShape(0, 3),
                    onClick = { onNavToMiniWindowAdjust() },
                )
                LunoSettingsRow(
                    icon = Icons.Default.History,
                    title = stringResource(R.string.recent_apps_blocklist),
                    subtitle = if (actionSettings.recentAppsBlocklist.isEmpty()) {
                        stringResource(R.string.recent_apps_blocklist_hint)
                    } else {
                        stringResource(R.string.action_param_multi_selected, actionSettings.recentAppsBlocklist.size)
                    },
                    shape = segmentedShape(1, 3),
                    onClick = onNavToRecentAppsBlocklist,
                )
                LunoSettingsRow(
                    icon = Icons.Default.TouchApp,
                    title = stringResource(R.string.pointer),
                    subtitle = stringResource(R.string.pointer_hint),
                    shape = segmentedShape(2, 3),
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
    // 不用 LunoColumn：它带的 verticalScroll 会和 ModalBottomSheet 的下滑关闭抢手势，
    // 拖拽时 sheet 会上下抖。这里内容固定、不需要滚动，用普通 Column 把拖动交给 sheet。
    Column(
        modifier = Modifier
            .padding(PageGutter)
            .padding(bottom = ContentBottom),
        verticalArrangement = Arrangement.spacedBy(ListSpacing),
    ) {
        LunoGroup(title = stringResource(R.string.pointer_group_motion)) {
            LunoTextSlider(
                value = draft.sensitivityX,
                onValueChange = { draft = draft.copy(sensitivityX = it) },
                onValueChangeFinished = { onCommit(draft) },
                text = stringResource(id = R.string.pointer_sensitivity_x_plain),
                valueDisplay = { stringResource(id = R.string.pointer_sensitivity_x, it) },
                valueRange = 0.5f..4f,
                shape = segmentedShape(0, 3),
            )
            LunoTextSlider(
                value = draft.sensitivityY,
                onValueChange = { draft = draft.copy(sensitivityY = it) },
                onValueChangeFinished = { onCommit(draft) },
                text = stringResource(id = R.string.pointer_sensitivity_y_plain),
                valueDisplay = { stringResource(id = R.string.pointer_sensitivity_y, it) },
                valueRange = 0.5f..4f,
                shape = segmentedShape(1, 3),
            )
            LunoTextSlider(
                value = draft.acceleration,
                onValueChange = { draft = draft.copy(acceleration = it) },
                onValueChangeFinished = { onCommit(draft) },
                text = stringResource(id = R.string.pointer_acceleration_plain),
                valueDisplay = { stringResource(id = R.string.pointer_acceleration, it) },
                valueRange = 0f..2f,
                shape = segmentedShape(2, 3),
            )
        }
        LunoGroup(title = stringResource(R.string.pointer_group_cursor)) {
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
                shape = segmentedShape(0, 2),
            )
            LunoTextSlider(
                value = draft.cursorAlpha,
                onValueChange = { draft = draft.copy(cursorAlpha = it) },
                onValueChangeFinished = { onCommit(draft) },
                text = stringResource(id = R.string.pointer_cursor_alpha_plain),
                valueDisplay = { stringResource(id = R.string.pointer_cursor_alpha, (it * 100).roundToInt()) },
                valueRange = 0.2f..1f,
                shape = segmentedShape(1, 2),
            )
        }
    }
}
@Composable
private fun actionPanelStyleSummary(style: ArcStyle): String {
    val density = LocalDensity.current
    val itemDp = with(density) { style.itemSize.toDp().value }.roundToInt()
    val gapDp = with(density) { style.itemGapPx.toDp().value }.roundToInt()
    return "$itemDp dp · $gapDp dp · ${(style.spreadSpacing * 100).roundToInt()}%"
}
