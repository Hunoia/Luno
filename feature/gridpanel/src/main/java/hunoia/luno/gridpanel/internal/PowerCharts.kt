package hunoia.luno.gridpanel.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 耗电部件用的小图表原语。
 *
 * 全部是自绘基础布局（`Box` + 比例宽度），不引第三方图表库：
 *  - [PowerComponentChart]：整机组件横向条（屏幕 / CPU / 音频 …）。
 *  - [PowerSplitBar]：前台 / 前台服务 / 后台 / 缓存四段堆叠条。
 *  - [PowerRowBar]：列表行内的一条占比细条（应用排行就是它，不另起一张重复图）。
 *
 * 曲线本身复用趋势部件的 `TrendMiniChart`，这里不重复实现折线。
 */

/** 组件条形图的一行。 */
internal data class PowerComponentBar(
    val label: String,
    /** 0..1，相对最大项（让短条也看得见）。 */
    val fraction: Float,
    val valueText: String,
    val color: Color,
)

/** 整机组件横向条形图。 */
@Composable
internal fun PowerComponentChart(
    rows: List<PowerComponentBar>,
    onCard: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.label,
                    modifier = Modifier.width(58.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = onCard.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(onCard.copy(alpha = 0.10f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(row.fraction.coerceIn(0f, 1f))
                            .background(row.color),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = row.valueText,
                    modifier = Modifier.width(58.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = onCard,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 一次放电的前后台四段拆分（mAh）。 */
internal data class PowerSplit(
    val fgMah: Float = 0f,
    val fgsMah: Float = 0f,
    val bgMah: Float = 0f,
    val cachedMah: Float = 0f,
) {
    val total: Float get() = fgMah + fgsMah + bgMah + cachedMah
    val isEmpty: Boolean get() = total <= 0f
}

/** 四段堆叠条；[colors] 顺序与 fg / fgs / bg / cached 一致。 */
@Composable
internal fun PowerSplitBar(
    split: PowerSplit,
    colors: List<Color>,
    onCard: Color,
    modifier: Modifier = Modifier,
) {
    val values = listOf(split.fgMah, split.fgsMah, split.bgMah, split.cachedMah)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(onCard.copy(alpha = 0.08f)),
    ) {
        if (!split.isEmpty) {
            values.forEachIndexed { index, value ->
                if (value > 0f) {
                    Box(
                        Modifier
                            .weight(value)
                            .fillMaxHeight()
                            .background(colors.getOrElse(index) { onCard }),
                    )
                }
            }
        }
    }
}

/** 列表行内的一条占比细条（0..1）。 */
@Composable
internal fun PowerRowBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(color),
        )
    }
}
