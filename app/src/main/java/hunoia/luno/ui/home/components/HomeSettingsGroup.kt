package hunoia.luno.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material.icons.twotone.Restore
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape

@Composable
fun HomeSettingsGroup(
    onActionSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LunoGroup(
        title = stringResource(R.string.home_section_settings),
        modifier = modifier,
    ) {
        LunoSettingsRow(
            title = stringResource(R.string.action_settings),
            icon = Icons.TwoTone.Tune,
            shape = segmentedShape(0, 1),
            onClick = onActionSettingsClick,
        )
    }
}

@Composable
fun DataTransferGroup(
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onResetClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LunoGroup(
        title = stringResource(R.string.home_section_data),
        modifier = modifier,
    ) {
        LunoSettingsRow(
            title = stringResource(R.string.backup),
            icon = Icons.TwoTone.Save,
            shape = segmentedShape(0, 3),
            onClick = onBackupClick,
        )
        LunoSettingsRow(
            title = stringResource(R.string.restore),
            icon = Icons.TwoTone.Restore,
            shape = segmentedShape(1, 3),
            onClick = onRestoreClick,
        )
        LunoSettingsRow(
            title = stringResource(R.string.reset),
            icon = Icons.TwoTone.RestartAlt,
            shape = segmentedShape(2, 3),
            onClick = onResetClick,
        )
    }
}
