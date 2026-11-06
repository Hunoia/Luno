package hunoia.luno.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Link
import androidx.compose.material.icons.twotone.RadioButtonChecked
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material.icons.twotone.Restore
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material.icons.twotone.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSegmentedGroup
import hunoia.luno.ui.component.row.LunoSettingsRow

@Composable
fun HomeSettingsGroup(
    onSearchEngineClick: () -> Unit,
    onFloatBallClick: () -> Unit,
    onLinkDispatchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LunoGroup(
        title = stringResource(R.string.home_section_settings),
        modifier = modifier,
    ) {
        LunoSegmentedGroup {
            item { shape -> LunoSettingsRow(
            title = stringResource(hunoia.luno.quickaccess.R.string.search_engine_settings),
            icon = Icons.TwoTone.Search,
            shape = shape,
            onClick = onSearchEngineClick,
            ) }
            item { shape -> LunoSettingsRow(
            title = stringResource(hunoia.luno.floatball.R.string.float_ball),
            icon = Icons.TwoTone.RadioButtonChecked,
            shape = shape,
            onClick = onFloatBallClick,
            ) }
            item { shape -> LunoSettingsRow(
            title = stringResource(hunoia.luno.link.R.string.link_dispatch),
            icon = Icons.TwoTone.Link,
            shape = shape,
            onClick = onLinkDispatchClick,
            ) }
        }
    }
}

/** 外部调用与系统授权已移到设置页底部。 */

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
        LunoSegmentedGroup {
            item { shape -> LunoSettingsRow(
            title = stringResource(R.string.backup),
            icon = Icons.TwoTone.Save,
            shape = shape,
            onClick = onBackupClick,
            ) }
            item { shape -> LunoSettingsRow(
            title = stringResource(R.string.restore),
            icon = Icons.TwoTone.Restore,
            shape = shape,
            onClick = onRestoreClick,
            ) }
            item { shape -> LunoSettingsRow(
            title = stringResource(hunoia.luno.design.R.string.reset),
            icon = Icons.TwoTone.RestartAlt,
            shape = shape,
            onClick = onResetClick,
            ) }
        }
    }
}
