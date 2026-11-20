package hunoia.luno.ui.component.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import hunoia.luno.ui.overlay.EdgeBar
import hunoia.luno.ui.overlay.EdgeBarEdge
import hunoia.luno.ui.overlay.EdgeBarLayout
import hunoia.luno.ui.theme.CapsuleShape
import kotlin.math.roundToInt

/**
 * 动作条一格：图标 + 文案（文案同时作无障碍描述与浮标）；[id] 由调用方映射回自己的动作。
 */
data class EdgeBarSlot(
    val icon: ImageVector,
    val label: String,
    val id: String = "",
)

/**
 * 边缘动作条：贴边一条胶囊，每格一个圆形图标；指到哪一格哪一格放大，
 * 并在条内侧浮出该格的名字，松手即执行。
 *
 * 只读渲染，不吃触摸 —— 手势在调用方的拖拽节点上，命中判定用同一份
 * [EdgeBarLayout.hit]。识屏工具条与剪贴板部件拖拽共用这一份外观
 * （ui-guidelines §1.1 单一实现），两边只提供 [slots]。
 *
 * [screenWidthPx] / [screenHeightPx] 供浮标贴边夹取；传 0 表示不夹取。
 */
@Composable
fun EdgeActionBar(
    bar: EdgeBar,
    slots: List<EdgeBarSlot>,
    activeSlot: Int,
    modifier: Modifier = Modifier,
    screenWidthPx: Float = 0f,
    screenHeightPx: Float = 0f,
) {
    if (slots.isEmpty()) return
    val depth = (EdgeBarLayout.SlotDp + EdgeBarLayout.PaddingDp * 2).dp
    Box(modifier = modifier.fillMaxSize()) {
        val icons: @Composable () -> Unit = {
            slots.forEachIndexed { index, slot ->
                EdgeBarIcon(slot = slot, active = index == activeSlot)
            }
        }
        Box(
            modifier = Modifier
                .offsetPx(bar.bounds.left, bar.bounds.top)
                .let { base -> if (bar.isVertical) base.width(depth) else base.height(depth) }
                .background(
                    MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
                    CapsuleShape,
                )
                .padding(EdgeBarLayout.PaddingDp.dp),
        ) {
            if (bar.isVertical) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(EdgeBarLayout.GapDp.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { icons() }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(EdgeBarLayout.GapDp.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { icons() }
            }
        }
        slots.getOrNull(activeSlot)?.let { slot ->
            EdgeBarLabel(
                bar = bar,
                text = slot.label,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
            )
        }
    }
}

@Composable
private fun EdgeBarIcon(slot: EdgeBarSlot, active: Boolean) {
    val tint = if (active) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.inverseOnSurface
    }
    Box(
        modifier = Modifier
            .size(EdgeBarLayout.SlotDp.dp)
            .scale(if (active) 1.1f else 1f)
            .background(
                if (active) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                } else {
                    Color.White.copy(alpha = 0.10f)
                },
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = slot.icon,
            contentDescription = slot.label,
            tint = tint,
            modifier = Modifier.size(EdgeBarLayout.IconDp.dp),
        )
    }
}

/** 当前格的名字：浮在动作条内侧（屏内那侧），不遮挡手指。 */
@Composable
private fun EdgeBarLabel(
    bar: EdgeBar,
    text: String,
    screenWidthPx: Float,
    screenHeightPx: Float,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    // 浮标与条体的缝（px）：条体几何是屏幕像素，这里不能再拿 dp 当 px 用。
    val gapPx = with(LocalDensity.current) { 8.dp.toPx() }
    val chipWidth = size.width.toFloat()
    val chipHeight = size.height.toFloat()
    val centeredX = bar.bounds.left + (bar.bounds.width - chipWidth) / 2f
    val centeredY = bar.bounds.top + (bar.bounds.height - chipHeight) / 2f
    val (x, y) = when (bar.edge) {
        EdgeBarEdge.LEFT -> (bar.bounds.right + gapPx) to centeredY
        EdgeBarEdge.RIGHT -> (bar.bounds.left - gapPx - chipWidth) to centeredY
        EdgeBarEdge.TOP -> centeredX to (bar.bounds.bottom + gapPx)
        EdgeBarEdge.BOTTOM -> centeredX to (bar.bounds.top - gapPx - chipHeight)
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offsetPx(
                    x.coerceIn(0f, (screenWidthPx - chipWidth).coerceAtLeast(0f)),
                    y.coerceIn(0f, (screenHeightPx - chipHeight).coerceAtLeast(0f)),
                )
                .onSizeChanged { size = it }
                .background(
                    MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f),
                    CapsuleShape,
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 绝对定位（px）：动作条按屏幕像素算几何，这里只做一次换算。 */
private fun Modifier.offsetPx(x: Float, y: Float): Modifier =
    this.then(Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) })
