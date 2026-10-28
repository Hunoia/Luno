package hunoia.luno.ui.component.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.TimelineBadgeSize
import hunoia.luno.ui.theme.TimelineRailWidth
import hunoia.luno.ui.theme.TimelineRailX

/**
 * 时间线容器（条件树 / 步骤流共用）：左侧纵贯连接线，线心在 [TimelineRailX]。
 * 节点行用 [TimelineNode] 占出徽标位（宽 [TimelineBadgeSize]，圆心落在 rail 上）；
 * 非节点子项（分支标签、添加按钮）用 [timelineIndent] 与节点内容对齐。
 */
@Composable
fun TimelineColumn(
    modifier: Modifier = Modifier,
    spacing: Dp = CardInnerSpacing,
    railColor: Color = MaterialTheme.colorScheme.outlineVariant,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.drawBehind {
            val x = TimelineRailX.toPx()
            val cap = TimelineBadgeSize.toPx() / 2f
            if (size.height > cap * 2f) {
                drawLine(
                    color = railColor,
                    start = Offset(x, cap),
                    end = Offset(x, size.height - cap),
                    strokeWidth = TimelineRailWidth.toPx(),
                )
            }
        },
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

@Composable
fun TimelineNode(
    badge: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(TimelineBadgeSize),
            contentAlignment = Alignment.Center,
        ) {
            badge()
        }
        Spacer(
            modifier = Modifier.size(CardInnerSpacing),
        )
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

/** 非节点子项的缩进：与节点内容左对齐。 */
fun timelineIndent(): Modifier =
    Modifier.padding(
        start = TimelineBadgeSize + CardInnerSpacing,
    )

@Composable
fun TimelineBadge(
    text: String,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(TimelineBadgeSize)
            .background(
                color = if (error) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (error) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
        )
    }
}
