package hunoia.luno.ui.actionselect
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastForEachIndexed
import com.aaron.compose.ktx.onClick
import hunoia.luno.quicklaunch.model.icon
import hunoia.luno.quicklaunch.model.LauncherInfo
import hunoia.luno.ui.theme.SegmentedGap
import hunoia.luno.ui.component.row.LunoSelectableRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.row.LunoSettingsRow
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LauncherInfoItem(
    canLauncherInfoEnabled: (LauncherInfo) -> Boolean,
    canShortcutInfoEnabled: (LauncherInfo.ShortcutInfo) -> Boolean,
    isShortcutInfoSelected: (LauncherInfo.ShortcutInfo) -> Boolean,
    onClick: () -> Unit,
    onSelect: (LauncherInfo.ShortcutInfo, Boolean) -> Unit,
    launcherInfo: LauncherInfo,
    selectSingle: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val launcherEnabled = canLauncherInfoEnabled(launcherInfo)
    val total = 1 + launcherInfo.shortcuts.size
    val interactionSource = remember { MutableInteractionSource() }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(SegmentedGap)) {
        LunoSettingsRow(
            title = launcherInfo.label,
            subtitle = launcherInfo.packageName,
            iconModel = launcherInfo.icon,
            onClick = onClick.takeIf { launcherEnabled },
            enabled = launcherEnabled,
            shape = segmentedShape(0, total),
        )
        launcherInfo.shortcuts.fastForEachIndexed { index, shortcutInfo ->
            key(shortcutInfo) {
                LunoSelectableRow(
                    title = shortcutInfo.label,
                    selected = isShortcutInfoSelected(shortcutInfo),
                    onSelectedChange = { onSelect(shortcutInfo, it) },
                    enabled = canShortcutInfoEnabled(shortcutInfo),
                    iconModel = shortcutInfo.icon,
                    showCheckbox = !selectSingle,
                    shape = segmentedShape(index + 1, total),
                )
            }
        }
    }
}