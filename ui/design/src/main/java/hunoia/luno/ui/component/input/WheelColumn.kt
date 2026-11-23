package hunoia.luno.ui.component.input

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollField
import androidx.compose.material3.ScrollFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberScrollFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hunoia.luno.ui.theme.SubMinInteractiveSize

/** 滚轮里的两位数字（时 / 分）：纯数字格式，没有本地化差异，不需要字符串资源。 */
fun wheelNumber(value: Int): String = value.toString().padStart(2, '0')

/** 滚轮露出多少行（奇数，选中行居中）。 */
private const val WheelVisibleRows = 3

/** 行内文字的上下留白：行高 = 一行文字高 + 两侧留白，仍受 [SubMinInteractiveSize] 兜底。 */
private val WheelRowPaddingVertical = 6.dp

/**
 * 纵向滚动选择器单列（滚轮）。
 *
 * 实现换成 M3 1.5 的 `ScrollField`：滚动吸附、选中语义与 TalkBack / 键盘支持由 M3 管，
 * 视觉（字号、颜色、高度）仍走 Luno token。公开 API 不变，调用点无需改。
 *
 * 交互契约：
 * - 只有**停稳后**居中的那一项是选中项，[onSelected] 在选中项变化时回调（含首帧的初始项）。
 * - [initialIndex] 只在首次组合生效；后续外部状态变化不反向驱动滚动，避免
 *   「回调 → 父级重组 → 再滚回去」的自激环。需要重置就换 `key` 重建。
 * - 高度由 [textStyle] 的行高（大字体下随 `fontScale` 长高）推出，不写死 dp：
 *   固定高度容器里放会随字体长高的文字，正是 ui-guidelines §1.5 禁止的那类裁切。
 *   `ScrollField` 默认高度是固定值，这里显式传入按字体算出的高度覆盖它。
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
    val state = rememberScrollFieldState(
        itemCount = values.size,
        index = initialIndex.coerceIn(0, values.lastIndex),
    )
    val currentOnSelected by rememberUpdatedState(onSelected)
    val selectedIndex by remember(state) { derivedStateOf { state.selectedOption } }
    LaunchedEffect(selectedIndex) { currentOnSelected(selectedIndex) }

    ScrollField(
        state = state,
        contentDescription = null,
        modifier = modifier.height(wheelHeight),
        // ScrollField 默认会画一层浅色容器背景（containerColor），滚轮是嵌在弹窗 / 卡片自己的
        // 底上的，这里显式置透明，否则滚轮四周多出一块白底。
        colors = ScrollFieldDefaults.colors(containerColor = Color.Transparent),
        field = { index, selected, _ ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = label(values[index]),
                    style = textStyle,
                    color = if (selected) scheme.primary else scheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                )
            }
        },
    )
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
