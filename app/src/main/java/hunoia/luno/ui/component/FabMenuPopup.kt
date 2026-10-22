package hunoia.luno.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * 全屏透明层 + 右下角菜单面板：点菜单外任意处/返回键关闭（onDismiss）。
 * 菜单限高可滚，M3 menu 观感（surfaceContainer + 小圆角 + 阴影）。
 * 不用 DropdownMenu：其锚定在右下角会被裁成贴左；普通 Popup 又没有点外关闭。
 */
@Composable
fun FabMenuPopup(
    onDismiss: () -> Unit,
    bottomPadding: Dp,
    maxHeight: Dp = 360.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        // 全屏拦截层：吃掉菜单外的一切点击（点击 = 关闭）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            // 导航栏 inset：与 FAB（自身做了 insets padding）同基准对齐，三键导航下不错位。
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .padding(end = 16.dp, bottom = bottomPadding)
                        .widthIn(min = 168.dp)
                        .heightIn(max = maxHeight),
                ) {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        content = content,
                    )
                }
            }
        }
    }
}

