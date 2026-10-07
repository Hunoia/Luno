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
 * 分组内第 index 项的圆角（[count] 项）：只有组首/组尾两角走 [ContainerRadius]，中间走 [ConnectionRadius]。
 *
 * **由调用方传入行组件的 `shape`**，[LunoGroup] 不会自动分配 —— 它的 `content` 是裸
 * `ColumnScope` 插槽，拿不到子项数量与下标，且子项常是卡/编辑器/`AnimatedVisibility` 而非同质行。
 * 行列表记得用 `forEachIndexed` 配 `segmentedShape(index, count)`。
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
 * 可选中列表里第 index 项的圆角：按**选中段**而不是位置切角。
 *
 * `flags[i]` 为该行是否选中。相邻同状态的行连成一段，段内边用 [ConnectionRadius] 缝合，
 * 段的两个外边用 [ContainerRadius]。这样非连续选中（如第 1、3 项选中、第 2 项未选）时，
 * 选中底色不会以方角渗进未选邻居。
 *
 * `flags` 为 null 时退化为 [segmentedShape]（导航列表用不到，保留给调用方图省事）。
 */
fun selectedSegmentedShape(index: Int, count: Int, flags: List<Boolean>?): RoundedCornerShape {
    if (flags == null) return segmentedShape(index, count)
    fun sameAsNeighbor(neighbor: Int): Boolean =
        neighbor in 0 until count && flags.getOrElse(neighbor) { false } == flags.getOrElse(index) { false }
    val top = if (index == 0 || !sameAsNeighbor(index - 1)) ContainerRadius else ConnectionRadius
    val bottom = if (index == count - 1 || !sameAsNeighbor(index + 1)) ContainerRadius else ConnectionRadius
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
