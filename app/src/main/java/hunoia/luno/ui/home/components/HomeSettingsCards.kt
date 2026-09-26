package hunoia.luno.ui.home

import hunoia.luno.ui.theme.*
import hunoia.luno.R
import hunoia.luno.ui.component.ExpressiveCard
import hunoia.luno.ui.component.settings.CompactSettingsRow
import androidx.compose.ui.unit.dp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt

@Composable
fun HomeExcludeCard(onClick: () -> Unit) {
    CompactSettingsRow(
        title = stringResource(id = R.string.exclude_app_short),
        icon = Icons.Default.Block,
        onClick = onClick,
    )
}

@Composable
fun HomePointerCard(onClick: () -> Unit) {
    CompactSettingsRow(
        title = stringResource(id = R.string.pointer),
        icon = Icons.Default.TouchApp,
        onClick = onClick,
    )
}

@Composable
fun HomeActionSettingsCard(onClick: () -> Unit) {
    CompactSettingsRow(
        title = stringResource(id = R.string.action_settings),
        icon = Icons.Default.Tune,
        onClick = onClick,
    )
}

@Composable
fun HomeFrozenCard(
    uiState: UiState,
    onClick: () -> Unit,
    onFreezeClick: () -> Unit,
    onUnfreezeClick: () -> Unit,
) {
    ExpressiveCard(
        title = stringResource(id = R.string.frozen_app_manage_short) + " (${uiState.selectedFrozenAppCount}/${uiState.frozenAppCount})",
        subtitle = "",
        icon = Icons.Default.AcUnit,
        onClick = onClick,
        accent = MaterialTheme.colorScheme.tertiaryContainer,
        onAccent = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onFreezeClick, modifier = Modifier.weight(1f)) {
                Text(stringResource(id = R.string.freeze_action))
            }
            FilledTonalButton(onClick = onUnfreezeClick, modifier = Modifier.weight(1f)) {
                Text(stringResource(id = R.string.unfreeze_action))
            }
        }
    }
}

@Composable
fun HomeToolsCard(
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onResetToggle: () -> Unit,
    showResetConfirm: Boolean,
    onResetConfirm: () -> Unit,
    onResetDismiss: () -> Unit,
    onCardAreaPosition: (Int) -> Unit,
) {
    ExpressiveCard(
        title = stringResource(id = R.string.backup_restore),
        subtitle = stringResource(id = R.string.backup_restore_default_hint),
        icon = Icons.Default.Build,
        onClick = {},
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onBackupClick, modifier = Modifier.weight(1f)) {
                Text(stringResource(id = R.string.backup))
            }
            FilledTonalButton(onClick = onRestoreClick, modifier = Modifier.weight(1f)) {
                Text(stringResource(id = R.string.restore))
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onResetToggle, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(id = R.string.default_action))
        }
        AnimatedVisibility(
            visible = showResetConfirm,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        onCardAreaPosition(coords.positionInWindow().y.roundToInt())
                    }
                    .padding(top = 12.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(id = R.string.reset_default_settings_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilledTonalButton(
                            onClick = onResetDismiss,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(id = R.string.cancel))
                        }
                        FilledTonalButton(
                            onClick = onResetConfirm,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(id = R.string.confirm_reset))
                        }
                    }
                }
            }
        }
    }
}
