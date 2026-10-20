package hunoia.luno.ui.settings.action.panel

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonChecked
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
import hunoia.luno.config.model.ActionPanel
import hunoia.luno.config.model.ActionSettings
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
 * 全局动作面板列表（决策 5：多个具名面板）。
 *
 * 面板内容全局一份、可被多个触钮复用；动作选择页里一行一个面板，
 * 触钮上配「动作面板」动作即引用其中一个。
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
    var pendingDelete by remember { mutableStateOf<ActionPanel?>(null) }
    val listState = rememberLazyListState()

    pendingDelete?.let { panel ->
        ConfirmDialog(
            onDismissRequest = { pendingDelete = null },
            title = stringResource(R.string.action_panel_delete_title),
            text = stringResource(
                R.string.action_panel_delete_desc,
                panel.name.ifBlank { stringResource(R.string.action_panel_unnamed) },
            ),
            destructive = true,
            onConfirm = {
                pendingDelete = null
                scope.launch {
                    TuneConfig.updateAction { it.copy(actionPanels = it.actionPanels.withoutPanel(panel.id)) }
                    hunoia.luno.domain.gridpanel.PanelResources.cleanup()
                }
            },
        )
    }

    fun createPanel() {
        scope.launch {
            val panel = ActionPanel.create()
            TuneConfig.updateAction { it.copy(actionPanels = it.actionPanels.withPanel(panel)) }
            onEditPanel(panel.id)
        }
    }

    val addRow: @Composable () -> Unit = {
        LunoSettingsRow(
            icon = Icons.Default.Add,
            title = stringResource(R.string.add_action_panel),
            onClick = { createPanel() },
        )
    }

    if (panels.isEmpty()) {
        LunoScreen(title = stringResource(R.string.action_panels), onBack = onBack) {
            EmptyState(message = stringResource(R.string.action_panel_empty_hint))
            LunoGroup { addRow() }
        }
        return
    }

    LunoScreenLazy(
        title = stringResource(R.string.action_panels),
        onBack = onBack,
        state = listState,
    ) {
        item {
            LunoGroup {
                panels.forEach { panel ->
                    LunoSettingsRow(
                        icon = Icons.Default.RadioButtonChecked,
                        title = panel.name.ifBlank { stringResource(R.string.action_panel_unnamed) },
                        subtitle = stringResource(R.string.action_panel_items_count, panel.items.size),
                        onClick = { onEditPanel(panel.id) },
                        // 删除挪到长按：行级只保留「进入编辑」这一个主动作，
                        // 避免 trailing 里的可点控件与行级 clickable 抢同一块触摸区。
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