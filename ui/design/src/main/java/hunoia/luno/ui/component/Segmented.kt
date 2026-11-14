package hunoia.luno.ui.component

import androidx.annotation.StringRes
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import hunoia.luno.design.R
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.MinInteractiveSize
import hunoia.luno.ui.theme.MotionSubtle
import hunoia.luno.ui.theme.RowIconSize
import hunoia.luno.ui.theme.SegmentedGap

@Composable
fun SheetGroupHeader(@StringRes titleRes: Int) {
    SheetGroupHeader(stringResource(titleRes))
}

@Composable
fun SheetGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = ListSpacing, bottom = SegmentedGap),
    )
}

/**
 * 可收起分组头：标题 + 箭头，整行点击切换，箭头 180ms 转 180°（`MotionSubtle`）。
 *
 * 用于同一屏里分组多、需要就地收起来看下一组的场合（动作选择页合并后的
 * 应用 / 快捷方式分组）。不可收起的分组继续用单参数重载，视觉保持不变。
 */
@Composable
fun SheetGroupHeader(
    title: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(MotionSubtle),
        label = "sheetGroupHeaderArrow",
    )
    val actionLabel = stringResource(
        if (expanded) R.string.group_collapse else R.string.group_expand,
    )
    val stateLabel = stringResource(
        if (expanded) R.string.group_state_expanded else R.string.group_state_collapsed,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinInteractiveSize)
            .pressClickable(
                role = Role.Button,
                onClickLabel = actionLabel,
                onClick = onToggleExpanded,
            )
            .semantics { stateDescription = stateLabel }
            .padding(top = ListSpacing, bottom = SegmentedGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(RowIconSize)
                .graphicsLayer { rotationZ = rotation },
        )
    }
}
