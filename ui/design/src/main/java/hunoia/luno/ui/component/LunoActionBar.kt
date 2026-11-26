package hunoia.luno.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import hunoia.luno.ui.theme.BottomBarHeight
import hunoia.luno.ui.theme.BottomBarPadding
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.TileInnerPadding
import hunoia.luno.ui.theme.glassCapsuleBackground

/**
 * 页面底部**动作底栏**：一颗靠右收窄的玻璃胶囊装页面的主操作与次要操作
 * （保存 / 新建 / 试播 / 测试 / 编辑）。
 *
 * 替代右下角 FAB：FAB 是悬浮的一个点，胶囊是「一条可放多动作的底栏」，且高度由
 * [hunoia.luno.ui.component.page.LunoPage] 实测回传给内容层，页面不再写固定留白常量。
 * 几何与冻结管理页的底栏同源
 * （`glassCapsuleBackground` + `TileInnerPadding` + `BottomBarHeight`）。
 *
 * 约定：
 * - 主操作放最右（拇指热区），次要操作依次向左，一次最多三个；
 * - **破坏性操作不进底栏**（删除 / 清空），留在顶栏或 `⋮` 菜单，避开误触；
 * - 键盘弹出时整条上移（IME ∪ 导航栏 inset 自己承担），没有可用动作也常显，
 *   避免内容底部留白随可用动作跳变。
 */
@Composable
fun LunoActionBar(
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .swallowTouches()
            .windowInsetsPadding(
                WindowInsets.navigationBars
                    .only(WindowInsetsSides.Bottom)
                    .union(WindowInsets.ime.only(WindowInsetsSides.Bottom)),
            )
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(start = PageGutter, end = PageGutter, bottom = BottomBarPadding),
        horizontalAlignment = Alignment.End,
    ) {
        // 玻璃作装饰背景层、内容层叠在上面：胶囊内动作的中文名 / 长按气泡可以溢出，
        // 不会被玻璃层的裁切切掉（同冻结管理页的字母条）。
        Box {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .glassCapsuleBackground(hazeState),
            )
            Row(
                modifier = Modifier
                    .padding(TileInnerPadding)
                    .height(BottomBarHeight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ActionBarItemGap),
                content = content,
            )
        }
    }
}

/** 底栏胶囊内的一个动作：40dp 圆形图标钮，容器色默认 `primaryContainer`。 */
@Composable
fun LunoActionBarItem(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color? = null,
) {
    PressIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor ?: contentColorFor(containerColor),
        ),
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

private val ActionBarItemGap = 4.dp
