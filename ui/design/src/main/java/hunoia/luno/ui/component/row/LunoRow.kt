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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
 * 列表行的唯一实现，三种预设共用：内边距由页面 gutter 或 [LunoGroup] 提供，
 * 触控区统一 ≥ [MinInteractiveSize]。`leading` 与 icon/iconBitmap/iconModel/textIcon
 * 互斥（自定义优先）；四种图标参数是唯一允许的图标入口。
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
    onLongClick: (() -> Unit)? = null,
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
        onLongClick = onLongClick,
    )
}

/** 可选中行：用于选择器、快速搜索结果等。 */
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
 * 开关行。`onClick` 与 `onCheckedChange` 可并存：前者表示整行是导航入口
 * （此时不给 Role.Switch，行与开关是两个焦点）；只给后者时整行即开关。
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

/** 行组件自绘背景时的内部横向内边距：与 M3 ListItem 一致。 */
private val RowInnerPadding = 16.dp

/**
 * 行内输入行：标签左、无边框输入右（右对齐），底色与触控高度对齐 [LunoSettingsRow]，
 * 供编辑页把短字段收进 [LunoGroup] 分段卡片。错误态输入值与 [subtitle] 走 error 色。
 */
@Composable
fun LunoInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    subtitle: String = "",
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    isError: Boolean = false,
    singleLine: Boolean = true,
    shape: Shape = RoundedCornerShape(ContainerRadius),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(shape)
            .background(colorScheme.surfaceBright),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinInteractiveSize)
                .padding(horizontal = RowInnerPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurfaceVariant,
            )
            // 占位与输入叠在同一个定宽容器里：宽度必须落在 BasicTextField 本体上
            // （decorationBox 的核心文本节点拿松约束自适宽，TextAlign.End 不生效）。
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.End),
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        textAlign = TextAlign.End,
                        color = if (isError) colorScheme.error else colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(colorScheme.primary),
                    singleLine = singleLine,
                    keyboardOptions = keyboardOptions,
                )
            }
        }
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) colorScheme.error else subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = RowInnerPadding, end = RowInnerPadding, bottom = 10.dp),
            )
        }
    }
}

/**
 * 卡内无边框输入：组标题作标签，卡内只有输入与常驻提示行，网址模板这类长内容用。
 * 底色与行组件一致；错误态输入值与 [supportingText] 走 error 色。
 */
@Composable
fun LunoCardInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    supportingText: String = "",
    isError: Boolean = false,
    singleLine: Boolean = true,
    shape: Shape = RoundedCornerShape(ContainerRadius),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(shape)
            .background(colorScheme.surfaceBright)
            .padding(horizontal = RowInnerPadding, vertical = 12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.titleMedium.copy(
                color = if (isError) colorScheme.error else colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(colorScheme.primary),
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            decorationBox = { innerField ->
                Box(Modifier.fillMaxWidth()) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerField()
                }
            },
        )
        if (supportingText.isNotEmpty()) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) colorScheme.error else colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

