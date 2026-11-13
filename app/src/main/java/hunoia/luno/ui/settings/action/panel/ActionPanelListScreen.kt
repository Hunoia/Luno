package hunoia.luno.ui.settings.action.panel

import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.ArcStyle
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.GridPanel
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.gridpanel.SinglePanelCatalog
import hunoia.luno.gesture.settings.style.ActionPanelStyleConfigContent
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.PanelCreateItem
import hunoia.luno.ui.component.screen.PanelListRow
import hunoia.luno.ui.component.screen.PanelListScreen
import hunoia.luno.ui.component.screen.PanelListSection
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.component.sheet.OptimizedScrollState
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 全局面板列表。
 *
 * 单功能面板固定三块（剪贴板 / 应用抽屉 / 快速启动），由注册表驱动、不可新建 / 删除 /
 * 改名；点击直接进入对应面板设置。网格面板仍可新建多个，动作选择页一行一个具名面板。
 * 顶部挂「动作面板样式」全局配置入口。
 */
@Composable
fun ActionPanelListScreen(
    onBack: () -> Unit,
    onEditActionPanel: (String) -> Unit,
    onEditSinglePanel: (String) -> Unit,
    onEditGridPanel: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val actionSettings by TuneConfig.action
        .collectAsStateWithLifecycle(initialValue = ActionSettings())
    val gestureSettings by TuneConfig.gesture
        .collectAsStateWithLifecycle(initialValue = GestureSettings())
    val panels = actionSettings.actionPanels.panels
    val singleWidgets = SinglePanelCatalog.widgets()
    val gridPanels = actionSettings.actionGridPanels.panels
    var showStyle by remember { mutableStateOf(false) }
    // 样式项已超过一屏：内容滚动与 sheet 下滑由 LunoSheet 的 nestedScroll 衔接。
    val styleScrollState = rememberScrollState()

    fun createActionPanel() {
        scope.launch {
            val panel = hunoia.luno.config.model.ActionPanel.create()
            TuneConfig.updateAction { it.copy(actionPanels = it.actionPanels.withPanel(panel)) }
            onEditActionPanel(panel.id)
        }
    }

    fun createGridPanel() {
        scope.launch {
            val panel = GridPanel.create()
            TuneConfig.updateAction { it.copy(actionGridPanels = it.actionGridPanels.withPanel(panel)) }
            onEditGridPanel(panel.id)
        }
    }

    PanelListScreen(
        title = stringResource(R.string.action_panel_list_group),
        onBack = onBack,
        sections = listOf(
            PanelListSection(
                title = stringResource(R.string.single_panels),
                emptyHint = "",
                icon = Icons.Default.Widgets,
                rows = singleWidgets.map { widget ->
                    PanelListRow(
                        id = widget.id,
                        title = stringResource(widget.labelRes),
                        subtitle = stringResource(hunoia.luno.gridpanel.R.string.single_panel_mode_single),
                    )
                },
            ),
            PanelListSection(
                title = stringResource(R.string.action_panels),
                emptyHint = stringResource(R.string.action_panel_empty_hint),
                icon = Icons.Default.RadioButtonChecked,
                rows = panels.map { panel ->
                    PanelListRow(
                        id = panel.id,
                        title = panel.name.ifBlank { stringResource(hunoia.luno.actions.R.string.action_panel_unnamed) },
                        subtitle = stringResource(R.string.action_panel_items_count, panel.items.size),
                    )
                },
            ),
            PanelListSection(
                title = stringResource(R.string.grid_panels),
                emptyHint = stringResource(R.string.grid_panel_empty_hint),
                icon = Icons.Default.GridView,
                rows = gridPanels.map { panel ->
                    PanelListRow(
                        id = panel.id,
                        title = panel.name.ifBlank { stringResource(hunoia.luno.actions.R.string.grid_panel_unnamed) },
                        subtitle = gridPanelSummary(panel),
                    )
                },
            ),
        ),
        onEditPanel = { sectionIndex, row ->
            when (sectionIndex) {
                0 -> onEditSinglePanel(row.id)
                1 -> onEditActionPanel(row.id)
                2 -> onEditGridPanel(row.id)
            }
        },
        createItems = listOf(
            PanelCreateItem(
                label = stringResource(R.string.add_action_panel),
                icon = Icons.Default.RadioButtonChecked,
                onClick = ::createActionPanel,
            ),
            PanelCreateItem(
                label = stringResource(R.string.grid_panel_add),
                icon = Icons.Default.GridView,
                onClick = ::createGridPanel,
            ),
        ),
        header = {
            LunoGroup(title = stringResource(R.string.action_panel_style_group)) {
                LunoSettingsRow(
                    icon = Icons.Default.Gesture,
                    title = stringResource(R.string.action_panel_style),
                    subtitle = actionPanelStyleSummary(actionSettings.actionPanelStyle),
                    shape = segmentedShape(0, 1),
                    onClick = { showStyle = true },
                )
            }
        },
    )

    if (showStyle) {
        LunoSheet(
            onDismissRequest = { showStyle = false },
            showDragHandle = true,
            scrollState = OptimizedScrollState.Scroll(styleScrollState),
        ) {
            ActionPanelStyleConfigContent(
                currentStyle = actionSettings.actionPanelStyle,
                actionPanelVibrate = gestureSettings.actionPanelVibrate,
                actionPanelLongPressMs = gestureSettings.actionPanelLongPressMs,
                onStyleChanged = { next ->
                    scope.launch { TuneConfig.updateAction { it.copy(actionPanelStyle = next) } }
                },
                onActionPanelVibrateChanged = { enabled ->
                    scope.launch { TuneConfig.updateGesture { it.copy(actionPanelVibrate = enabled) } }
                },
                onActionPanelLongPressChanged = { ms ->
                    scope.launch { TuneConfig.updateGesture { it.copy(actionPanelLongPressMs = ms) } }
                },
                scrollState = styleScrollState,
            )
        }
    }
}

@Composable
private fun gridPanelSummary(panel: GridPanel): String = stringResource(
    R.string.grid_panel_summary,
    panel.portrait.items.size,
    panel.portrait.geometry.columnCount,
)

@Composable
private fun actionPanelStyleSummary(style: ArcStyle): String {
    val density = LocalDensity.current
    val itemDp = with(density) { style.itemSize.toDp().value }.roundToInt()
    val gapDp = with(density) { style.itemGapPx.toDp().value }.roundToInt()
    return "$itemDp dp · ${(style.appIconScale * 100).roundToInt()}% · $gapDp dp · ${(style.spreadSpacing * 100).roundToInt()}% · ${style.maxAngleDegrees.roundToInt()}°"
}
