package hunoia.luno.ui.settings.action.panel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gesture
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
import hunoia.luno.ui.component.screen.PanelListRow
import hunoia.luno.ui.component.screen.PanelListScreen
import hunoia.luno.ui.component.sheet.LunoSheet
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 全局动作面板列表（决策 5：多个具名面板）。
 *
 * 面板内容全局一份、可被多个触钮复用；动作选择页里一行一个面板，
 * 触钮上配「动作面板」动作即引用其中一个。
 * 顶部挂「动作面板样式」全局配置入口（点击弹底部配置面板）。
 * 新增走右下角 FAB，删除在编辑页右上角。
 */
@Composable
fun ActionPanelListScreen(
    onBack: () -> Unit,
    onEditPanel: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val actionSettings by TuneConfig.action
        .collectAsStateWithLifecycle(initialValue = ActionSettings())
    val gestureSettings by TuneConfig.gesture
        .collectAsStateWithLifecycle(initialValue = GestureSettings())
    val panels = actionSettings.actionPanels.panels
    var showStyle by remember { mutableStateOf(false) }

    fun createPanel() {
        scope.launch {
            val panel = hunoia.luno.config.model.ActionPanel.create()
            TuneConfig.updateAction { it.copy(actionPanels = it.actionPanels.withPanel(panel)) }
            onEditPanel(panel.id)
        }
    }

    PanelListScreen(
        title = stringResource(R.string.action_panels),
        onBack = onBack,
        emptyHint = stringResource(R.string.action_panel_empty_hint),
        addLabel = stringResource(R.string.add_action_panel),
        rowIcon = Icons.Default.RadioButtonChecked,
        rows = panels.map { panel ->
            PanelListRow(
                id = panel.id,
                title = panel.name.ifBlank { stringResource(hunoia.luno.actions.R.string.action_panel_unnamed) },
                subtitle = stringResource(R.string.action_panel_items_count, panel.items.size),
            )
        },
        onEditPanel = onEditPanel,
        onCreate = { createPanel() },
        listTitle = stringResource(R.string.action_panel_list_group),
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
        LunoSheet(onDismissRequest = { showStyle = false }, showDragHandle = true) {
            ActionPanelStyleConfigContent(
                currentStyle = actionSettings.actionPanelStyle,
                actionPanelVibrate = gestureSettings.actionPanelVibrate,
                onStyleChanged = { next ->
                    scope.launch { TuneConfig.updateAction { it.copy(actionPanelStyle = next) } }
                },
                onActionPanelVibrateChanged = { enabled ->
                    scope.launch { TuneConfig.updateGesture { it.copy(actionPanelVibrate = enabled) } }
                },
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
