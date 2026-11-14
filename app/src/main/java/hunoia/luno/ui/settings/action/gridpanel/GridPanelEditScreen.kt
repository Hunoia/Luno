package hunoia.luno.ui.settings.action.gridpanel

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.config.model.GridPanel
import hunoia.luno.config.model.PanelAppearance
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.actions.library.IconPickerSheet
import hunoia.luno.gridpanel.PanelDockCatalog
import hunoia.luno.gridpanel.ui.PanelDockGlyph
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.feedback.LunoIconButton
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.RowIconSize
import kotlinx.coroutines.launch

/**
 * 网格面板属性编辑：名称 / 透明度 / 背景模糊 / 离屏边距。
 * 网格面板的项与列数在面板内管理；页数是派生值（编辑态滑到末尾虚页落项即建页）；
 * 列数只在编辑态双指调整；卡片宽高只在面板内的缩放手柄调整。
 * 装载必须显式 collect：拿到 DataStore 真实值前不能判「面板不存在」，首帧空配置会误报。
 */
@Composable
fun GridPanelEditScreen(
    panelId: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // 当前朝向：横屏打开改的是横屏设置，竖屏改竖屏，实时跟随旋转。
    fun isLandscape(): Boolean = context.resources.configuration.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var loaded by remember { mutableStateOf(false) }
    var panel by remember(panelId) { mutableStateOf<GridPanel?>(null) }
    var globalAppearance by remember { mutableStateOf(PanelAppearance()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }
    LaunchedEffect(panelId) {
        TuneConfig.action.collect { settings ->
            panel = settings.actionGridPanels.panelById(panelId)
            globalAppearance = settings.panelAppearance
            loaded = true
        }
    }

    val current = panel
    if (current == null) {
        if (loaded) {
            LunoScreen(title = stringResource(hunoia.luno.actions.R.string.grid_panel_unnamed), onBack = onBack) {
                EmptyState(message = stringResource(hunoia.luno.gridpanel.R.string.action_panel_missing))
            }
        }
        return
    }

    fun update(transform: (GridPanel) -> GridPanel) {
        scope.launch {
            TuneConfig.updateAction { settings ->
                val latest = settings.actionGridPanels.panelById(panelId) ?: return@updateAction settings
                settings.copy(actionGridPanels = settings.actionGridPanels.withPanel(transform(latest)))
            }
        }
    }

    LunoScreenLazy(
        title = current.name.ifBlank { stringResource(hunoia.luno.actions.R.string.grid_panel_unnamed) },
        onBack = onBack,
        state = rememberLazyListState(),
        actions = {
            // 删除从列表页移到编辑页右上角：带确认，删后返回。
            LunoIconButton(
                icon = Icons.Outlined.Delete,
                contentDescription = stringResource(hunoia.luno.design.R.string.delete),
                onClick = { showDeleteConfirm = true },
            )
        },
        dialogs = {
            if (showIconPicker) {
                IconPickerSheet(
                    onDismissRequest = { showIconPicker = false },
                    currentKey = current.icon,
                    defaultIcon = Icons.Default.GridView,
                    onPick = { key ->
                        showIconPicker = false
                        update { it.copy(icon = key) }
                    },
                )
            }
            if (showDeleteConfirm) {
                // 引用数在弹窗打开时现查：引用可能刚在别的页面改过。
                var referenceCount by remember { mutableStateOf(0) }
                LaunchedEffect(showDeleteConfirm) {
                    if (showDeleteConfirm) referenceCount = TuneConfig.countPanelReferences(panelId)
                }
                val deleteDesc = stringResource(
                    R.string.action_panel_delete_desc,
                    current.name.ifBlank { stringResource(hunoia.luno.actions.R.string.grid_panel_unnamed) },
                )
                ConfirmDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = stringResource(R.string.grid_panel_delete_title),
                    text = if (referenceCount > 0) {
                        deleteDesc + "\n" + stringResource(R.string.action_panel_delete_refs, referenceCount)
                    } else {
                        deleteDesc
                    },
                    destructive = true,
                    onConfirm = {
                        showDeleteConfirm = false
                        scope.launch {
                            TuneConfig.cleanPanelReferences(panelId)
                            TuneConfig.updateAction { settings ->
                                settings.copy(actionGridPanels = settings.actionGridPanels.withoutPanel(panelId))
                            }
                            hunoia.luno.gridpanel.PanelResources.cleanup()
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
                onValueChange = { name -> update { it.copy(name = name) } },
                label = { Text(stringResource(R.string.action_panel_name_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter, vertical = CardInnerSpacing),
                shape = LargeShape,
            )
        }
        item {
            LunoGroup {
                LunoSettingsRow(
                    title = stringResource(R.string.grid_panel_icon),
                    subtitle = stringResource(R.string.grid_panel_icon_hint),
                    leading = {
                        PanelDockGlyph(
                            icon = PanelDockCatalog.iconFor(current),
                            size = RowIconSize,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    shape = segmentedShape(0, 1),
                    onClick = { showIconPicker = true },
                )
            }
        }
        item {
            LunoGroup {
                val ownAppearance = current.appearance
                val appearanceRows = if (ownAppearance == null) 1 else 6
                PanelAppearanceControls(
                    appearance = ownAppearance,
                    global = globalAppearance,
                    landscape = isLandscape(),
                    shapeAt = { index -> segmentedShape(index, appearanceRows) },
                    onFollowGlobalChange = { follow ->
                        update { panel ->
                            panel.copy(
                                appearance = if (follow) null else globalAppearance,
                            )
                        }
                    },
                    onCommit = { next -> update { it.copy(appearance = next) } },
                )
            }
        }
    }
}
