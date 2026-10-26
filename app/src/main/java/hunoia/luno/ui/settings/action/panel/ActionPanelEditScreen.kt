package hunoia.luno.ui.settings.action.panel

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.action.model.StoredAction
import hunoia.luno.action.model.longPressVariant
import hunoia.luno.config.model.ActionPanel
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.ui.component.DragReorderHandle
import hunoia.luno.ui.component.LunoFab
import hunoia.luno.ui.component.actionTextCompose
import hunoia.luno.ui.component.dragReorderPlacement
import hunoia.luno.ui.component.rememberDragReorderState
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.feedback.LunoIconButton
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.selectedSegmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.navigation.ActionSelect
import hunoia.luno.ui.theme.*
import kotlinx.coroutines.launch

/**
 * 单个动作面板编辑器：改名 + 面板项增删排序 + 逐项设长按变体。
 * 挑动作复用 `ActionSelect` 路由（`actionPanelId` 模式），分组/搜索/应用/快捷方式现成；
 * 落盘见 DataLoader.saveActionPanelItem。
 *
 * 排序是拖动手柄即时改本地草稿，松手才落盘；直接返回不丢已提交顺序。
 */
@Composable
fun ActionPanelEditScreen(
    panelId: String,
    onBack: () -> Unit,
    onPickItem: (ActionSelect) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // 这里**不能**用 collectAsStateWithLifecycle(initialValue = ActionSettings())：
    // 首帧拿到的是那份空配置，panelById 必然 miss。早先的实现在这里 early-return +
    // popBackStack，于是「点面板行 → 进编辑页 → 首帧判定面板不存在 → 立刻退回列表」，
    // 表现成点了完全没反应。现在显式收集，拿到 DataStore 真实值后才判断面板在不在。
    var loaded by remember { mutableStateOf(false) }
    var panel by remember(panelId) { mutableStateOf<ActionPanel?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // 拖动排序草稿由 DragReorderState 持有：null = 跟随配置，拖动手柄只改本地顺序，松手才落盘。
    fun persistItems(newItems: List<StoredAction>, cleanupResources: Boolean = false) {
        scope.launch {
            TuneConfig.updateAction { settings ->
                val latest = settings.actionPanels.panelById(panelId) ?: return@updateAction settings
                settings.copy(
                    actionPanels = settings.actionPanels.withPanel(latest.copy(items = newItems))
                )
            }
            if (cleanupResources) hunoia.luno.domain.gridpanel.PanelResources.cleanup()
        }
    }

    val reorder = rememberDragReorderState(panelId,
        current = { panel?.items.orEmpty() },
        onCommit = { persistItems(it) },
    )

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

    val items = reorder.items
    LaunchedEffect(current.items) {
        reorder.syncWithCurrent()
    }

    fun updateItems(newItems: List<StoredAction>, cleanupResources: Boolean = false) {
        reorder.draft = newItems
        persistItems(newItems, cleanupResources)
    }

    LunoScreenLazy(
        title = current.name.ifBlank { stringResource(R.string.action_panel_unnamed) },
        onBack = onBack,
        state = listState,
        bottomContentPadding = FloatingContentBottom,
        actions = {
            // 删除从列表页移到编辑页右上角：带确认，删后返回。
            LunoIconButton(
                icon = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.delete),
                onClick = { showDeleteConfirm = true },
            )
        },
        floatingContent = {
            LunoFab(
                onClick = { onPickItem(ActionSelect(actionPanelId = panelId)) },
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.action_panel_add_item),
            )
        },
        dialogs = {
            if (showDeleteConfirm) {
                ConfirmDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = stringResource(R.string.action_panel_delete_title),
                    text = stringResource(
                        R.string.action_panel_delete_desc,
                        current.name.ifBlank { stringResource(R.string.action_panel_unnamed) },
                    ),
                    destructive = true,
                    onConfirm = {
                        showDeleteConfirm = false
                        scope.launch {
                            TuneConfig.updateAction { settings ->
                                settings.copy(actionPanels = settings.actionPanels.withoutPanel(panelId))
                            }
                            hunoia.luno.domain.gridpanel.PanelResources.cleanup()
                            onBack()
                        }
                    },
                )
            }
        },
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
                if (items.isEmpty()) {
                    EmptyState(message = stringResource(R.string.action_panel_empty_hint))
                } else {
                    items.forEachIndexed { index, item ->
                        val variant = item.longPressVariant()
                        LunoSettingsRow(
                            title = item.actionTextCompose(),
                            subtitle = variant?.actionTextCompose()
                                ?: stringResource(R.string.action_panel_item_no_long_press),
                            modifier = Modifier.dragReorderPlacement(reorder, index),
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
                                                reorder.items.toMutableList()
                                                    .apply { removeAt(index) },
                                                cleanupResources = true,
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
                                    // 拖动手柄放在删除右侧：按住只改本地顺序，松手落盘。
                                    DragReorderHandle(
                                        reorder,
                                        index,
                                        stringResource(R.string.action_panel_item_drag),
                                        panelId, current.items,
                                    )
                                }
                            },
                            shape = selectedSegmentedShape(index, items.size, null),
                        )
                    }
                }
            }
        }
    }
}
