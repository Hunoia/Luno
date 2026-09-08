package hunoia.luno.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import hunoia.luno.config.defaults.SettingsUiDefaults
import hunoia.luno.ui.theme.ContentPaddingHorizontal
import hunoia.luno.ui.theme.ContentPaddingVerticalWithSection
import hunoia.luno.ui.theme.IconTextPadding
import hunoia.luno.ui.theme.ItemPadding
import hunoia.luno.ui.theme.CardShape
import hunoia.luno.ui.theme.MainSecondaryTextPadding
import hunoia.luno.ui.theme.MinItemHeight
import hunoia.luno.ui.theme.MinItemHeightNoSecondary

@Composable
fun ExpressiveRow(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    secondaryText: String = "",
    secondaryTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    icon: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .alpha(if (enabled) 1f else SettingsUiDefaults.DisabledAlpha)
            .fillMaxWidth(),
        onClick = { if (enabled) onClick() },
        enabled = enabled,
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let {
                    val minHeight = if (secondaryText.isEmpty()) {
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
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(IntrinsicSize.Max),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IconTextPadding),
            ) {
                icon?.invoke()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .width(IntrinsicSize.Max),
                    verticalArrangement = Arrangement.spacedBy(MainSecondaryTextPadding),
                ) {
                    Text(
                        modifier = Modifier.width(IntrinsicSize.Max),
                        text = text,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    if (secondaryText.isNotEmpty()) {
                        Text(
                            modifier = Modifier.width(IntrinsicSize.Max),
                            text = secondaryText,
                            color = secondaryTextColor,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (trailing != null) {
                trailing()
            } else if (enabled) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = text,
                )
            }
        }
    }
}
