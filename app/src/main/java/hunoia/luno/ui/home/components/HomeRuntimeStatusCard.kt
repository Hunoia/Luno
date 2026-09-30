package hunoia.luno.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.TaskAlt
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.ui.theme.PageGutter

@Composable
fun HomeRuntimeStatusCard(
    runtimeStatus: HomeRuntimeStatus,
    isGestureSwitchEnabled: Boolean,
    onGestureSwitchEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = when (runtimeStatus.level) {
        HomeRuntimeStatusLevel.Running -> colorScheme.primaryContainer
        HomeRuntimeStatusLevel.NeedsAttention -> colorScheme.secondaryContainer
        HomeRuntimeStatusLevel.Unavailable -> colorScheme.errorContainer
    }
    val contentColor = when (runtimeStatus.level) {
        HomeRuntimeStatusLevel.Running -> colorScheme.onPrimaryContainer
        HomeRuntimeStatusLevel.NeedsAttention -> colorScheme.onSecondaryContainer
        HomeRuntimeStatusLevel.Unavailable -> colorScheme.onErrorContainer
    }
    val icon: ImageVector = when (runtimeStatus.level) {
        HomeRuntimeStatusLevel.Running -> Icons.TwoTone.TaskAlt
        HomeRuntimeStatusLevel.NeedsAttention -> Icons.TwoTone.Info
        HomeRuntimeStatusLevel.Unavailable -> Icons.TwoTone.Warning
    }
    val titleRes = when (runtimeStatus.level) {
        HomeRuntimeStatusLevel.Running -> R.string.home_status_running
        HomeRuntimeStatusLevel.NeedsAttention -> R.string.home_status_needs_attention
        HomeRuntimeStatusLevel.Unavailable -> R.string.home_status_unavailable
    }
    val descRes = runtimeStatus.primaryIssue.descRes ?: R.string.home_status_running_desc

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = hunoia.luno.ui.theme.LargeShape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PageGutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .padding(end = PageGutter),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(descRes),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(
                modifier = Modifier.padding(start = PageGutter),
                checked = isGestureSwitchEnabled,
                onCheckedChange = onGestureSwitchEnabledChange,
            )
        }
    }
}

private val HomePrimaryIssue.descRes: Int?
    get() = when (this) {
        HomePrimaryIssue.AccessibilityDisabled -> R.string.home_status_accessibility_desc
        HomePrimaryIssue.GestureDisabled -> R.string.home_status_gesture_desc
        HomePrimaryIssue.ShizukuNotRunning -> R.string.home_status_shizuku_not_running_desc
        HomePrimaryIssue.ShizukuNotAuthorized -> R.string.home_status_shizuku_not_authorized_desc
        HomePrimaryIssue.KeepAliveDisabled -> R.string.home_status_keep_alive_desc
        HomePrimaryIssue.None -> null
    }
