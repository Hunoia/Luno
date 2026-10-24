package hunoia.luno.quicklaunch.panel

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.config.model.SearchEngine
import hunoia.luno.config.model.badgeText
import hunoia.luno.quicklaunch.query.SearchAction
import hunoia.luno.quicklaunch.query.SearchActionLabel
import hunoia.luno.quicklaunch.query.SearchResult
import hunoia.luno.quicklaunch.query.SOURCE_ENGINE
import hunoia.luno.quicklaunch.query.isResultOverrideAllowed
import hunoia.luno.ui.component.TextIcon
import hunoia.luno.ui.component.row.LunoSelectableRow
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SheetListMaxHeight

/** 引擎选择：主引擎（列表第一位）高亮，点即用该引擎执行当前查询。 */
@Composable
internal fun SearchEnginePickerSheet(
    engines: List<SearchEngine>,
    onPick: (SearchEngine) -> Unit,
    onDismiss: () -> Unit,
) {
    LunoSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.search_engine_other),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PageGutter, vertical = 12.dp),
        )
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PageGutter)
                .heightIn(max = SheetListMaxHeight)
                .verticalScroll(rememberScrollState()),
        ) {
            engines.forEachIndexed { index, engine ->
                LunoSelectableRow(
                    title = engine.name,
                    textIcon = TextIcon(engine.badgeText),
                    subtitle = engine.trigger.takeIf { it.isNotBlank() }.orEmpty(),
                    // 主引擎 = 列表第一位，不再单独选默认。
                    selected = index == 0,
                    singleSelect = true,
                    onSelectedChange = { onPick(engine) },
                    shape = segmentedShape(index, engines.size),
                )
            }
        }
    }
}

/** 结果操作 Sheet：业务动作 + 通用 Pin/其他引擎。 */
@Composable
internal fun SearchResultActionSheet(
    result: SearchResult,
    query: String,
    pinned: Boolean,
    onAction: (SearchAction, String) -> Unit,
    onTogglePin: (String, String) -> Unit,
    onShowEngines: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val items = buildList {
        result.actions.forEach { item ->
            add(SheetActionItem(item.label) { onAction(item.action, query) })
        }
        if (result.source != SOURCE_ENGINE) {
            add(
                SheetActionItem(SearchActionLabel.Res(R.string.search_action_use_other_engine)) {
                    onShowEngines(query)
                },
            )
        }
        if (result.isResultOverrideAllowed() && query.isNotBlank()) {
            add(
                SheetActionItem(
                    SearchActionLabel.Res(
                        if (pinned) R.string.search_action_unpin else R.string.search_action_pin,
                    ),
                ) { onTogglePin(query, result.key) },
            )
        }
    }
    if (items.isEmpty()) return
    LunoSheet(onDismissRequest = onDismiss) {
        Text(
            text = result.title,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PageGutter, vertical = 12.dp),
        )
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PageGutter)
                .heightIn(max = SheetListMaxHeight)
                .verticalScroll(rememberScrollState()),
        ) {
            items.forEachIndexed { index, item ->
                LunoSettingsRow(
                    title = item.label.resolve(),
                    trailing = {},
                    shape = segmentedShape(index, items.size),
                    onClick = {
                        onDismiss()
                        item.onClick()
                    },
                )
            }
        }
    }
}

private data class SheetActionItem(
    val label: SearchActionLabel,
    val onClick: () -> Unit,
)

@Composable
private fun SearchActionLabel.resolve(): String = when (this) {
    is SearchActionLabel.Res -> stringResource(id)
    is SearchActionLabel.Literal -> text
}
