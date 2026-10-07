package hunoia.luno.ui.component.row

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import hunoia.luno.ui.theme.AnimNormal
import hunoia.luno.ui.theme.ContainerRadius
import hunoia.luno.ui.theme.DisabledAlpha
import hunoia.luno.ui.theme.MinInteractiveSize
import hunoia.luno.ui.theme.RowIconSize
import hunoia.luno.ui.theme.SmallShape

/**
 * 列表行的唯一实现（见 docs/ui-spec.md §4）。
 *
 * 三种预设 [LunoSettingsRow] / [LunoSelectableRow] / [LunoSwitchRow] 共用本 base，
 * 内边距统一贴边（由页面 gutter 或 [LunoGroup] 提供）、触控区统一 ≥ [MinInteractiveSize]。
 *
 * @param leading 自定义前置内容；与 icon/iconBitmap/iconModel/textIcon 互斥（自定义优先）。
 * @param icon / iconBitmap / iconModel / textIcon 四种派生图标，是唯一允许的图标入口，
 *        内部统一渲染进前置槽位，避免"每处自己写一个图标包装"。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun LunoRowBase(
    title: String = "",
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(ContainerRadius),
    enabled: Boolean = true,
    subtitle: String = "",
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    headline: (@Composable () -> Unit)? = null,
    marquee: Boolean = false,
    icon: ImageVector? = null,
    iconBitmap: androidx.compose.ui.graphics.ImageBitmap? = null,
    iconModel: Any? = null,
    textIcon: hunoia.luno.ui.component.TextIcon? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onSelectedChange: ((Boolean) -> Unit)? = null,
    selected: Boolean = false,
    selectionRole: Role = Role.Checkbox,
    containerColor: Color? = null,
    selectedContainerColor: Color? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val selectable = onSelectedChange != null

    val background by animateColorAsState(
        targetValue = if (selectable && selected) {
            selectedContainerColor ?: colorScheme.primaryContainer
        } else {
            containerColor ?: colorScheme.surfaceBright
        },
        animationSpec = tween(AnimNormal.toInt()),
        label = "lunoRowBackground",
    )
    val iconTint = if (selectable && selected) colorScheme.primary else colorScheme.onSurfaceVariant

    val clickableModifier = when {
        onLongClick != null && onClick != null -> Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick,
        )
        onClick != null -> Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
        selectable -> Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = selectionRole,
            onClick = { onSelectedChange(!selected) },
        )
        else -> Modifier
    }

    ListItem(
        modifier = modifier
            .heightIn(min = MinInteractiveSize)
            .alpha(if (enabled) 1f else DisabledAlpha)
            .then(clickableModifier)
            .clip(shape)
            .background(background)
            .semantics(mergeDescendants = true) {
                if (selectable) this.selected = selected
            },
        headlineContent = headline ?: {
            Text(
                modifier = if (marquee) Modifier.basicMarquee(velocity = MarqueeVelocity) else Modifier,
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        supportingContent = subtitle.takeIf { it.isNotEmpty() }?.let { s ->
            {
                Text(
                    text = s,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        leadingContent = leading
            ?: textIcon?.let { ti -> { hunoia.luno.ui.component.TextIconGlyph(text = ti.text, size = RowIconSize, color = iconTint) } }
            ?: icon?.let { iv ->
                {
                    Icon(
                        modifier = Modifier.size(RowIconSize),
                        imageVector = iv,
                        contentDescription = null,
                        tint = iconTint,
                    )
                }
            }
            ?: iconBitmap?.let { bm ->
                {
                    Image(
                        bitmap = bm,
                        modifier = Modifier.size(RowIconSize).clip(SmallShape),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            ?: iconModel?.let { model ->
                {
                    AsyncImage(
                        modifier = Modifier.size(RowIconSize).clip(SmallShape),
                        model = model,
                        contentDescription = null,
                        imageLoader = LocalContext.current.imageLoader,
                        contentScale = ContentScale.Crop,
                    )
                }
            },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = colorScheme.onSurface,
            leadingIconColor = iconTint,
            trailingIconColor = colorScheme.onSurfaceVariant,
            supportingColor = subtitleColor,
        ),
    )
}

/** 跑马灯滚动速度。 */
private val MarqueeVelocity = 50.dp // 跑马灯速度参数（dp/s），非间距令牌

