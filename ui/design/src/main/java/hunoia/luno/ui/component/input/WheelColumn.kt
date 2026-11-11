package hunoia.luno.ui.component.input

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hunoia.luno.ui.component.pressClickable
import hunoia.luno.ui.theme.MediumShape
import hunoia.luno.ui.theme.SubMinInteractiveSize
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/** 滚轮里的两位数字（时 / 分）：纯数字格式，没有本地化差异，不需要字符串资源。 */
fun wheelNumber(value: Int): String = value.toString().padStart(2, '0')

/** 滚轮露出多少行（奇数，选中行居中）。 */
private const val WheelVisibleRows = 3

/** 行内文字的上下留白：行高 = 一行文字高 + 两侧留白，仍受 [SubMinInteractiveSize] 兜底。 */
private val WheelRowPaddingVertical = 6.dp

/** 离中心每远一行衰减多少不透明度；再远也不低于 [WheelMinAlpha]。 */
private const val WheelFadePerRow = 0.35f
private const val WheelMinAlpha = 0.25f

/** 边缘行绕 X 轴倾斜的角度（度）：给出「转到背面」的滚轮观感，不是真圆柱投影。 */
private const val WheelRowTiltDegrees = 10f

/** 透视距离倍数：不设会退化成无透视的平面缩放。 */
private const val WheelCameraDistance = 18f

/**
 * 纵向滚动选择器单列（滚轮），ui-guidelines 下的自绘实现：
 * `LazyColumn` + 居中吸附，不用第三方 picker，行高 / 颜色 / 字号全部走主题 token。
 *
 * 交互契约：
 * - 只有**停稳后**居中的那一项是选中项，[onSelected] 在中心项变化时回调（含首帧的初始项）。
 * - 点某一项 = 滚到该项（`pressClickable`，无 ripple）；拖动 = 滚动，父级 `verticalScroll`
 *   只在滚轮自身滚不动时才接手（嵌套滚动，互不抢手势）。
 * - [initialIndex] 只在首次组合生效；后续外部状态变化不反向驱动滚动，避免
 *   「回调 → 父级重组 → 再滚回去」的自激环。需要重置就换 `key` 重建。
 * - 行高由 [textStyle] 的行高（大字体下随 `fontScale` 长高）推出，不写死 dp：
 *   固定高度容器里放会随字体长高的文字，正是 ui-guidelines §1.5 禁止的那类裁切。
 */
@Composable
fun <T> WheelColumn(
    values: List<T>,
    initialIndex: Int,
    onSelected: (Int) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    visibleRows: Int = WheelVisibleRows,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    if (values.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val rowHeight = remember(density, textStyle) { density.wheelRowHeight(textStyle) }
    val wheelHeight = rowHeight * visibleRows
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = initialIndex.coerceIn(0, values.lastIndex),
    )
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val scope = rememberCoroutineScope()
    val currentOnSelected by rememberUpdatedState(onSelected)
    val centerIndex by remember(state) { derivedStateOf { state.centeredItemIndex() } }
    LaunchedEffect(centerIndex) { currentOnSelected(centerIndex) }

    Box(
        modifier = modifier.height(wheelHeight),
        contentAlignment = Alignment.Center,
    ) {
        // 选中带（选择窗口）：画在列表下面，不随滚动移动。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .clip(MediumShape)
                .background(scheme.surfaceContainerHighest),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            flingBehavior = fling,
            // 上下各留半个窗口，使首 / 尾项也能滚到中间。
            contentPadding = PaddingValues(vertical = (wheelHeight - rowHeight) / 2),
        ) {
            itemsIndexed(values, key = { index, _ -> index }) { index, value ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .wheelFalloff(state = state, index = index)
                        .pressClickable { scope.launch { state.animateScrollToItem(index) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(value),
                        style = textStyle,
                        color = if (index == centerIndex) scheme.primary else scheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * 离中心越远越淡、越倾斜（在绘制阶段读布局，滚动时不触发重组）。
 *
 * 行偏移与视口中心都取 `LazyListLayoutInfo` 的坐标（含 `contentPadding` 修正），
 * 与吸附行为使用同一个中心，不会出现「看着选中 A、上报 B」。
 */
private fun Modifier.wheelFalloff(state: LazyListState, index: Int): Modifier = graphicsLayer {
    val layout = state.layoutInfo
    val item = layout.visibleItemsInfo.firstOrNull { it.index == index } ?: return@graphicsLayer
    val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
    val rowsFromCenter = (item.offset + item.size / 2f - center) / item.size.coerceAtLeast(1)
    val distance = rowsFromCenter.absoluteValue.coerceAtMost(2f)
    alpha = (1f - distance * WheelFadePerRow).coerceIn(WheelMinAlpha, 1f)
    rotationX = -rowsFromCenter.coerceIn(-2f, 2f) * WheelRowTiltDegrees
    cameraDistance = WheelCameraDistance * density
}

/** 停在视口中心的那一项；布局尚未就绪时回落到首项。 */
private fun LazyListState.centeredItemIndex(): Int {
    val layout = layoutInfo
    if (layout.visibleItemsInfo.isEmpty()) return firstVisibleItemIndex
    val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
    return layout.visibleItemsInfo
        .minByOrNull { (it.offset + it.size / 2f - center).absoluteValue }
        ?.index
        ?: firstVisibleItemIndex
}

/**
 * 行高：一行文字的高度（行高缺失时按字号 ×1.6 估）+ 上下留白，且不低于 36dp 命中区。
 *
 * sp → dp 走 `Density`，已经包含 `fontScale`：大字体下滚轮整体长高，文字不被裁。
 */
private fun Density.wheelRowHeight(style: TextStyle): Dp {
    val lineHeight: TextUnit = when {
        style.lineHeight.type == TextUnitType.Sp -> style.lineHeight
        style.fontSize.type == TextUnitType.Sp -> style.fontSize * 1.6f
        else -> 16.sp
    }
    return (lineHeight.toDp() + WheelRowPaddingVertical * 2)
        .coerceAtLeast(SubMinInteractiveSize)
}
