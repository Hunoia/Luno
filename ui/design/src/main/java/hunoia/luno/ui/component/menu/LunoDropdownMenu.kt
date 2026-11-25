package hunoia.luno.ui.component.menu

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import hunoia.luno.ui.theme.MenuShape
import hunoia.luno.ui.theme.ElevationSmall
import hunoia.luno.ui.theme.MenuMaxHeight

/**
 * 下拉 / 长按菜单的唯一实现（ui-guidelines §4.4）：视觉与尺寸在此定死，调用方只写内容。
 *
 * - 视觉：`surfaceContainerHigh` 底 + `MenuShape` 圆角 + `ElevationSmall`（只改底色，不画
 *   阴影——弹出菜单窗口尺寸 == 内容尺寸，阴影会被窗口边界裁掉，见 ui-guidelines §4.6）。
 * - 高度：上限 [MenuMaxHeight]，内容超过时菜单内滚动（M3 `DropdownMenu` 内置滚动，
 *   本组件只收紧高度上限）。
 * - 弹层：默认 `PopupProperties(focusable = true)`；需要弹出时不抢输入框焦点（键盘保持
 *   打开）时传 `PopupProperties(focusable = false)`。
 *
 * 菜单项用 [LunoDropdownMenuItem]；调用方负责展开态的开启与 [onDismissRequest]。
 */
@Composable
fun LunoDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        // 收紧到 Luno 上限：M3 内置 heightIn(max) 按屏幕高推导（常见 ≥504dp），
        // 取交集后本值生效，M3 内置 verticalScroll 负责超限滚动。
        modifier = modifier.heightIn(max = MenuMaxHeight),
        offset = offset,
        scrollState = scrollState,
        properties = properties,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MenuShape,
        tonalElevation = ElevationSmall,
        shadowElevation = 0.dp,
        content = content,
    )
}
