package hunoia.luno.ui.settings.action.panel

import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.RadioButtonChecked
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
import hunoia.luno.domain.tune.TuneConfig
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
 * 全局面板列表（决策 5：多个具名面板）。
 *
 * 动作面板与网格面板合并一页：内容全局一份、可被多个触钮复用；动作选择页里一行一个面板，
 * 触钮上配「动作面板」动作即引用其中一个。
 * 顶部挂「动作面板样式」全局配置入口（点击弹底部配置面板）。
 * 新增走右下角 FAB（弹菜单选动作/网格面板），删除在编辑页右上角。
 */
@Composable
fun ActionPanelListScreen(
    onBack: () -> Unit,
    onEditActionPanel: (String) -> Unit,
    onEditGridPanel: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val actionSettings by TuneConfig.action
        .collectAsStateWithLifecycle(initialValue = ActionSettings())
    val gestureSettings by TuneConfig.gesture
        .collectAsStateWithLifecycle(initialValue = GestureSettings())
    val panels = actionSettings.actionPanels.panels
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
            val panel = hunoia.luno.config.model.GridPanel.create()
            TuneConfig.updateAction { it.copy(actionGridPanels = it.actionGridPanels.withPanel(panel)) }
            onEditGridPanel(panel.id)
        }
    }

    PanelListScreen(
        title = stringResource(R.string.action_panel_list_group),
        onBack = onBack,
        sections = listOf(
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
                        subtitle = stringResource(
                            R.string.grid_panel_summary,
                            panel.portrait.items.size, panel.portrait.geometry.columnCount,
                        ),
                    )
                },
            ),
        ),
        onEditPanel = { sectionIndex, row ->
            when (sectionIndex) {
                0 -> onEditActionPanel(row.id)
                1 -> onEditGridPanel(row.id)
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
                onStyleChanged = { next ->
                    scope.launch { TuneConfig.updateAction { it.copy(actionPanelStyle = next) } }
                },
                onActionPanelVibrateChanged = { enabled ->
                    scope.launch { TuneConfig.updateGesture { it.copy(actionPanelVibrate = enabled) } }
                },
                scrollState = styleScrollState,
            )
        }
    }
}

@Composable
private fun actionPanelStyleSummary(style: ArcStyle): String {
    val density = LocalDensity.current
    val itemDp = with(density) { style.itemSize.toDp().value }.roundToInt()
    val gapDp = with(density) { style.itemGapPx.toDp().value }.roundToInt()
    return "$itemDp dp · ${(style.appIconScale * 100).roundToInt()}% · $gapDp dp · ${(style.spreadSpacing * 100).roundToInt()}% · ${style.maxAngleDegrees.roundToInt()}°"
}
