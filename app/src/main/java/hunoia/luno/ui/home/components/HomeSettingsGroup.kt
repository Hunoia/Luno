package hunoia.luno.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AcUnit
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.Checklist
import androidx.compose.material.icons.twotone.ContentPaste
import androidx.compose.material.icons.twotone.Link
import androidx.compose.material.icons.twotone.ShowChart
import androidx.compose.material.icons.twotone.SettingsBackupRestore
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSegmentedGroup
import hunoia.luno.ui.component.row.LunoSettingsRow

@Composable
fun HomeSettingsGroup(
    onHabitsClick: () -> Unit,
    onTodosClick: () -> Unit,
    onTrendsClick: () -> Unit,
    onClipboardClick: () -> Unit,
    onAppControlClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LunoGroup(
        title = stringResource(R.string.home_section_settings),
        modifier = modifier,
    ) {
        LunoSegmentedGroup {
            item { shape -> LunoSettingsRow(
            title = stringResource(hunoia.luno.gridpanel.R.string.habit_list_title),
            icon = Icons.TwoTone.CheckCircle,
            shape = shape,
            onClick = onHabitsClick,
            ) }
            item { shape -> LunoSettingsRow(
            title = stringResource(hunoia.luno.gridpanel.R.string.todo_list_title),
            icon = Icons.TwoTone.Checklist,
            shape = shape,
            onClick = onTodosClick,
            ) }
            item { shape -> LunoSettingsRow(
            title = stringResource(hunoia.luno.gridpanel.R.string.trend_list_title),
            icon = Icons.TwoTone.ShowChart,
            shape = shape,
            onClick = onTrendsClick,
            ) }
            item { shape -> LunoSettingsRow(
            title = stringResource(R.string.clipboard_settings_entry),
            icon = Icons.TwoTone.ContentPaste,
            shape = shape,
            onClick = onClipboardClick,
            ) }
            item { shape -> LunoSettingsRow(
            title = stringResource(R.string.app_control_settings_entry),
            icon = Icons.TwoTone.AcUnit,
            shape = shape,
            onClick = onAppControlClick,
            ) }
        }
    }
}

@Composable
fun DataTransferGroup(
    onBackupSettingsClick: () -> Unit,
    onActionSelectClick: () -> Unit,
    onLogSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LunoGroup(
        title = stringResource(R.string.home_section_data),
        modifier = modifier,
    ) {
        LunoSegmentedGroup {
            item { shape -> LunoSettingsRow(
            title = stringResource(R.string.backup_settings_entry),
            icon = Icons.TwoTone.SettingsBackupRestore,
            shape = shape,
            onClick = onBackupSettingsClick,
            ) }
            item { shape -> LunoSettingsRow(
            title = stringResource(hunoia.luno.actions.R.string.action_deep_links),
            icon = Icons.TwoTone.Link,
            shape = shape,
            onClick = onActionSelectClick,
            ) }
            item { shape -> LunoSettingsRow(
            title = stringResource(R.string.log_settings_title),
            icon = Icons.TwoTone.BugReport,
            shape = shape,
            onClick = onLogSettingsClick,
            ) }
        }
    }
}
