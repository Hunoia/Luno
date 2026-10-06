package hunoia.luno.ui.component.row

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.ConnectionRadius
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.ContainerRadius
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SegmentedGap

private val GroupHeaderPadding =
    PaddingValues(start = PageGutter, top = CardInnerSpacing, bottom = ListSpacing)
private val GroupSubtitlePadding =
    PaddingValues(start = PageGutter, end = PageGutter, bottom = CardInnerSpacing)

/**
 * 分组内第 index 项的圆角（[count] 项）。
 *
 * 现状这个函数被界面手工调用 60 次，还有 2 处直接抄数字、1 处漏掉 —— 圆角因此到处不一致。
 * 现在由 [LunoGroup] 自动分配，界面不应再手工传 shape。
 */
fun segmentedShape(index: Int, count: Int): RoundedCornerShape {
    val isSingle = count <= 1
    val top = if (isSingle || index == 0) ContainerRadius else ConnectionRadius
    val bottom = if (isSingle || index == count - 1) ContainerRadius else ConnectionRadius
    return RoundedCornerShape(
        topStart = top,
        topEnd = top,
        bottomStart = bottom,
        bottomEnd = bottom,
    )
}

/**
 * 分组容器（见 docs/ui-spec.md §5）。
 *
 * 组间距 [hunoia.luno.ui.theme.ListSpacing]、组内间距 [contentSpacing]（1dp 圆角缝合）。
 * `contentSpacing` 有默认值，**不要显式传默认值**。
 *
 * @param badgeText 组头状态徽标文案（如缺项计数）；null 不显示。
 * @param badgeError 置 true 徽标走 error 色（组内有校验失败）。
 */
@Composable
fun LunoGroup(
    title: String = "",
    subtitle: String = "",
    modifier: Modifier = Modifier,
    contentSpacing: Dp = SegmentedGap,
    badgeText: String? = null,
    badgeError: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(GroupHeaderPadding).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (badgeText != null) {
                    SectionBadge(text = badgeText, error = badgeError)
                }
            }
        }
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(GroupSubtitlePadding),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(contentSpacing)) {
            content()
        }
    }
}

/**
 * 组头状态徽标：缺项计数 / 错误提示。
 *
 * 错误态走 error 色，常态走 primary 色；只在组头右侧出现。
 */
@Composable
fun SectionBadge(
    text: String,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(ContainerRadius),
        color = if (error) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (error) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(
                horizontal = CardInnerSpacing,
                vertical = SegmentedGap,
            ),
            maxLines = 1,
        )
    }
}
