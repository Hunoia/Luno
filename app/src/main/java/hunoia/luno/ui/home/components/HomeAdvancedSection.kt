package hunoia.luno.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.ui.component.settings.CompactSettingsGroup
import hunoia.luno.ui.theme.HomeWideBreakpoint
import hunoia.luno.ui.theme.Spacing12

@Composable
fun HomeAdvancedSection(
    uiState: UiState,
    onExcludeClick: () -> Unit,
    onFrozenClick: () -> Unit,
    onFreezeClick: () -> Unit,
    onUnfreezeClick: () -> Unit,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onResetToggle: () -> Unit,
    showResetConfirm: Boolean,
    onResetConfirm: () -> Unit,
    onResetDismiss: () -> Unit,
    onCardAreaPosition: (Int) -> Unit,
) {
    CompactSettingsGroup(title = stringResource(id = R.string.advanced_settings)) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val useTwoColumns = maxWidth >= HomeWideBreakpoint
            if (useTwoColumns) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing12),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing12),
                    ) {
                        HomeFrozenCard(uiState, onFrozenClick, onFreezeClick, onUnfreezeClick)
                        HomeExcludeCard(onExcludeClick)
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing12),
                    ) {
                        HomeToolsCard(
                            onBackupClick,
                            onRestoreClick,
                            onResetToggle,
                            showResetConfirm,
                            onResetConfirm,
                            onResetDismiss,
                            onCardAreaPosition,
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing12),
                ) {
                    HomeFrozenCard(uiState, onFrozenClick, onFreezeClick, onUnfreezeClick)
                    HomeExcludeCard(onExcludeClick)
                    HomeToolsCard(
                        onBackupClick,
                        onRestoreClick,
                        onResetToggle,
                        showResetConfirm,
                        onResetConfirm,
                        onResetDismiss,
                        onCardAreaPosition,
                    )
                }
            }
        }
    }
}
