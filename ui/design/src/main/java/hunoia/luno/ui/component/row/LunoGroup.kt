package hunoia.luno.ui.component.row

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import hunoia.luno.config.model.ListStyle
import hunoia.luno.ui.theme.CapsuleShape
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.ConnectionRadius
import hunoia.luno.ui.theme.ContainerRadius
import hunoia.luno.ui.theme.containerShape
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SegmentedGap
import androidx.compose.foundation.background

private val GroupSubtitlePadding =
    PaddingValues(start = PageGutter, end = PageGutter, bottom = CardInnerSpacing)

/**
 * 分组内第 index 项（共 [count] 项）的圆角：组首/组尾走 [ContainerRadius]，
 * 中间走 [ConnectionRadius]。
 *
 * 必须由调用方传给行组件的 `shape`：[LunoGroup] 的 content 是裸 `ColumnScope`
 * 插槽，拿不到子项数量与下标，子项还可能是卡/编辑器/`AnimatedVisibility`。
 * 行列表用 `forEachIndexed` 配 `segmentedShape(index, count)`。
 */
fun segmentedShape(index: Int, count: Int): Shape {
    val isSingle = count <= 1
    val top = if (isSingle || index == 0) ContainerRadius else ConnectionRadius
    val bottom = if (isSingle || index == count - 1) ContainerRadius else ConnectionRadius
    return containerShape(
        topStart = top,
        topEnd = top,
        bottomStart = bottom,
        bottomEnd = bottom,
    )
}

/**
 * 由容器自动计算分段圆角的静态内容组。
 *
 * 子项通过 [SegmentedGroupScope.item] 注册，只有实际执行到的 item 才会计数。
 * 子项接收容器按最终数量计算出的 shape，因此不需要手动维护总数。
 */
@Composable
fun LunoSegmentedGroup(
    modifier: Modifier = Modifier,
    contentSpacing: Dp = SegmentedGap,
    content: @Composable SegmentedGroupScope.() -> Unit,
) {
    val scope = SegmentedGroupScope()
    scope.content()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(contentSpacing),
    ) {
        scope.items.forEachIndexed { index, item ->
            item(segmentedShape(index, scope.items.size))
        }
    }
}

class SegmentedGroupScope internal constructor() {
    internal val items = mutableListOf<@Composable (Shape) -> Unit>()

    fun item(content: @Composable (Shape) -> Unit) {
        items += content
    }
}

/**
 * 可选中列表里第 index 项的圆角：按**选中段**而不是位置切角。
 *
 * 相邻同状态的行连成一段，段内边用 [ConnectionRadius] 缝合，段外边用
 * [ContainerRadius]，避免选中底色以方角渗进未选邻居。
 * `flags` 为 null 时退化为 [segmentedShape]。
 */
fun selectedSegmentedShape(index: Int, count: Int, flags: List<Boolean>?): Shape {
    if (flags == null) return segmentedShape(index, count)
    fun sameAsNeighbor(neighbor: Int): Boolean =
        neighbor in 0 until count && flags.getOrElse(neighbor) { false } == flags.getOrElse(index) { false }
    val top = if (index == 0 || !sameAsNeighbor(index - 1)) ContainerRadius else ConnectionRadius
    val bottom = if (index == count - 1 || !sameAsNeighbor(index + 1)) ContainerRadius else ConnectionRadius
    return containerShape(
        topStart = top,
        topEnd = top,
        bottomStart = bottom,
        bottomEnd = bottom,
    )
}

/**
 * 分组容器。
 *
 * 组间距 [ListSpacing]，组内间距 [contentSpacing]（1dp 圆角缝合），默认值即可，
 * 不要显式传。组头右侧可挂状态徽标，[badgeError] 时走 error 色。
 * 标题下第一项不是等高容器（如裸输入框）时，可传小一点的 [titleBottomSpacing]
 * 让视觉间距与其他组一致。
 */
@Composable
fun LunoGroup(
    title: String = "",
    subtitle: String = "",
    modifier: Modifier = Modifier,
    contentSpacing: Dp = SegmentedGap,
    titleBottomSpacing: Dp = ListSpacing,
    badgeText: String? = null,
    badgeError: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .padding(
                        start = PageGutter,
                        top = CardInnerSpacing,
                        bottom = titleBottomSpacing,
                    )
                    .fillMaxWidth(),
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
        // 大卡分割线风格：整组一张大圆角卡片，行间由行组件顶部画分割线
        // （LocalInDividedCard 让行忽略自身 shape/底色，由卡片统一托底）。
        if (LocalListStyle.current == ListStyle.CardDivided) {
            CompositionLocalProvider(LocalInDividedCard provides true) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(containerShape(ContainerRadius))
                        .background(MaterialTheme.colorScheme.surfaceBright),
                ) {
                    content()
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(contentSpacing)) {
                content()
            }
        }
    }
}

/**
 * 大卡分割线风格下，行是否应在自身顶部画分割线（首行 / 单项除外）。
 *
 * 依据行传入的分段形状判定：顶角为 [ConnectionRadius] 说明不是组首，
 * 需在其上画线。覆盖 `segmentedShape` 的中段与尾段两种组合；
 * `selectedSegmentedShape` 的选中段边界行会少画一条线，属可接受的视觉近似。
 */
internal fun showsDividedCardTopLine(shape: Shape): Boolean {
    val connection = ConnectionRadius
    val middle = containerShape(connection, connection, connection, connection)
    val last = containerShape(connection, connection, ContainerRadius, ContainerRadius)
    return shape == middle || shape == last
}

@Composable
fun SectionBadge(
    text: String,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) {
    Surface(
        modifier = modifier,
        // 组头徽标是文字胶囊：高度随字体变，用百分比胶囊角色。
        shape = CapsuleShape,
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
