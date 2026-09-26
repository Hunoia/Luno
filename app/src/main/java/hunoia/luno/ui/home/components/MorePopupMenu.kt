package hunoia.luno.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.R

@Composable
fun MorePopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onResetClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
    ) {
        MoreMenuItem(
            icon = Icons.Filled.Save,
            label = stringResource(id = R.string.backup),
            onClick = {
                onDismissRequest()
                onBackupClick()
            },
        )
        MoreMenuItem(
            icon = Icons.Filled.Restore,
            label = stringResource(id = R.string.restore),
            onClick = {
                onDismissRequest()
                onRestoreClick()
            },
        )
        MoreMenuItem(
            icon = Icons.Filled.Refresh,
            label = stringResource(id = R.string.reset),
            onClick = {
                onDismissRequest()
                onResetClick()
            },
        )
    }
}

@Composable
private fun MoreMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text = label) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        },
        onClick = onClick,
    )
}
