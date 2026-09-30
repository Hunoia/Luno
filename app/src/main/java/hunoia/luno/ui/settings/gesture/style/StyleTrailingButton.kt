package hunoia.luno.ui.settings.gesture.style

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.ui.theme.SmallShape
import hunoia.luno.ui.theme.ListItemVerticalPadding
import hunoia.luno.R
import hunoia.luno.config.model.ActionPanelStyles

@Composable
fun StyleTrailingButton(
    currentStyle: ActionPanelStyles,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = SmallShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            text = actionPanelStyleText(currentStyle),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = ListItemVerticalPadding),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun actionPanelStyleText(style: ActionPanelStyles): String =
    stringResource(R.string.action_panel_style_arc)
