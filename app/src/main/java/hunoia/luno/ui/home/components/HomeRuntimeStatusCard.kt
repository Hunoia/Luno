package hunoia.luno.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.ui.theme.Spacing12
import hunoia.luno.ui.theme.Spacing16
import hunoia.luno.ui.theme.Spacing20
import hunoia.luno.ui.theme.Spacing4
import hunoia.luno.ui.theme.Spacing8

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeRuntimeStatusCard(
    status: HomeRuntimeStatus,
    isGestureSwitchEnabled: Boolean,
    onGestureSwitchEnabledChange: (Boolean) -> Unit,
    onAction: (HomeRuntimeAction) -> Unit,
) {
    val showDetails = status.level != HomeRuntimeStatusLevel.Running ||
        status.primaryAction != HomeRuntimeAction.None ||
        status.secondaryAction != HomeRuntimeAction.None
    val containerColor = when (status.level) {
        HomeRuntimeStatusLevel.Running -> MaterialTheme.colorScheme.primaryContainer
        HomeRuntimeStatusLevel.NeedsAttention -> MaterialTheme.colorScheme.tertiaryContainer
        HomeRuntimeStatusLevel.Unavailable -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (status.level) {
        HomeRuntimeStatusLevel.Running -> MaterialTheme.colorScheme.onPrimaryContainer
        HomeRuntimeStatusLevel.NeedsAttention -> MaterialTheme.colorScheme.onTertiaryContainer
        HomeRuntimeStatusLevel.Unavailable -> MaterialTheme.colorScheme.onErrorContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (showDetails) Spacing20 else Spacing16),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing12),
            ) {
                Icon(
                    imageVector = when (status.level) {
                        HomeRuntimeStatusLevel.Running -> Icons.Default.CheckCircle
                        HomeRuntimeStatusLevel.NeedsAttention -> Icons.Default.Info
                        HomeRuntimeStatusLevel.Unavailable -> Icons.Default.Warning
                    },
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(Spacing20),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = status.titleRes()),
                        style = if (showDetails) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                        color = contentColor,
                    )
                    if (showDetails) {
                        Spacer(Modifier.height(Spacing4))
                        Text(
                            text = stringResource(id = status.descriptionRes()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor,
                        )
                    }
                }
            }

            Spacer(Modifier.height(if (showDetails) Spacing16 else Spacing8))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.enable_gesture),
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                )
                Switch(
                    checked = isGestureSwitchEnabled,
                    onCheckedChange = onGestureSwitchEnabledChange,
                )
            }

            val problematicItems = status.items.filter { !it.available }
            if (showDetails && problematicItems.isNotEmpty()) {
                Spacer(Modifier.height(Spacing12))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing8),
                    verticalArrangement = Arrangement.spacedBy(Spacing8),
                ) {
                    problematicItems.forEach { item ->
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = {
                            Text(text = stringResource(id = item.labelRes()))
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (item.available) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = null,
                            )
                        },
                    )
                }
                }
            }

            if (showDetails && (status.primaryAction != HomeRuntimeAction.None || status.secondaryAction != HomeRuntimeAction.None)) {
                Spacer(Modifier.height(Spacing16))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing12),
                ) {
                    if (status.primaryAction != HomeRuntimeAction.None) {
                        FilledTonalButton(
                            onClick = { onAction(status.primaryAction) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = stringResource(id = status.primaryAction.labelRes()))
                        }
                    }
                    if (status.secondaryAction != HomeRuntimeAction.None) {
                        OutlinedButton(
                            onClick = { onAction(status.secondaryAction) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = stringResource(id = status.secondaryAction.labelRes()))
                        }
                    }
                }
            }
        }
    }
}

private fun HomeRuntimeStatus.titleRes(): Int = when (primaryIssue) {
    HomePrimaryIssue.None -> R.string.home_status_running
    HomePrimaryIssue.AccessibilityDisabled,
    HomePrimaryIssue.GestureDisabled -> R.string.home_status_unavailable
    HomePrimaryIssue.ShizukuNotInstalled,
    HomePrimaryIssue.ShizukuNotRunning,
    HomePrimaryIssue.ShizukuNotAuthorized,
    HomePrimaryIssue.KeepAliveDisabled -> R.string.home_status_needs_attention
}

private fun HomeRuntimeStatus.descriptionRes(): Int = when (primaryIssue) {
    HomePrimaryIssue.None -> R.string.home_status_running_desc
    HomePrimaryIssue.AccessibilityDisabled -> R.string.home_status_accessibility_desc
    HomePrimaryIssue.GestureDisabled -> R.string.home_status_gesture_desc
    HomePrimaryIssue.ShizukuNotInstalled -> R.string.home_status_shizuku_not_installed_desc
    HomePrimaryIssue.ShizukuNotRunning -> R.string.home_status_shizuku_not_running_desc
    HomePrimaryIssue.ShizukuNotAuthorized -> R.string.home_status_shizuku_not_authorized_desc
    HomePrimaryIssue.KeepAliveDisabled -> R.string.home_status_keep_alive_desc
}

private fun HomeStatusItem.labelRes(): Int = when (type) {
    HomeStatusItemType.Accessibility -> if (available) R.string.status_accessibility_enabled else R.string.status_accessibility_disabled
    HomeStatusItemType.Gesture -> if (available) R.string.status_gesture_enabled else R.string.status_gesture_disabled
    HomeStatusItemType.KeepAlive -> if (available) R.string.status_keep_alive_enabled else R.string.status_keep_alive_disabled
    HomeStatusItemType.Shizuku -> when (shizuku) {
        HomeShizukuUiStatus.NotInstalled -> R.string.status_shizuku_not_installed
        HomeShizukuUiStatus.NotRunning -> R.string.status_shizuku_not_running
        HomeShizukuUiStatus.NotAuthorized -> R.string.status_shizuku_not_authorized
        HomeShizukuUiStatus.Authorized -> R.string.status_shizuku_authorized
        null -> R.string.status_shizuku_not_installed
    }
}

private fun HomeRuntimeAction.labelRes(): Int = when (this) {
    HomeRuntimeAction.None -> R.string.done
    HomeRuntimeAction.OpenAccessibility -> R.string.open_accessibility
    HomeRuntimeAction.EnableGesture -> R.string.enable_gesture
    HomeRuntimeAction.RequestShizukuPermission -> R.string.request_shizuku_permission
    HomeRuntimeAction.RefreshStatus -> R.string.refresh_status
    HomeRuntimeAction.EnableKeepAlive -> R.string.enable_keep_alive
}
