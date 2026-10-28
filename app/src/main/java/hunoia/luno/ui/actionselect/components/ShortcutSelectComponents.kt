package hunoia.luno.ui.actionselect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastForEachIndexed
import com.aaron.compose.ktx.onClick
import hunoia.luno.quicklaunch.model.icon
import hunoia.luno.ui.icon.icon as launcherIcon
import hunoia.luno.bridge.launcher.LauncherInfo
import hunoia.luno.ui.theme.SegmentedGap
import hunoia.luno.ui.component.row.LunoSelectableRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.row.selectedSegmentedShape
import hunoia.luno.ui.component.row.LunoSettingsRow
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LauncherInfoItem(
    isShortcutInfoSelected: (LauncherInfo.ShortcutInfo) -> Boolean,
    onClick: () -> Unit,
    onSelect: (LauncherInfo.ShortcutInfo, Boolean) -> Unit,
    launcherInfo: LauncherInfo,
    modifier: Modifier = Modifier,
) {
    val total = 1 + launcherInfo.shortcuts.size
    // launcher 行恒未选，占 flags[0]；否则 shortcut 的 index+1 会与 flags 对位错一行。
    val flags = listOf(false) + launcherInfo.shortcuts.map { isShortcutInfoSelected(it) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(SegmentedGap)) {
        LunoSettingsRow(
            title = launcherInfo.label,
            subtitle = launcherInfo.packageName,
            iconModel = launcherInfo.launcherIcon,
            onClick = onClick,
            shape = segmentedShape(0, total),
        )
        launcherInfo.shortcuts.fastForEachIndexed { index, shortcutInfo ->
            key(shortcutInfo) {
                LunoSelectableRow(
                    title = shortcutInfo.label,
                    selected = isShortcutInfoSelected(shortcutInfo),
                    onSelectedChange = { onSelect(shortcutInfo, it) },
                    iconModel = shortcutInfo.launcherIcon,
                    singleSelect = true,
                    shape = selectedSegmentedShape(index + 1, total, flags),
                )
            }
        }
    }
}