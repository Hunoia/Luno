package hunoia.luno.ui.settings.action.panel

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.action.model.StoredAction
import hunoia.luno.action.model.longPressVariant
import hunoia.luno.config.model.ActionPanel
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.ui.component.actionTextCompose
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.selectedSegmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.navigation.ActionSelect
import hunoia.luno.ui.theme.*
import kotlinx.coroutines.launch

/**
 * 单个动作面板的编辑器：改名 + 面板项增删排序 + **逐项设长按变体**。
 *
 * 挑动作不自己实现一套：复用 `ActionSelect` 路由（`actionPanelId` 模式），
 * 分组/搜索/应用/快捷方式全部现成，见 DataLoader.saveActionPanelItem。
 */
@Composable
fun ActionPanelEditScreen(
    panelId: String,
    onBack: () -> Unit,
    onPickItem: (ActionSelect) -> Unit,
) {
    val scope = rememberCoroutineScope()
    // 这里**不能**用 collectAsStateWithLifecycle(initialValue = ActionSettings())：
    // 首帧拿到的是那份空配置，panelById 必然 miss。早先的实现在这里 early-return +
    // popBackStack，于是「点面板行 → 进编辑页 → 首帧判定面板不存在 → 立刻退回列表」，
    // 表现成点了完全没反应。现在显式收集，拿到 DataStore 真实值后才判断面板在不在。
    var loaded by remember { mutableStateOf(false) }
    var panel by remember(panelId) { mutableStateOf<ActionPanel?>(null) }
    LaunchedEffect(panelId) {
        TuneConfig.action.collect { settings ->
            panel = settings.actionPanels.panelById(panelId)
            loaded = true
        }
    }
    val listState = rememberLazyListState()

    val current = panel
    if (current == null) {
        // 数据未到：本帧什么都不画（一次闪烁）。数据到了还不存在：给明确落点，不静默弹回。
        if (loaded) {
            LunoScreen(title = stringResource(R.string.action_panel_unnamed), onBack = onBack) {
                EmptyState(message = stringResource(R.string.action_panel_missing))
            }
        }
        return
    }

    fun updateItems(items: List<StoredAction>) {
        scope.launch {
            TuneConfig.updateAction { settings ->
                val latest = settings.actionPanels.panelById(panelId) ?: return@updateAction settings
                settings.copy(
                    actionPanels = settings.actionPanels.withPanel(latest.copy(items = items))
                )
            }
        }
    }

    LunoScreenLazy(
        title = current.name.ifBlank { stringResource(R.string.action_panel_unnamed) },
        onBack = onBack,
        state = listState,
    ) {
        item {
            OutlinedTextField(
                value = current.name,
                onValueChange = { name ->
                    scope.launch {
                        TuneConfig.updateAction { settings ->
                            settings.copy(
                                actionPanels = settings.actionPanels.withPanel(current.copy(name = name))
                            )
                        }
                    }
                },
                label = { Text(stringResource(R.string.action_panel_name_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter, vertical = CardInnerSpacing),
                shape = LargeShape,
            )
        }
        item {
            LunoGroup(title = stringResource(R.string.action_panel_items)) {
                if (current.items.isEmpty()) {
                    EmptyState(message = stringResource(R.string.action_panel_empty_hint))
                } else {
                    if (current.items.size < 2) {
                        EmptyState(
                            message = stringResource(R.string.action_panel_single_item_hint),
                            icon = Icons.Default.RadioButtonChecked,
                        )
                    }
                    current.items.forEachIndexed { index, item ->
                        val variant = item.longPressVariant()
                        LunoSettingsRow(
                            title = item.actionTextCompose(),
                            subtitle = variant?.actionTextCompose()
                                ?: stringResource(R.string.action_panel_item_no_long_press),
                            onClick = {
                                onPickItem(
                                    ActionSelect(
                                        actionPanelId = panelId,
                                        actionPanelItemIndex = index,
                                    )
                                )
                            },
                            trailing = {
                                Row {
                                    IconButton(
                                        onClick = {
                                            updateItems(
                                                current.items.toMutableList().apply {
                                                    add(index + 1, removeAt(index))
                                                }
                                            )
                                        },
                                        enabled = index > 0,
                                    ) {
                                        Icon(
                                            Icons.Default.ArrowUpward,
                                            contentDescription = stringResource(R.string.action_panel_item_move_up),
                                            modifier = Modifier.size(RowIconSize),
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            updateItems(
                                                current.items.toMutableList().apply {
                                                    add(index - 1, removeAt(index))
                                                }
                                            )
                                        },
                                        enabled = index < current.items.lastIndex,
                                    ) {
                                        Icon(
                                            Icons.Default.ArrowDownward,
                                            contentDescription = stringResource(R.string.action_panel_item_move_down),
                                            modifier = Modifier.size(RowIconSize),
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            updateItems(
                                                current.items.toMutableList().apply { removeAt(index) }
                                            )
                                        },
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = stringResource(R.string.delete),
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(RowIconSize),
                                        )
                                    }
                                }
                            },
                            shape = selectedSegmentedShape(index, current.items.size, null),
                        )
                    }
                }
            }
        }
        item {
            LunoGroup {
                LunoSettingsRow(
                    icon = Icons.Default.Add,
                    title = stringResource(R.string.action_panel_add_item),
                    onClick = { onPickItem(ActionSelect(actionPanelId = panelId)) },
                )
            }
        }
    }
}