package hunoia.luno.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hunoia.luno.config.defaults.SettingsUiDefaults
import hunoia.luno.ui.theme.ContentPaddingHorizontal
import hunoia.luno.ui.theme.ContentPaddingVerticalWithSection
import hunoia.luno.ui.theme.DividerHeight
import hunoia.luno.ui.theme.IconTextPadding
import hunoia.luno.ui.theme.ItemPadding
import hunoia.luno.ui.theme.MainSecondaryTextPadding
import hunoia.luno.ui.theme.MarkColorSize
import hunoia.luno.ui.theme.MinItemHeight
import hunoia.luno.ui.theme.MinItemHeightNoSecondary

@Composable
fun ExpressiveSwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    subtitle: String = "",
    secondaryTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    markColor: Color = Color.Unspecified,
    onMarkColorClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    mainSecondaryTextPadding: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .alpha(if (enabled) 1f else SettingsUiDefaults.DisabledAlpha)
            .fillMaxWidth(),
        onClick = {
            if (onClick != null) {
                onClick()
            } else {
                onCheckedChange(!checked)
            }
        },
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let {
                    val minHeight = if (subtitle.isEmpty()) {
                        MinItemHeightNoSecondary
                    } else {
                        MinItemHeight
                    }
                    it.heightIn(min = minHeight)
                }
                .padding(
                    horizontal = ContentPaddingHorizontal,
                    vertical = ContentPaddingVerticalWithSection,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ItemPadding),
        ) {
            val paddingVV = when (mainSecondaryTextPadding) {
                true -> MainSecondaryTextPadding
                else -> 0.dp
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(IntrinsicSize.Max),
                verticalArrangement = Arrangement.spacedBy(paddingVV),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(IconTextPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icon != null) {
                        icon()
                    }
                    Text(
                        modifier = Modifier.weight(1f),
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    if (markColor.isSpecified) {
                        Box(
                            modifier = Modifier
                                .size(MarkColorSize)
                                .background(color = markColor, shape = CircleShape)
                                .then(
                                    if (onMarkColorClick != null) {
                                        Modifier.clickable(
                                            indication = null,
                                            interactionSource = null,
                                            onClick = onMarkColorClick,
                                        )
                                    } else Modifier
                                ),
                        )
                    }
                }
                if (subtitle.isNotEmpty()) {
                    Text(
                        modifier = Modifier.width(IntrinsicSize.Max),
                        text = subtitle,
                        color = secondaryTextColor,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onClick != null) {
                VerticalDivider(
                    modifier = Modifier.height(DividerHeight),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Switch(
                    enabled = enabled,
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                )
            }
        }
    }
}
