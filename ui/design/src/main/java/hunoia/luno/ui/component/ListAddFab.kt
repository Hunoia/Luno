package hunoia.luno.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hunoia.luno.ui.theme.FabSize
import hunoia.luno.ui.theme.PageGutter

/**
 * 列表页右下角的新建 FAB：填满父 Box 定位、让开导航栏，尺寸 / 容器色 / elevation 固定。
 *
 * 「列表 + 新建」的页面共用这一份（`PanelListScreen`、习惯设置页），不再各抄一遍
 * 64dp + 四档 elevation 归零。列表的 `bottomContentPadding` 用 `FloatingContentBottom`
 * 让最后一行能滚到 FAB 上方（ui-guidelines §3.2 第 2 条）。
 *
 * [dropdown] 挂在同一锚点旁（新建类型菜单），由调用方提供内容。
 */
@Composable
fun ListAddFab(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    dropdown: @Composable () -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(end = PageGutter, bottom = PageGutter),
        ) {
            PressFloatingActionButton(
                onClick = onClick,
                modifier = Modifier.size(FabSize),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = contentDescription,
                )
            }
            dropdown()
        }
    }
}
