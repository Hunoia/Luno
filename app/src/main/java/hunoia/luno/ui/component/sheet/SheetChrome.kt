package hunoia.luno.ui.component.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.ui.component.AppSearchBar
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.ContentBottom
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SegmentedGap
import hunoia.luno.ui.theme.SheetListMaxHeight

/**
 * 弹层标题栏（见 docs/ui-spec.md §6）：左标题 + 右侧可选操作区。
 *
 * 各 sheet 手写的标题 Row（字号/边距各异）收敛到这里。
 */
@Composable
fun SheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PageGutter, vertical = CardInnerSpacing),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = title,
            style = MaterialTheme.typography.titleLarge,
        )
        trailing?.invoke()
    }
}

/**
 * Sheet 内「搜索框 + 结果列表」的公共骨架：搜索框 + 限高 [LazyColumn]（统一横向留白、
 * 底部留白与行间距）。在自己的 Column 里使用，列表内容通过 [content] 提供。
 */
@Composable
fun SheetSearchList(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.search_hint_all),
    content: LazyListScope.() -> Unit,
) {
    AppSearchBar(
        query = query,
        onQueryChange = onQueryChange,
        modifier = modifier.fillMaxWidth().padding(horizontal = PageGutter),
        placeholder = placeholder,
    )
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = SheetListMaxHeight)
            .padding(horizontal = PageGutter),
        contentPadding = PaddingValues(bottom = ContentBottom),
        verticalArrangement = Arrangement.spacedBy(SegmentedGap),
    ) {
        content()
    }
}

/**
 * 弹层底部操作区（见 docs/ui-spec.md §6）：取消 + 确认。
 *
 * 确认文案可覆盖（删除/重置类语义需要自己的文案）；[confirmEnabled] 为 false 时确认禁用。
 */
@Composable
fun SheetFooterActions(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String = stringResource(R.string.confirm),
    dismissLabel: String = stringResource(R.string.cancel),
    confirmEnabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PageGutter, vertical = CardInnerSpacing),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onDismissRequest) {
            Text(dismissLabel)
        }
        TextButton(onClick = onConfirm, enabled = confirmEnabled) {
            Text(confirmLabel)
        }
    }
}

