package hunoia.luno.ui.component.screen

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import hunoia.luno.ui.component.LunoFab
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.theme.FloatingContentBottom

/** 面板列表屏的一行：[id] 进编辑页，[title]/[subtitle] 展示。 */
data class PanelListRow(
    val id: String,
    val title: String,
    val subtitle: String,
)

/**
 * 面板列表屏骨架：网格面板与动作面板两页只有数据与文案不同。
 * 行级只管进入属性编辑；新增走右下角 FAB，删除在编辑页右上角。
 * [header] 渲染在列表顶部（空态时也保留），供调用方挂全局配置入口；
 * [listTitle] 为面板列表组的标题（空则无标题）。
 */
@Composable
fun PanelListScreen(
    title: String,
    onBack: () -> Unit,
    emptyHint: String,
    addLabel: String,
    rowIcon: ImageVector,
    rows: List<PanelListRow>,
    onEditPanel: (String) -> Unit,
    onCreate: () -> Unit,
    header: (@Composable () -> Unit)? = null,
    listTitle: String = "",
) {
    val listState = rememberLazyListState()
    val addFab: @Composable () -> Unit = {
        LunoFab(onClick = onCreate, icon = Icons.Default.Add, contentDescription = addLabel)
    }

    if (rows.isEmpty()) {
        LunoScreen(
            title = title,
            onBack = onBack,
            bottomContentPadding = FloatingContentBottom,
            floatingContent = { addFab() },
        ) {
            header?.invoke()
            EmptyState(message = emptyHint)
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
        item {
            LunoGroup(title = listTitle) {
                rows.forEach { row ->
                    LunoSettingsRow(
                        icon = rowIcon,
                        title = row.title,
                        subtitle = row.subtitle,
                        onClick = { onEditPanel(row.id) },
                    )
                }
            }
        }
    }
}
