package hunoia.luno.ui.settings.action.gridpanel

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.GridPanel
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.theme.RowIconSize
import kotlinx.coroutines.launch

/**
 * 全局网格面板列表（docs/grid-panel.md）。
 *
 * 行级只管「进入属性编辑」；面板**内容**（项）在面板内长按空白进编辑态，
 * 不在这里做 —— 网格的加项/排序/变体都依赖看见真实卡片。
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
    var pendingDelete by remember { mutableStateOf<GridPanel?>(null) }
    val listState = rememberLazyListState()

    pendingDelete?.let { panel ->
        ConfirmDialog(
            onDismissRequest = { pendingDelete = null },
            title = stringResource(R.string.grid_panel_delete_title),
            text = stringResource(
                R.string.action_panel_delete_desc,
                panel.name.ifBlank { stringResource(R.string.grid_panel_unnamed) },
            ),
            destructive = true,
            onConfirm = {
                pendingDelete = null
                scope.launch {
                    TuneConfig.updateAction { it.copy(actionGridPanels = it.actionGridPanels.withoutPanel(panel.id)) }
                    hunoia.luno.domain.gridpanel.PanelResources.cleanup()
                }
            },
        )
    }

    fun createPanel() {
        scope.launch {
            val panel = GridPanel.create()
            TuneConfig.updateAction { it.copy(actionGridPanels = it.actionGridPanels.withPanel(panel)) }
            onEditPanel(panel.id)
        }
    }

    val addRow: @Composable () -> Unit = {
        LunoSettingsRow(
            icon = Icons.Default.Add,
            title = stringResource(R.string.grid_panel_add),
            onClick = { createPanel() },
        )
    }

    if (panels.isEmpty()) {
        LunoScreen(title = stringResource(R.string.grid_panels), onBack = onBack) {
            EmptyState(message = stringResource(R.string.grid_panel_empty_hint))
            LunoGroup { addRow() }
        }
        return
    }

    LunoScreenLazy(
        title = stringResource(R.string.grid_panels),
        onBack = onBack,
        state = listState,
    ) {
        item {
            LunoGroup {
                panels.forEach { panel ->
                    LunoSettingsRow(
                        icon = Icons.Default.GridView,
                        title = panel.name.ifBlank { stringResource(R.string.grid_panel_unnamed) },
                        subtitle = stringResource(
                            R.string.grid_panel_summary,
                            panel.items.size, panel.geometry(false).columnCount,
                        ),
                        onClick = { onEditPanel(panel.id) },
                        onLongClick = { pendingDelete = panel },
                        trailing = {
                            IconButton(onClick = { pendingDelete = panel }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(RowIconSize),
                                )
                            }
                        },
                    )
                }
            }
        }
        item { LunoGroup { addRow() } }
    }
}
