package hunoia.luno.gridpanel.trend

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hunoia.luno.config.model.TrendEntry
import hunoia.luno.gridpanel.R

/** 一条折线：一个读数槽位的序列 + 它的颜色。 */
data class TrendSeries(val entries: List<TrendEntry>, val color: Color)

/**
 * 多系列折线色：第 0 条用指标本色，后面的依次压暗 + 降透明度。
 *
 * 只调明度与透明度，不换色相：同一指标的几个读数用同族颜色，读图时「谁是谁」靠图例 + 深浅，
 * 不靠红绿对照（红绿已经在涨跌语义里被占用）。
 */
fun trendSeriesColor(base: Color, index: Int, count: Int): Color {
    if (count <= 1) return base
    val step = 0.55f / (count - 1)
    val factor = (1f - step * index * 0.6f).coerceIn(0.35f, 1f)
    val alpha = (base.alpha * (1f - step * index * 0.5f)).coerceIn(0.5f, 1f)
    return Color(
        red = (base.red * factor).coerceIn(0f, 1f),
        green = (base.green * factor).coerceIn(0f, 1f),
        blue = (base.blue * factor).coerceIn(0f, 1f),
        alpha = alpha,
    )
}

/**
 * 趋势折线图（可多系列）。
 *
 * X 轴按真实时间线性映射（不是等距索引），所以记录稀疏的区间自然留白、密集区间自然变紧；
 * 视觉上的「短横轴」由 [range] 切换，长范围的性能由 [buildTrendChartLayout] 的像素桶降采样保证。
 * 所有点用一条折线连接：缺记录只代表没测量，不代表数值归零。
 *
 * 多系列时各条线共用同一套视窗与 Y 边界（见 [TrendChartLayout]），且**不画线下渐变填充**
 * ——两条以上的填充叠在一起会变成一团颜色，反而读不出各自的走势。
 */
@Composable
fun TrendSeriesChart(
    series: List<TrendSeries>,
    target: Double?,
    decimals: Int,
    unit: String,
    range: TrendRange,
    modifier: Modifier = Modifier,
) {
    val now = remember(series, range) { System.currentTimeMillis() }
    val measurer = rememberTextMeasurer()
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val emptyText = stringResource(R.string.trend_detail_no_entries)
    val targetLabel = stringResource(R.string.trend_chart_target, formatTrendValue(target ?: 0.0, decimals))
    BoxWithConstraints(modifier) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val layout = remember(series, target, range, widthPx, now) {
            buildTrendChartLayout(
                seriesEntries = series.map { it.entries },
                range = range,
                now = now,
                target = target,
                widthPx = widthPx,
            )
        }
        if (!layout.hasData) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurfaceVariant,
                )
            }
            return@BoxWithConstraints
        }
        val colors = series.map { it.color }
        val lineColor = colors.firstOrNull() ?: onSurfaceVariant
        val drawFill = series.size == 1
        Canvas(Modifier.fillMaxSize()) {
            val bottom = 20.dp.toPx()
            val plotHeight = (size.height - bottom).coerceAtLeast(24.dp.toPx())
            val plotWidth = size.width
            drawGrid(plotWidth, plotHeight, onSurfaceVariant)

            val targetY = layout.targetFractionY?.let { it * plotHeight }
            if (targetY != null) {
                drawLine(
                    color = lineColor.copy(alpha = 0.65f),
                    start = Offset(0f, targetY),
                    end = Offset(plotWidth, targetY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                )
                drawTargetLabel(targetLabel, targetY, plotWidth, measurer, onSurfaceVariant.copy(alpha = 0.9f))
            }

            layout.series.forEachIndexed { index, seriesData ->
                val color = colors.getOrElse(index) { lineColor }
                val offsets = seriesData.points.map {
                    Offset(it.fractionX * plotWidth, it.fractionY * plotHeight)
                }
                if (offsets.size < 2) return@forEachIndexed
                val linePath = Path().apply {
                    moveTo(offsets.first().x, offsets.first().y)
                    offsets.drop(1).forEach { lineTo(it.x, it.y) }
                }
                if (drawFill) {
                    // 线下淡渐变：让曲线在暗色 / 亮色背景下都有「数值体积」，不抢折线本体的可读性。
                    val fillPath = Path().apply {
                        addPath(linePath)
                        lineTo(offsets.last().x, plotHeight)
                        lineTo(offsets.first().x, plotHeight)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f)),
                            startY = 0f,
                            endY = plotHeight,
                        ),
                    )
                }
                drawPath(
                    path = linePath,
                    color = color,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
                // 点太多时只留首尾，避免变成一条毛边；短范围保留每个真实记录点。
                val dots = when {
                    offsets.size <= 80 -> offsets
                    else -> listOf(offsets.first(), offsets.last())
                }
                dots.forEach { center ->
                    drawCircle(color = color, radius = 3.dp.toPx(), center = center)
                    drawCircle(color = Color.White.copy(alpha = 0.85f), radius = 1.1.dp.toPx(), center = center)
                }
            }

            layout.axisTicks.forEach { tick ->
                val tickLayout = measurer.measure(tick.label, labelStyle(onSurfaceVariant))
                val x = (tick.fraction * plotWidth - tickLayout.size.width / 2f)
                    .coerceIn(0f, (plotWidth - tickLayout.size.width).coerceAtLeast(0f))
                drawText(
                    textLayoutResult = tickLayout,
                    topLeft = Offset(x, size.height - tickLayout.size.height),
                )
            }
        }
    }
}

