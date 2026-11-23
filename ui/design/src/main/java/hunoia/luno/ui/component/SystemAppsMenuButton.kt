package hunoia.luno.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import hunoia.luno.design.R
import hunoia.luno.ui.component.menu.LunoDropdownMenu
import hunoia.luno.ui.component.menu.LunoDropdownMenuItem

/**
 * 应用列表顶栏的「系统应用显示」开关：一个 Android 图标，点开 Luno 下拉菜单，两项各为
 * 一类——无界面系统组件 / 有界面无入口系统应用，选中态用高亮块表达（不放 Switch）。
 * 图标点亮 = 至少一类开启。
 *
 * 点击项不收起菜单，便于连续切换两类；两态都由宿主持有（A 类按列表内存态，不落盘）。
 */
@Composable
fun SystemAppsMenuButton(
    showUILess: Boolean,
    showNoEntry: Boolean,
    onShowUILessChange: (Boolean) -> Unit,
    onShowNoEntryChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val anyActive = showUILess || showNoEntry
    IconButton(onClick = { expanded = true }, modifier = modifier) {
        Icon(
            imageVector = Icons.Default.Android,
            contentDescription = stringResource(R.string.system_apps_menu_cd),
            tint = if (anyActive) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
    LunoDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        LunoDropdownMenuItem(
            text = stringResource(R.string.system_apps_show_uiless),
            selected = showUILess,
            onClick = { onShowUILessChange(!showUILess) },
        )
        LunoDropdownMenuItem(
            text = stringResource(R.string.system_apps_show_noentry),
            selected = showNoEntry,
            onClick = { onShowNoEntryChange(!showNoEntry) },
        )
    }
}
