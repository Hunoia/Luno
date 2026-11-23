package hunoia.luno.actions.library

import hunoia.luno.actions.icon.IconPalette

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import hunoia.luno.actions.R
import hunoia.luno.ui.component.pressClickable
import hunoia.luno.ui.component.sheet.TextInputDialog
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.PageMatchIconSize
import hunoia.luno.ui.theme.SmallShape

/** 图标区单选项：key 为 null 表示「默认」图标。 */
private data class IconChoiceItem(
    val key: String?,
    val vector: ImageVector,
    /** 可访问性描述（纯图标无文字，用 label 做 contentDescription）。 */
    val label: String,
)

/**
 * 更换图标弹窗：预设图标两排横滚（纯图标，无文字）+ 底部自定义文字/表情输入。
 *
 * - 点选图标只高亮，需点「确认」才生效并关闭（与输入共用确认）。
 * - 选中图标时输入框清空（`initialValue` 联动重置），二者互斥：确认时输入非空优先走自定义，
 *   否则走选中图标，都没有则 `null`（默认）。
 * - 当前为自定义文字时输入框回填、图标区无选中。
 */
@Composable
fun IconPickerSheet(
    onDismissRequest: () -> Unit,
    onPick: (String?) -> Unit,
    currentKey: String?,
    defaultIcon: ImageVector,
) {
    val isCustomText = currentKey != null && IconPalette.byKey(currentKey) == null
    var selectedKey by remember(currentKey) {
        mutableStateOf(if (isCustomText) null else currentKey)
    }
    val customText = if (isCustomText) currentKey.orEmpty() else ""

    // 默认图标 label 只在组合期读一次，不进 remember（stringResource 需组合上下文）。
    val defaultLabel = stringResource(R.string.action_library_icon_default)
    val icons = remember(defaultIcon, defaultLabel) {
        listOf(
            IconChoiceItem(null, defaultIcon, defaultLabel),
        ) + IconPalette.all.map { IconChoiceItem(it.key, it.vector, it.label) }
    }

    TextInputDialog(
        title = "",
        label = stringResource(R.string.action_library_icon_custom),
        // 选中图标时清空输入框：TextInputDialog 的 rememberSaveable 以 initialValue 为 key，
        // 值变化即重置文本。选图标切回「图标模式」，输入框不再残留旧文字。
        initialValue = if (selectedKey != null) "" else customText,
        onDismissRequest = onDismissRequest,
        onConfirm = { text ->
            // 输入非空优先（自定义文字）；否则选中图标；都无 → 默认。
            onPick(text.trim().takeIf { it.isNotEmpty() } ?: selectedKey)
        },
        content = {
            IconStrip(
                icons = icons,
                selectedKey = selectedKey,
                onSelect = { selectedKey = it },
            )
        },
    )
}

/**
 * 两排图标横滚：一个横滚容器放两行（同一 ScrollState，两行同步滚动）。
 * 纯图标无文字；选中项 primaryContainer 背景高亮。
 */
@Composable
private fun IconStrip(
    icons: List<IconChoiceItem>,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
) {
    val scrollState = rememberScrollState()
    val half = (icons.size + 1) / 2
    // 不 fillMaxWidth：横滚容器宽度由内容决定（行超出视口才可横滚）。
    Column(
        modifier = Modifier.horizontalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing)) {
            icons.take(half).forEach { item ->
                IconChoice(
                    item = item,
                    selected = item.key == selectedKey,
                    onClick = { onSelect(item.key) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing)) {
            icons.drop(half).forEach { item ->
                IconChoice(
                    item = item,
                    selected = item.key == selectedKey,
                    onClick = { onSelect(item.key) },
                )
            }
        }
    }
}

@Composable
private fun IconChoice(
    item: IconChoiceItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(PageMatchIconSize * 2)
            .clip(SmallShape)
            .background(if (selected) colorScheme.primaryContainer else Color.Transparent)
            .pressClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = item.vector,
            contentDescription = item.label,
            modifier = Modifier.size(PageMatchIconSize),
            tint = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
        )
    }
}
