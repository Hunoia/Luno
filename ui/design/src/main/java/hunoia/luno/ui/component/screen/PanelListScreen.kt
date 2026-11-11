package hunoia.luno.ui.component.screen

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.theme.ElevationSmall
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.component.PressFloatingActionButton

/** 面板列表屏的一行：[id] 进编辑页，[title]/[subtitle] 展示。 */
data class PanelListRow(
    val id: String,
    val title: String,
    val subtitle: String,
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
 * 行级只管进入属性编辑；新增走右下角 FAB（弹菜单选面板类型），删除在编辑页右上角。
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

    val addFab: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(end = 16.dp, bottom = 16.dp),
            ) {
                    PressFloatingActionButton(
                        onClick = { showCreateMenu = true },
                        modifier = Modifier.size(64.dp),
                        shape = FloatingActionButtonDefaults.shape,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                            focusedElevation = 0.dp,
                            hoveredElevation = 0.dp,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = createItems.firstOrNull()?.label.orEmpty(),
                        )
                    }
                DropdownMenu(
                    expanded = showCreateMenu,
                    onDismissRequest = { showCreateMenu = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = LargeShape,
                    tonalElevation = ElevationSmall,
                    shadowElevation = 0.dp,
                ) {
                    createItems.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.label) },
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
        LunoScreen(
            title = title,
            onBack = onBack,
            bottomContentPadding = FloatingContentBottom,
            floatingContent = { addFab() },
        ) {
            header?.invoke()
            EmptyState(message = sections.firstOrNull()?.emptyHint.orEmpty())
        }
        return
    }

    LunoScreenLazy(
        title = title,
        onBack = onBack,
        state = listState,
        bottomContentPadding = FloatingContentBottom,
        floatingContent = addFab,
    ) {
        header?.let { h ->
            item(key = "header") { h() }
        }
        sections.forEachIndexed { sectionIndex, section ->
            if (section.rows.isNotEmpty()) {
                item(key = "section-$sectionIndex") {
                    LunoGroup(title = section.title) {
                        section.rows.forEachIndexed { rowIndex, row ->
                            LunoSettingsRow(
                                icon = section.icon,
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
