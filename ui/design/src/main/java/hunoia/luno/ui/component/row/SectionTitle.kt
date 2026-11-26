package hunoia.luno.ui.component.row

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import hunoia.luno.design.R
import hunoia.luno.ui.component.pressClickable
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.MinInteractiveSize
import hunoia.luno.ui.theme.MotionSubtle
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.RowIconSize
import hunoia.luno.ui.theme.SegmentedGap

/**
 * 分组标题的唯一实现：组卡（[LunoGroup]）的组头与独立列表（吸顶字母节头）共用。
 *
 * 两侧页边距由这里统一给（[PageGutter]），放在已有左右留白的容器里时用 [modifier] 覆盖。
 * [expanded] + [onToggle] 同时给出后可收起：整行可点、行尾箭头旋转，并播报展开 / 收起。
 */
@Composable
fun LunoSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    badgeText: String? = null,
    badgeError: Boolean = false,
    trailing: @Composable () -> Unit = {},
    topSpacing: Dp = ListSpacing,
    bottomSpacing: Dp = SegmentedGap,
    expanded: Boolean? = null,
    onToggle: (() -> Unit)? = null,
) {
    val collapsible = expanded != null && onToggle != null
    val rotation by animateFloatAsState(
        targetValue = if (expanded == true) 180f else 0f,
        animationSpec = tween(MotionSubtle),
        label = "sectionTitleChevron",
    )
    val actionLabel = if (collapsible) {
        stringResource(if (expanded == true) R.string.group_collapse else R.string.group_expand)
    } else {
        ""
    }
    val stateLabel = if (collapsible) {
        stringResource(
            if (expanded == true) R.string.group_state_expanded else R.string.group_state_collapsed,
        )
    } else {
        ""
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onToggle != null && collapsible) {
                    Modifier
                        .heightIn(min = MinInteractiveSize)
                        .pressClickable(
                            role = Role.Button,
                            onClickLabel = actionLabel,
                            onClick = onToggle,
                        )
                        .semantics { stateDescription = stateLabel }
                } else {
                    Modifier
                },
            )
            .padding(
                start = PageGutter,
                end = PageGutter,
                top = topSpacing,
                bottom = bottomSpacing,
            ),
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
        trailing()
        if (collapsible) {
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(RowIconSize)
                    .rotate(rotation),
            )
        }
    }
}
