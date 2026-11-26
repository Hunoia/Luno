package hunoia.luno.ui.component.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import dev.chrisbanes.haze.HazeState
import hunoia.luno.ui.component.LunoActionBar
import hunoia.luno.ui.component.LunoActionBarItem
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.page.LunoPage
import hunoia.luno.ui.component.page.LunoPageColumn
import hunoia.luno.ui.component.page.LunoPageList
import hunoia.luno.ui.component.menu.LunoDropdownMenu
import hunoia.luno.ui.component.menu.LunoDropdownMenuItem
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.TextIcon

/** 面板列表屏的一行：[id] 进编辑页，[title]/[subtitle] 展示。 */
data class PanelListRow(
    val id: String,
    val title: String,
    val subtitle: String,
    /** 行级功能图标（单功能面板=功能图标、网格面板=iconFor 结果）；null 回退分组 [PanelListSection.icon]。 */
    val icon: ImageVector? = null,
    /** 行级文字字标（网格面板名称为首字时）；null 用 [icon]。 */
    val textIcon: TextIcon? = null,
)

/** 面板列表屏的一个分组：同类型面板（动作面板 / 网格面板）归一段。 */
data class PanelListSection(
    val title: String,
    val emptyHint: String,
    val icon: ImageVector,
    val rows: List<PanelListRow>,
)

/** FAB 弹菜单里的一项：选择要新建的面板类型。 */
data class PanelCreateItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/**
 * 面板列表屏骨架：网格面板与动作面板共用的合并列表。
 * 行级只管进入属性编辑；新增走底部动作底栏（弹菜单选面板类型），删除在编辑页右上角。
 * [header] 渲染在列表顶部（空态时也保留），供调用方挂全局配置入口。
 */
@Composable
fun PanelListScreen(
    title: String,
    onBack: () -> Unit,
    sections: List<PanelListSection>,
    onEditPanel: (sectionIndex: Int, row: PanelListRow) -> Unit,
    createItems: List<PanelCreateItem>,
    header: (@Composable () -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    var showCreateMenu by remember { mutableStateOf(false) }

    // 底栏只放一个「新建」动作，菜单挂在它的锚点上（与旧 FAB 的下拉同款）。
    val actionBar: @Composable BoxScope.(HazeState) -> Unit = { hazeState ->
        LunoActionBar(hazeState) {
            Box {
                LunoActionBarItem(
                    onClick = { showCreateMenu = true },
                    icon = Icons.Default.Add,
                    contentDescription = createItems.firstOrNull()?.label.orEmpty(),
                )
                LunoDropdownMenu(
                    expanded = showCreateMenu,
                    onDismissRequest = { showCreateMenu = false },
                ) {
                    createItems.forEach { item ->
                        LunoDropdownMenuItem(
                            text = item.label,
                            leadingIcon = { Icon(item.icon, contentDescription = null) },
                            onClick = {
                                showCreateMenu = false
                                item.onClick()
                            },
                        )
                    }
                }
            }
        }
    }

    val totalRows = sections.sumOf { it.rows.size }
    if (totalRows == 0) {
        LunoPage(
            title = title,
            onBack = onBack,
            bottomBar = actionBar,
        ) {
            LunoPageColumn {
                block {
                    header?.invoke()
                    EmptyState(message = sections.firstOrNull()?.emptyHint.orEmpty())
                }
            }
        }
        return
    }

    LunoPage(
        title = title,
        onBack = onBack,
        bottomBar = actionBar,
    ) {
        LunoPageList(state = listState) {
            lazy {
        header?.let { h ->
            item(key = "header") { h() }
        }
        sections.forEachIndexed { sectionIndex, section ->
            if (section.rows.isNotEmpty()) {
                item(key = "section-$sectionIndex") {
                    LunoGroup(title = section.title) {
                        section.rows.forEachIndexed { rowIndex, row ->
                            LunoSettingsRow(
                                icon = row.icon ?: section.icon,
                                textIcon = row.textIcon,
                                title = row.title,
                                subtitle = row.subtitle,
                                shape = segmentedShape(rowIndex, section.rows.size),
                                onClick = { onEditPanel(sectionIndex, row) },
                            )
                        }
                    }
                }
            }
            }
        }
    }
}
}
