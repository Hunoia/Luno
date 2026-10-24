package hunoia.luno.ui.settings.action.panel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.ui.component.screen.PanelListRow
import hunoia.luno.ui.component.screen.PanelListScreen
import kotlinx.coroutines.launch

/**
 * 全局动作面板列表（决策 5：多个具名面板）。
 *
 * 面板内容全局一份、可被多个触钮复用；动作选择页里一行一个面板，
 * 触钮上配「动作面板」动作即引用其中一个。
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
    val panels = actionSettings.actionPanels.panels

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
                title = panel.name.ifBlank { stringResource(R.string.action_panel_unnamed) },
                subtitle = stringResource(R.string.action_panel_items_count, panel.items.size),
            )
        },
        onEditPanel = onEditPanel,
        onCreate = { createPanel() },
    )
}