private val chevron: @Composable () -> Unit = {
    Icon(
        modifier = Modifier.size(RowIconSize),
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
    )
}

/** 导航 / 设置行：有 onClick 且无 trailing 时自动带右箭头。 */
@Composable
fun LunoSettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconBitmap: androidx.compose.ui.graphics.ImageBitmap? = null,
    iconModel: Any? = null,
    textIcon: hunoia.luno.ui.component.TextIcon? = null,
    subtitle: String = "",
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(ContainerRadius),
    enabled: Boolean = true,
) {
    LunoRowBase(
        title = title,
        modifier = modifier,
        shape = shape,
        enabled = enabled,
        subtitle = subtitle,
        subtitleColor = subtitleColor,
        icon = icon,
        iconBitmap = iconBitmap,
        iconModel = iconModel,
        textIcon = textIcon,
        leading = leading,
        trailing = trailing ?: chevron.takeIf { enabled && onClick != null },
        onClick = onClick,
    )
}

/** 可选中行：用于选择器、快速搜索结果等（替代原 SelectableListItem）。 */
@Composable
fun LunoSelectableRow(
    title: String = "",
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    headline: (@Composable () -> Unit)? = null,
    marquee: Boolean = false,
    icon: ImageVector? = null,
    iconBitmap: androidx.compose.ui.graphics.ImageBitmap? = null,
    iconModel: Any? = null,
    textIcon: hunoia.luno.ui.component.TextIcon? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(ContainerRadius),
    enabled: Boolean = true,
    /** true=单选（语义 role 用 RadioButton），false=多选（Checkbox）。 */
    singleSelect: Boolean = false,
    containerColor: Color? = null,
    selectedContainerColor: Color? = null,
) {
    LunoRowBase(
        title = title,
        modifier = modifier,
        shape = shape,
        enabled = enabled,
        subtitle = subtitle,
        subtitleColor = subtitleColor,
        headline = headline,
        marquee = marquee,
        icon = icon,
        iconBitmap = iconBitmap,
        iconModel = iconModel,
        textIcon = textIcon,
        leading = leading,
        trailing = trailing,
        onClick = onLongClick?.let { { onSelectedChange(!selected) } },
        onLongClick = onLongClick,
        onSelectedChange = onSelectedChange,
        selected = selected,
        selectionRole = if (singleSelect) Role.RadioButton else Role.Checkbox,
        containerColor = containerColor,
        selectedContainerColor = selectedContainerColor,
    )
}

/**
 * 开关行。
 *
 * [onClick] 与 [onCheckedChange] 可以并存：前者是"整行是导航入口"的语义（此时不给
 * Role.Switch，行与开关是两个焦点）；只给 [onCheckedChange] 时整行即开关。
 */
@Composable
fun LunoSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconBitmap: androidx.compose.ui.graphics.ImageBitmap? = null,
    iconModel: Any? = null,
    textIcon: hunoia.luno.ui.component.TextIcon? = null,
    subtitle: String = "",
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(ContainerRadius),
    enabled: Boolean = true,
) {
    LunoRowBase(
        title = title,
        modifier = modifier,
        shape = shape,
        enabled = enabled,
        subtitle = subtitle,
        subtitleColor = subtitleColor,
        icon = icon,
        iconBitmap = iconBitmap,
        iconModel = iconModel,
        textIcon = textIcon,
        leading = leading,
        trailing = {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Switch(enabled = enabled, checked = checked, onCheckedChange = onCheckedChange)
            }
        },
        onClick = onClick ?: { onCheckedChange(!checked) },
    )
}

/** 图标按钮的最小触控区包装：视觉尺寸可以小，触控区必须 ≥ [MinInteractiveSize]。 */
@Composable
fun TouchTargetBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(MinInteractiveSize),
        contentAlignment = Alignment.Center,
    ) { content() }
}
