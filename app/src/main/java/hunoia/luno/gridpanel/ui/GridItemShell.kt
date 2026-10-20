package hunoia.luno.gridpanel.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import hunoia.luno.R

/**
 * 编辑态的删除角标（右上）。是项的子层，排在最后 —— 子层先拿到指针事件，
 * 点角标不会误起整项拖拽。
 */
@Composable
fun ItemDeleteBadge(onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Default.Close,
        contentDescription = stringResource(R.string.grid_panel_item_menu_delete),
        tint = MaterialTheme.colorScheme.error,
        modifier = modifier
            .padding(2.dp)
            .size(20.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
            .clickable { onDelete() },
    )
}

/**
 * 尺寸手柄（右下）：四分之一圆弧，直边贴着项的右边/下边。
 *
 * 纯视觉件，**不挂点击**：命中区由父级的 gridItemGestures 统一判定
 * （手柄 > 长按），避免「子层点击 + 父层拖拽」两套手势抢同一根手指。
 * 视觉 26dp、命中 40dp —— 手感大、存在感小。
 */
@Composable
fun ItemResizeHandle(
    modifier: Modifier = Modifier,
    handleSize: androidx.compose.ui.unit.Dp = 26.dp,
) {
    val color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val label = stringResource(R.string.grid_panel_item_resize)
    Box(
        modifier
            .size(handleSize)
            .semantics { contentDescription = label },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val s = minOf(size.width, size.height)
            // 圆心在盒子**左上**（项内 s×s 处），半径 s：弧从 (s, 0) 到 (0, s)，
            // 向项的右下角**外**鼓 —— 和圆角边框同向，不再是向内凹的勺形。
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(-s, -s),
                size = androidx.compose.ui.geometry.Size(s * 2f, s * 2f),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}
