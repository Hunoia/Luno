package hunoia.luno.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AcUnit
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material.icons.twotone.ContentPaste
import androidx.compose.material.icons.twotone.Link
import androidx.compose.material.icons.twotone.SettingsBackupRestore
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.ui.component.row.group

@Composable
fun HomeSettingsGroup(
    onClipboardClick: () -> Unit,
    onAppControlClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    group(
        title = stringResource(R.string.home_section_settings),
        modifier = modifier,
    ) {
        row(
            title = stringResource(R.string.clipboard_settings_entry),
            icon = Icons.TwoTone.ContentPaste,
            onClick = onClipboardClick,
        )
        row(
            title = stringResource(R.string.app_control_settings_entry),
            icon = Icons.TwoTone.AcUnit,
            onClick = onAppControlClick,
        )
    }
}

@Composable
fun DataTransferGroup(
    onBackupSettingsClick: () -> Unit,
    onActionSelectClick: () -> Unit,
    onLogSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    group(
        title = stringResource(R.string.home_section_data),
        modifier = modifier,
    ) {
        row(
            title = stringResource(R.string.backup_settings_entry),
            icon = Icons.TwoTone.SettingsBackupRestore,
            onClick = onBackupSettingsClick,
        )
        row(
            title = stringResource(hunoia.luno.actions.R.string.action_deep_links),
            icon = Icons.TwoTone.Link,
            onClick = onActionSelectClick,
        )
        row(
            title = stringResource(R.string.log_settings_title),
            icon = Icons.TwoTone.BugReport,
            onClick = onLogSettingsClick,
        )
    }
}
