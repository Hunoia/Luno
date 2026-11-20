package hunoia.luno.gridpanel.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import hunoia.luno.gridpanel.GridPanelDragGhost
import hunoia.luno.ui.overlay.EdgeBarEdge
import hunoia.luno.ui.theme.containerShape
import kotlin.math.roundToInt

/**
 * 面板根层的拖拽幽灵：**中心**对准手指、不被格子裁剪。不吃触摸（没有 pointerInput），
 * 事件照常落到下面的列表/拖拽检测节点上。
 *
 * 剪贴板部件带动作条拖拽时（[GridPanelDragGhost.dragActive]），卡片沿动作条法线方向
 * 让位（见 [GridPanelDragGhost.dragBar]），避免盖住要松手的那一格。
 */
@Composable
fun GridPanelDragGhostLayer(ghost: GridPanelDragGhost, modifier: Modifier = Modifier) {
    if (!ghost.active) return
    // 幽灵宽度随文本变化，中心对齐要等它量出来才知道；量到之前按左上角跟手，不会跳。
    var size by remember { mutableStateOf(IntSize.Zero) }
    val gapPx = with(LocalDensity.current) { 8.dp.toPx() }
    Box(modifier.fillMaxSize()) {
        // 带动作条：卡片中心让开条体（法线方向推到屏内那侧），露出整条动作条。
        val bar = if (ghost.dragActive) ghost.dragBar else null
        var left = ghost.position.x - size.width / 2f
        var top = ghost.position.y - size.height / 2f
        bar?.let {
            when (it.edge) {
                EdgeBarEdge.LEFT -> left = maxOf(left, it.bounds.right + gapPx)
                EdgeBarEdge.RIGHT -> left = minOf(left, it.bounds.left - gapPx - size.width)
                EdgeBarEdge.TOP -> top = maxOf(top, it.bounds.bottom + gapPx)
                EdgeBarEdge.BOTTOM -> top = minOf(top, it.bounds.top - gapPx - size.height)
            }
        }
        Surface(
            modifier = Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .widthIn(max = 240.dp)
                .alpha(0.94f)
                .onSizeChanged { size = it },
            shape = containerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            // 跟手幽灵不加阴影：它贴着卡片裁形与窗口边界移动，阴影边缘必被切；而且
            // 逐帧重绘阴影要额外离屏层。层级用描边 + 底色差（ui-guidelines §4.6）。
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            shadowElevation = 0.dp,
        ) {
            Text(
                text = ghost.text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
