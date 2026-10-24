package hunoia.luno.ui.settings.action.gridpanel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
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
 * 全局网格面板列表。
 * 行级只管进入属性编辑；新增走右下角 FAB，删除在编辑页右上角。
 */
@Composable
fun GridPanelListScreen(
    onBack: () -> Unit,
    onEditPanel: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val actionSettings by TuneConfig.action
        .collectAsStateWithLifecycle(initialValue = ActionSettings())
    val panels = actionSettings.actionGridPanels.panels

    fun createPanel() {
        scope.launch {
            val panel = hunoia.luno.config.model.GridPanel.create()
            TuneConfig.updateAction { it.copy(actionGridPanels = it.actionGridPanels.withPanel(panel)) }
            onEditPanel(panel.id)
        }
    }

    PanelListScreen(
        title = stringResource(R.string.grid_panels),
        onBack = onBack,
        emptyHint = stringResource(R.string.grid_panel_empty_hint),
        addLabel = stringResource(R.string.grid_panel_add),
        rowIcon = Icons.Default.GridView,
        rows = panels.map { panel ->
            PanelListRow(
                id = panel.id,
                title = panel.name.ifBlank { stringResource(R.string.grid_panel_unnamed) },
                subtitle = stringResource(
                    R.string.grid_panel_summary,
                    panel.items.size, panel.geometry(false).columnCount,
                ),
            )
        },
        onEditPanel = onEditPanel,
        onCreate = { createPanel() },
    )
}
