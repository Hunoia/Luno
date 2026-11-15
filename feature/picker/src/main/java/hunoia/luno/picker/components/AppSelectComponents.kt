package hunoia.luno.picker

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Window
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hunoia.luno.bridge.launcher.LauncherInfo
import hunoia.luno.core.domain.AppInfo
import hunoia.luno.picker.R
import hunoia.luno.quickaccess.model.icon as appIcon
import hunoia.luno.ui.icon.icon as launcherIcon
import hunoia.luno.ui.component.PressIconButton
import hunoia.luno.ui.component.row.LunoSelectableRow
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.theme.BadgeIconSize
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.MotionSubtle
import hunoia.luno.ui.theme.RowIconSize

/**
 * 应用行：行本身选中应用；有快捷方式时行尾箭头展开「创建快捷方式 / 已有快捷方式」子行。
 *
 * 快捷方式不再是选择页顶层的两个独立分组，而是挂在对应应用下面——同一个应用只出现一次，
 * 用户找「微信 → 发消息」只靠应用名。
 */
@Composable
internal fun AppShortcutRow(
    appInfo: AppInfo,
    selected: Boolean,
    expanded: Boolean,
    hasChildren: Boolean,
    subtitle: String,
    onSelect: (Boolean) -> Unit,
    onToggleExpanded: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = LargeShape,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(MotionSubtle),
        label = "appShortcutArrow",
    )
    LunoSelectableRow(
        selected = selected,
        onSelectedChange = onSelect,
        modifier = modifier,
        iconModel = appInfo.appIcon,
        subtitle = subtitle,
        singleSelect = true,
        onLongClick = onLongClick,
        shape = shape,
        trailing = if (hasChildren) {
            {
                PressIconButton(
                    onClick = onToggleExpanded,
                ) {
                    // 箭头是独立控件：点行 = 选应用，点箭头 = 展开快捷方式，互不吃事件。
                    Icon(
                        modifier = Modifier
                            .size(RowIconSize)
                            .graphicsLayer { rotationZ = rotation },
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = stringResource(
                            if (expanded) hunoia.luno.design.R.string.group_collapse
                            else hunoia.luno.design.R.string.group_expand,
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            null
        },
        headline = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
            ) {
                if (appInfo.miniWindow) {
                    Icon(
                        modifier = Modifier.size(BadgeIconSize),
                        imageVector = Icons.Default.Window,
                        contentDescription = stringResource(R.string.mini_window_position_short),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    modifier = Modifier.weight(1f),
                    text = appInfo.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
    )
}

/** 已有快捷方式子行：与快捷方式来源图标一致，点行即选中并落盘。 */
@Composable
internal fun ShortcutChildRow(
    shortcutInfo: LauncherInfo.ShortcutInfo,
    selected: Boolean,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = LargeShape,
) {
    LunoSelectableRow(
        title = shortcutInfo.label,
        selected = selected,
        onSelectedChange = onSelect,
        modifier = modifier,
        iconModel = shortcutInfo.launcherIcon,
        singleSelect = true,
        shape = shape,
    )
}

/** 创建快捷方式子行：点行走系统的 ACTION_CREATE_SHORTCUT 创建器（不选中任何东西）。 */
@Composable
internal fun CreateShortcutChildRow(
    launcherInfo: LauncherInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = LargeShape,
) {
    LunoSettingsRow(
        title = stringResource(R.string.create_shortcut),
        subtitle = launcherInfo.label,
        iconModel = launcherInfo.launcherIcon,
        onClick = onClick,
        modifier = modifier,
        shape = shape,
    )
}

/** 子行整体右移一档：列表缩进表达层级，同时保留行内圆角与分割线几何。 */
internal val NestedRowIndent = 16.dp
