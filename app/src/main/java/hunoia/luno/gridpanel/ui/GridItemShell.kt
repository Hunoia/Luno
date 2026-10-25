package hunoia.luno.gridpanel.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.gridpanel.geometry.ItemCorner

/**
 * 编辑态的删除角标（右上）。是项的子层且排在最后：子层先拿到指针事件，
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
 * 尺寸手柄（右下）：与卡片圆角**同心**的两条弧，压在那道 14dp 圆角上。
 *
 * 半径固定取 [ItemCorner]（卡片圆角本身是常量）：以前用 `min(20dp, 边长的 30%)`
 * 做半径、圆心又落在手柄盒左上角，小格上弧心离角 20dp、曲率也不一样，
 * 于是弧线浮在卡内、和边框之间留一道缝。改成同心后任何尺寸的项都天然贴合。
 * 纯视觉件、不挂点击：命中区由父级 gridItemGestures 统一判定（手柄 > 长按），
 * 避免子层点击与父层拖拽抢同一根手指。
 */
@Composable
fun ItemResizeHandle(
    modifier: Modifier = Modifier,
    /** 正在拖拽这一项：提亮 + 加粗，给一个「已经抓住了」的反馈。 */
    active: Boolean = false,
) {
    val primary = MaterialTheme.colorScheme.primary
    val color by animateColorAsState(
        targetValue = if (active) primary else gridPanelOnCardColor.copy(alpha = 0.6f),
        animationSpec = tween(120),
        label = "itemResizeHandleTint",
    )
    val strokeDp by animateFloatAsState(
        targetValue = if (active) 3f else 2.5f,
        animationSpec = tween(120),
        label = "itemResizeHandleStroke",
    )
    val label = stringResource(R.string.grid_panel_item_resize)
    Box(
        modifier
            // 固定尺寸：半径是常量，手柄再跟着格子缩放只会让弧心漂移。
            .size(ItemCorner + 6.dp)
            .semantics { contentDescription = label },
    ) {
        // 只裁到这块盒子内的「卡片圆角」形状：弧线外侧的半条描边不会跑出卡外。
        Canvas(
            Modifier
                .fillMaxSize()
                .clip(
                    RoundedCornerShape(
                        topStart = 0.dp,
                        topEnd = 0.dp,
                        bottomEnd = ItemCorner,
                        bottomStart = 0.dp,
                    ),
                ),
        ) {
            val r = ItemCorner.toPx()
            val stroke = strokeDp.dp.toPx()
            // 圆心 = 卡片那道圆角的圆心（手柄盒贴 BottomEnd，所以是盒内右下 r 处）。
            val center = Offset(size.width - r, size.height - r)
            val arcRadius = (r - stroke / 2f).coerceAtLeast(1f)

            // 主弧：与卡片边框共形。
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = center - Offset(arcRadius, arcRadius),
                size = Size(arcRadius * 2f, arcRadius * 2f),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            // 内弧：同心、短一截，让这个角读作「抓手」而不是「边框高亮」。
            val innerRadius = r - 7.dp.toPx()
            if (innerRadius > 2.dp.toPx()) {
                drawArc(
                    color = color.copy(alpha = if (active) 0.7f else 0.45f),
                    startAngle = 8f,
                    sweepAngle = 74f,
                    useCenter = false,
                    topLeft = center - Offset(innerRadius, innerRadius),
                    size = Size(innerRadius * 2f, innerRadius * 2f),
                    style = Stroke(width = stroke * 0.6f, cap = StrokeCap.Round),
                )
            }
        }
    }
}
