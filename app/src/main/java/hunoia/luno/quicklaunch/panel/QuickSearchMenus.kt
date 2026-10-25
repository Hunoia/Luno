package hunoia.luno.quicklaunch.panel

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.PopupProperties
import hunoia.luno.R
import hunoia.luno.quicklaunch.query.SearchAction
import hunoia.luno.quicklaunch.query.SearchActionLabel
import hunoia.luno.quicklaunch.query.SearchResult
import hunoia.luno.quicklaunch.query.SOURCE_ENGINE
import hunoia.luno.quicklaunch.query.isResultOverrideAllowed
import hunoia.luno.ui.theme.ElevationSmall
import hunoia.luno.ui.theme.LargeShape

/** 结果操作菜单：锚定行尾 3 点/钉住按钮，业务动作 + 通用 其他引擎/Pin。 */
@Composable
internal fun ResultActionMenu(
    result: SearchResult,
    query: String,
    pinned: Boolean,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (SearchAction, String) -> Unit,
    onTogglePin: (String, String) -> Unit,
    onShowEngines: (String) -> Unit,
) {
    val items = buildList {
        result.actions.forEach { item ->
            add(MenuActionItem(item.label) { onAction(item.action, query) })
        }
        if (result.source != SOURCE_ENGINE) {
            add(
                MenuActionItem(SearchActionLabel.Res(R.string.search_action_use_other_engine)) {
                    onShowEngines(query)
                },
            )
        }
        if (result.isResultOverrideAllowed() && query.isNotBlank()) {
            add(
                MenuActionItem(
                    SearchActionLabel.Res(
                        if (pinned) R.string.search_action_unpin else R.string.search_action_pin,
                    ),
                ) { onTogglePin(query, result.key) },
            )
        }
    }
    if (items.isEmpty()) return
    // 非 focusable popup：弹出时不抢窗口焦点，键盘保持打开、锚点不动（默认 focusable
    // 会顶掉输入框焦点，键盘收起连带 imePadding 塌缩，菜单跟着锚点跳）。外部点击关闭
    // 走窗口级 ACTION_OUTSIDE，非 focusable 下依然生效；back 由面板层 BackHandler 收口。
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = LargeShape,
        tonalElevation = ElevationSmall,
    ) {
        items.forEach { item ->
            DropdownMenuItem(
                text = { Text(item.label.resolve()) },
                onClick = {
                    onDismiss()
                    item.onClick()
                },
            )
        }
    }
}

private data class MenuActionItem(
    val label: SearchActionLabel,
    val onClick: () -> Unit,
)

@Composable
private fun SearchActionLabel.resolve(): String = when (this) {
    is SearchActionLabel.Res -> stringResource(id)
    is SearchActionLabel.Literal -> text
}
