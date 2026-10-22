package hunoia.luno.ui.settings.action.gridpanel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.theme.FloatingContentBottom
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
    val listState = rememberLazyListState()

    fun createPanel() {
        scope.launch {
            val panel = hunoia.luno.config.model.GridPanel.create()
            TuneConfig.updateAction { it.copy(actionGridPanels = it.actionGridPanels.withPanel(panel)) }
            onEditPanel(panel.id)
        }
    }

    val addFab: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                FloatingActionButton(
                    onClick = { createPanel() },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(end = 16.dp, bottom = 16.dp)
                        .size(64.dp),
                    shape = FloatingActionButtonDefaults.shape,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.grid_panel_add),
                    )
                }
            }
        }
    }

    if (panels.isEmpty()) {
        LunoScreen(
            title = stringResource(R.string.grid_panels),
            onBack = onBack,
            bottomContentPadding = FloatingContentBottom,
            floatingContent = { addFab() },
        ) {
            EmptyState(message = stringResource(R.string.grid_panel_empty_hint))
        }
        return
    }

    LunoScreenLazy(
        title = stringResource(R.string.grid_panels),
        onBack = onBack,
        state = listState,
        bottomContentPadding = FloatingContentBottom,
        floatingContent = addFab,
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
                    )
                }
            }
        }
    }
}
