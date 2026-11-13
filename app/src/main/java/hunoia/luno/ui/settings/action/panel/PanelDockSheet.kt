package hunoia.luno.ui.settings.action.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.PanelDockDefaults
import hunoia.luno.config.model.PanelDockSettings
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.gridpanel.PanelDockCatalog
import hunoia.luno.gridpanel.PanelDockEntry
import hunoia.luno.ui.component.feedback.LunoIconButton
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.component.sheet.OptimizedScrollState
import hunoia.luno.gridpanel.ui.PanelDockGlyph
import hunoia.luno.ui.theme.RowIconSize
import kotlinx.coroutines.launch

/**
 * 面板底栏配置：总开关 + 有序成员 + 可添加项。
 *
 * 成员存的是**面板引用 id**（网格面板 UUID / `single:<widgetId>`），名字与图标实时解析，
 * 所以重命名面板不用改这里，删面板由 `PanelCleaner` 一并摘成员。
 * 顺序就是 tab 顺序；上限 [PanelDockDefaults.MaxMembers]（底栏是固定宽 tab 的胶囊，不滚动）。
 */
@Composable
fun PanelDockSheet(
    settings: ActionSettings,
    onDismissRequest: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val dock = settings.panelDock
    val members = remember(settings) { PanelDockCatalog.entries(context, settings) }
    val candidates = remember(settings) { PanelDockCatalog.candidates(context, settings) }
    val remaining = candidates.filterNot { candidate -> dock.members.contains(candidate.id) }
    val scrollState = rememberScrollState()

    fun update(transform: (PanelDockSettings) -> PanelDockSettings) {
        scope.launch {
            TuneConfig.updateAction { current -> current.copy(panelDock = transform(current.panelDock)) }
        }
    }

    LunoSheet(
        onDismissRequest = onDismissRequest,
        showDragHandle = true,
        scrollState = OptimizedScrollState.Scroll(scrollState),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LunoGroup {
                LunoSwitchRow(
                    title = stringResource(R.string.panel_dock_enabled),
                    subtitle = stringResource(R.string.panel_dock_enabled_hint),
                    checked = dock.enabled,
                    onCheckedChange = { enabled -> update { it.copy(enabled = enabled) } },
                    shape = segmentedShape(0, 1),
                )
            }
            LunoGroup(
                title = stringResource(
                    R.string.panel_dock_members,
                    members.size,
                    PanelDockDefaults.MaxMembers,
                ),
            ) {
                if (members.isEmpty()) {
                    LunoSettingsRow(
                        title = stringResource(R.string.panel_dock_members_empty),
                        shape = segmentedShape(0, 1),
                    )
                }
                members.forEachIndexed { index, entry ->
                    LunoSettingsRow(
                        title = entry.label,
                        leading = dockLeading(entry),
                        shape = segmentedShape(index, members.size),
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LunoIconButton(
                                    icon = Icons.Default.ArrowUpward,
                                    contentDescription = stringResource(R.string.panel_dock_move_up),
                                    enabled = index > 0,
                                    onClick = { update { it.moved(entry.id, -1) } },
                                )
                                LunoIconButton(
                                    icon = Icons.Default.ArrowDownward,
                                    contentDescription = stringResource(R.string.panel_dock_move_down),
                                    enabled = index < members.lastIndex,
                                    onClick = { update { it.moved(entry.id, 1) } },
                                )
                                LunoIconButton(
                                    icon = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.panel_dock_remove),
                                    onClick = { update { it.withoutMember(entry.id) } },
                                )
                            }
                        },
                    )
                }
            }
            LunoGroup(title = stringResource(R.string.panel_dock_candidates)) {
                if (remaining.isEmpty()) {
                    LunoSettingsRow(
                        title = if (dock.canAddMore) {
                            stringResource(R.string.panel_dock_candidates_empty)
                        } else {
                            stringResource(R.string.panel_dock_full, PanelDockDefaults.MaxMembers)
                        },
                        shape = segmentedShape(0, 1),
                    )
                }
                remaining.forEachIndexed { index, entry ->
                    LunoSettingsRow(
                        title = entry.label,
                        leading = dockLeading(entry),
                        shape = segmentedShape(index, remaining.size),
                        enabled = dock.canAddMore,
                        onClick = { update { it.withMember(entry.id) } },
                    )
                }
            }
        }
    }
}

/** 设置页行首图标：复用底栏同一份图标渲染，不另写一套。 */
private fun dockLeading(entry: PanelDockEntry): @Composable () -> Unit = {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PanelDockGlyph(
            icon = entry.icon,
            size = RowIconSize,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
    }
}
