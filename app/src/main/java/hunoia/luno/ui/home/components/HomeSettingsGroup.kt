package hunoia.luno.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.FilterAlt
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material.icons.twotone.Restore
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.ui.component.SegmentedGroup
import hunoia.luno.ui.component.SegmentedSettingsRow
import hunoia.luno.ui.component.segmentedShape

@Composable
fun HomeSettingsGroup(
    onActionSettingsClick: () -> Unit,
    onConditionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedGroup(
        title = stringResource(R.string.home_section_settings),
        modifier = modifier,
    ) {
        SegmentedSettingsRow(
            title = stringResource(R.string.action_settings),
            icon = Icons.TwoTone.Tune,
            shape = segmentedShape(0, 2),
            onClick = onActionSettingsClick,
        )
        SegmentedSettingsRow(
            title = stringResource(R.string.condition_home),
            icon = Icons.TwoTone.FilterAlt,
            shape = segmentedShape(1, 2),
            onClick = onConditionClick,
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
    SegmentedGroup(
        title = stringResource(R.string.home_section_data),
        modifier = modifier,
    ) {
        SegmentedSettingsRow(
            title = stringResource(R.string.backup),
            icon = Icons.TwoTone.Save,
            shape = segmentedShape(0, 3),
            onClick = onBackupClick,
        )
        SegmentedSettingsRow(
            title = stringResource(R.string.restore),
            icon = Icons.TwoTone.Restore,
            shape = segmentedShape(1, 3),
            onClick = onRestoreClick,
        )
        SegmentedSettingsRow(
            title = stringResource(R.string.reset),
            icon = Icons.TwoTone.RestartAlt,
            shape = segmentedShape(2, 3),
            onClick = onResetClick,
        )
    }
}