/** 单系列折线：多系列的退化形态，给单值指标的详情页用。 */
@Composable
fun TrendChart(
    entries: List<TrendEntry>,
    target: Double?,
    decimals: Int,
    unit: String,
    color: Color,
    range: TrendRange,
    modifier: Modifier = Modifier,
) = TrendSeriesChart(
    series = listOf(TrendSeries(entries = entries, color = color)),
    target = target,
    decimals = decimals,
    unit = unit,
    range = range,
    modifier = modifier,
)

/** 列表 / 面板部件用的小曲线：不画坐标轴、目标和网格，只保留走势。 */
@Composable
fun TrendMiniChart(
    entries: List<TrendEntry>,
    target: Double?,
    color: Color,
    modifier: Modifier = Modifier,
    range: TrendRange = TrendRange.ALL,
) {
    val now = remember(entries, range) { System.currentTimeMillis() }
    BoxWithConstraints(modifier) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val data = remember(entries, target, range, widthPx, now) {
            buildTrendChart(entries = entries, range = range, now = now, target = target, widthPx = widthPx)
        }
        if (!data.hasData) return@BoxWithConstraints
        Canvas(Modifier.fillMaxSize()) {
            val offsets = data.points.map {
                Offset(it.fractionX * size.width, it.fractionY * size.height)
            }
            data.targetFractionY?.let { fraction ->
                drawLine(
                    color = color.copy(alpha = 0.5f),
                    start = Offset(0f, fraction * size.height),
                    end = Offset(size.width, fraction * size.height),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                )
            }
            if (offsets.size >= 2) {
                val path = Path().apply {
                    moveTo(offsets.first().x, offsets.first().y)
                    offsets.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
            drawCircle(color = color, radius = 2.dp.toPx(), center = offsets.last())
        }
    }
}

private fun DrawScope.drawGrid(width: Float, height: Float, color: Color) {
    listOf(0f, 0.5f, 1f).forEach { fraction ->
        val y = fraction * height
        drawLine(
            color = color.copy(alpha = 0.10f),
            start = Offset(0f, y),
            end = Offset(width, y),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

private fun DrawScope.drawTargetLabel(
    label: String,
    targetY: Float,
    width: Float,
    measurer: TextMeasurer,
    color: Color,
) {
    val layout = measurer.measure(label, labelStyle(color))
    val x = (width - layout.size.width).coerceAtLeast(0f)
    val y = (targetY - layout.size.height - 2.dp.toPx()).coerceAtLeast(0f)
    drawText(textLayoutResult = layout, topLeft = Offset(x, y))
}

private fun labelStyle(color: Color): TextStyle = TextStyle(fontSize = 10.sp, color = color)