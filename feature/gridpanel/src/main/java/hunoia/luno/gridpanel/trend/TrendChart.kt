package hunoia.luno.gridpanel.trend

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import hunoia.luno.config.model.TrendEntry
import hunoia.luno.gridpanel.R
import hunoia.luno.ui.component.PressTextButton
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.ShapeRole
import hunoia.luno.ui.theme.UiAppearance
import hunoia.luno.ui.theme.TileInnerPadding
import kotlin.math.abs

/** 一条折线：一个读数槽位的序列 + 它的颜色。 */
data class TrendSeries(val entries: List<TrendEntry>, val color: Color)

/**
 * 多系列折线色：第 0 条用指标本色，后面的沿明度轴拉开。
 *
 * 亮色主题往黑压暗、暗色主题往白提亮，只动明度不动色相：同一指标的几个读数用同族颜色，
 * 读图时「谁是谁」靠图例 + 深浅，不靠红绿对照（红绿已经在涨跌语义里被占用）。
 */
fun trendSeriesColor(base: Color, index: Int, count: Int, dark: Boolean = false): Color {
    if (count <= 1 || index <= 0) return base
    val step = 0.6f / (count - 1)
    val amount = (step * index).coerceIn(0f, 0.6f) * 0.55f
    return lerp(base, if (dark) Color.White else Color.Black, amount)
}

/** 十字线命中的点：该点 + 所属系列下标（读数卡要按系列名对齐）。 */
private data class TrendNearestPoint(val seriesIndex: Int, val point: TrendPlotPoint)

/**
 * 趋势折线图（可多系列）。
 *
 * X 轴按真实时间线性映射（不是等距索引），所以记录稀疏的区间自然留白、密集区间自然变紧；
 * 视觉上的「短横轴」由 [range] 切换，长范围的性能由 [buildTrendChartLayout] 的像素桶降采样保证。
 * 所有点用一条折线连接：缺记录只代表没测量，不代表数值归零。
 *
 * 交互：按下 / 横向拖动出十字线读某条记录的值，松手消失；竖向拖动交给外层列表滚动。
 * 只有一条记录时画水平参考线 + 单点，不再是一张空白图。
 *
 * @param labels 每个系列的图例名，用于十字线读数；缺失时回落 `#N`。
 * @param onShowAll 当前范围内没有记录但历史有数据时的「查看全部」去向。
 */
@Composable
fun TrendSeriesChart(
    series: List<TrendSeries>,
    target: Double?,
    decimals: Int,
    unit: String,
    range: TrendRange,
    labels: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    onShowAll: (() -> Unit)? = null,
) {
    val now = remember(series, range) { System.currentTimeMillis() }
    val measurer = rememberTextMeasurer()
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = onSurfaceVariant)
    val chipColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val emptyText = stringResource(R.string.trend_detail_no_entries)
    val emptyRangeText = stringResource(R.string.trend_chart_empty_range)
    val targetLabel = stringResource(R.string.trend_chart_target, formatTrendValue(target ?: 0.0, decimals))
    val showAllLabel = stringResource(R.string.trend_range_all)
    // 文本样式在组合态解析；Canvas 的 DrawScope 里不能再调用 @Composable。
    val readoutBodyStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    val readoutSubStyle = MaterialTheme.typography.labelSmall.copy(color = onSurfaceVariant)

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
            val hasHistory = series.any { it.entries.isNotEmpty() }
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = if (hasHistory) emptyRangeText else emptyText,
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurfaceVariant,
                )
                if (hasHistory && onShowAll != null) {
                    PressTextButton(onClick = onShowAll) {
                        Text(showAllLabel)
                    }
                }
            }
            return@BoxWithConstraints
        }

        // Y 轴 3 个刻度（上 / 中 / 下）；左留白按实测标签宽度算，fontScale 变大时不压进绘图区。
        val yTickValues = remember(layout.valueMin, layout.valueMax) {
            listOf(layout.valueMax, (layout.valueMax + layout.valueMin) / 2.0, layout.valueMin)
        }
        val yTickLayouts = remember(yTickValues, decimals, unit, axisStyle) {
            yTickValues.map { value -> measurer.measure(formatTrendValue(value, decimals) + unit, axisStyle) }
        }
        val yLabelWidth = yTickLayouts.maxOf { it.size.width }.toFloat()
        val labelGap = with(LocalDensity.current) { TileInnerPadding.toPx() }
        val leftInset = yLabelWidth + labelGap
        val xTickLayouts = remember(layout.axisTicks, axisStyle) {
            layout.axisTicks.map { tick -> measurer.measure(tick.label, axisStyle) }
        }
        val xLabelHeight = xTickLayouts.maxOfOrNull { it.size.height }?.toFloat() ?: 0f
        val bottomInset = xLabelHeight + with(LocalDensity.current) { 4.dp.toPx() }
        val colors = series.map { it.color }
        val lineColor = colors.firstOrNull() ?: onSurfaceVariant
        val drawFill = series.size == 1

        val visibleCount = remember(series, range, now) {
            val all = series.flatMap { it.entries }.sortedBy { it.atMillis }
            val window = trendWindow(all, range, now)
            all.asSequence()
                .filter { it.atMillis in window.first..window.last }
                .map { it.atMillis }
                .distinct()
                .count()
        }
        val latestValue = series.asSequence()
            .flatMap { it.entries.asSequence() }
            .maxByOrNull { it.atMillis }
            ?.value
        val description = stringResource(
            R.string.trend_chart_description,
            latestValue?.let { formatTrendValue(it, decimals) + unit } ?: emptyText,
            visibleCount,
        )

        var scrubFraction by remember(series, range) { mutableStateOf<Float?>(null) }

        val chartModifier = Modifier
            .fillMaxSize()
            .pointerInput(layout, leftInset, bottomInset) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun fractionOf(x: Float): Float {
                        val plotWidth = (size.width - leftInset).coerceAtLeast(1f)
                        return ((x - leftInset) / plotWidth).coerceIn(0f, 1f)
                    }
                    scrubFraction = fractionOf(down.position.x)
                    var totalDx = 0f
                    var totalDy = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val delta = change.positionChange()
                        totalDx += abs(delta.x)
                        totalDy += abs(delta.y)
                        // 竖向为主 → 让外层列表滚动，不抢手势。
                        if (totalDy > totalDx && totalDy > viewConfiguration.touchSlop) break
                        scrubFraction = fractionOf(change.position.x)
                    }
                    scrubFraction = null
                }
            }
            .semantics { contentDescription = description }

        Canvas(chartModifier) {
            val plotWidth = (size.width - leftInset).coerceAtLeast(1f)
            val plotHeight = (size.height - bottomInset).coerceAtLeast(1f)
            val plotLeft = leftInset
            val plotRight = plotLeft + plotWidth
            val plotBottom = plotHeight

            // 网格 + Y 轴标签（三档，与网格线同高）。
            yTickLayouts.forEachIndexed { index, textLayout ->
                val fraction = index / (yTickLayouts.size - 1).toFloat()
                val y = fraction * plotHeight
                drawLine(
                    color = onSurfaceVariant.copy(alpha = 0.10f),
                    start = Offset(plotLeft, y),
                    end = Offset(plotRight, y),
                    strokeWidth = 1.dp.toPx(),
                )
                val labelX = (plotLeft - labelGap - textLayout.size.width).coerceAtLeast(0f)
                val labelY = (y - textLayout.size.height / 2f)
                    .coerceIn(0f, (plotHeight - textLayout.size.height).coerceAtLeast(0f))
                drawText(textLayoutResult = textLayout, topLeft = Offset(labelX, labelY))
            }

            val targetY = layout.targetFractionY?.let { it * plotHeight }
            if (targetY != null) {
                drawLine(
                    color = lineColor.copy(alpha = 0.65f),
                    start = Offset(plotLeft, targetY),
                    end = Offset(plotRight, targetY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                )
                drawTargetLabel(
                    label = targetLabel,
                    targetY = targetY,
                    plotLeft = plotLeft,
                    plotRight = plotRight,
                    barTop = 0f,
                    measurer = measurer,
                    style = axisStyle.copy(color = onSurfaceVariant.copy(alpha = 0.9f)),
                )
            }

            layout.series.forEachIndexed { index, seriesData ->
                val color = colors.getOrElse(index) { lineColor }
                if (seriesData.points.isEmpty()) return@forEachIndexed
                val offsets = seriesData.points.map {
                    Offset(plotLeft + it.fractionX * plotWidth, it.fractionY * plotHeight)
                }
                if (offsets.size == 1) {
                    // 单点：先画一条水平参考线，再画点；否则一条记录的用户只看到空白。
                    val only = offsets.first()
                    drawLine(
                        color = color.copy(alpha = 0.35f),
                        start = Offset(plotLeft, only.y),
                        end = Offset(plotRight, only.y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                    )
                } else {
                    val linePath = Path().apply {
                        moveTo(offsets.first().x, offsets.first().y)
                        offsets.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    if (drawFill) {
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
                }
                // 点太多时只留首尾，避免变成一条毛边；短范围保留每个真实记录点。
                val dots = if (offsets.size <= 80) offsets else listOf(offsets.first(), offsets.last())
                dots.forEach { center ->
                    drawCircle(color = color, radius = 3.dp.toPx(), center = center)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.85f),
                        radius = 1.1.dp.toPx(),
                        center = center,
                    )
                }
                // 末点额外强调：外圈用系列色，内圈用画布底色。
                val last = offsets.last()
                drawCircle(color = color.copy(alpha = 0.25f), radius = 6.dp.toPx(), center = last)
                drawCircle(color = color, radius = 3.4.dp.toPx(), center = last)
            }

            // X 轴标签：按实测高度贴底，居中夹在绘图区内。
            layout.axisTicks.forEachIndexed { index, tick ->
                val textLayout = xTickLayouts[index]
                val x = (plotLeft + tick.fraction * plotWidth - textLayout.size.width / 2f)
                    .coerceIn(plotLeft, (plotRight - textLayout.size.width).coerceAtLeast(plotLeft))
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(x, plotBottom + 2.dp.toPx()),
                )
            }

            // 单系列时给末点补一个数值标签；多系列各标各的会叠在一起，读数交给十字线。
            if (series.size == 1) {
                val lastPoint = layout.series.firstOrNull()?.points?.lastOrNull()
                if (lastPoint != null) {
                    val textLayout = measurer.measure(
                        formatTrendValue(lastPoint.value, decimals) + unit,
                        axisStyle.copy(color = lineColor),
                    )
                    val anchorX = plotLeft + lastPoint.fractionX * plotWidth
                    val anchorY = lastPoint.fractionY * plotHeight
                    val x = (anchorX - textLayout.size.width)
                        .coerceIn(plotLeft, (plotRight - textLayout.size.width).coerceAtLeast(plotLeft))
                    val y = (anchorY - textLayout.size.height - 8.dp.toPx())
                        .coerceIn(0f, (plotHeight - textLayout.size.height).coerceAtLeast(0f))
                    drawText(textLayoutResult = textLayout, topLeft = Offset(x, y))
                }
            }

            val fraction = scrubFraction
            if (fraction != null) {
                val nearest = nearestTrendPoint(layout, fraction)
                if (nearest != null) {
                    val anchor = nearest.point
                    val x = plotLeft + anchor.fractionX * plotWidth
                    val y = anchor.fractionY * plotHeight
                    drawLine(
                        color = onSurfaceVariant.copy(alpha = 0.55f),
                        start = Offset(x, 0f),
                        end = Offset(x, plotBottom),
                        strokeWidth = 1.dp.toPx(),
                    )
                    // 同一时刻的其他系列也一起标出来（多读数记录共用同一个时间戳）。
                    layout.series.forEachIndexed { index, seriesData ->
                        val point = seriesData.points.firstOrNull { it.atMillis == anchor.atMillis }
                            ?: seriesData.points.minByOrNull { abs(it.fractionX - anchor.fractionX) }
                            ?: return@forEachIndexed
                        val color = colors.getOrElse(index) { lineColor }
                        drawCircle(
                            color = chipColor,
                            radius = 5.dp.toPx(),
                            center = Offset(plotLeft + point.fractionX * plotWidth, point.fractionY * plotHeight),
                        )
                        drawCircle(
                            color = color,
                            radius = 3.dp.toPx(),
                            center = Offset(plotLeft + point.fractionX * plotWidth, point.fractionY * plotHeight),
                        )
                    }
                    val timeText = trendPointTimeLabel(anchor.atMillis, layout.spanMillis)
                    val valueText = layout.series.mapIndexedNotNull { index, seriesData ->
                        val point = seriesData.points.firstOrNull { it.atMillis == anchor.atMillis }
                            ?: return@mapIndexedNotNull null
                        val name = labels.getOrNull(index)?.takeIf { it.isNotBlank() } ?: "#${index + 1}"
                        "$name ${formatTrendValue(point.value, decimals)}$unit"
                    }.joinToString(" · ")
                    drawScrubReadout(
                        timeText = timeText,
                        valueText = valueText,
                        anchorX = x,
                        anchorY = y,
                        plotLeft = plotLeft,
                        plotRight = plotRight,
                        plotHeight = plotBottom,
                        measurer = measurer,
                        bodyStyle = readoutBodyStyle,
                        subStyle = readoutSubStyle,
                        background = chipColor,
                    )
                }
            }
        }
    }
}

/** 列表 / 面板部件用的小曲线：不画坐标轴、目标和网格，只保留走势。 */
@Composable
fun TrendMiniChart(
    entries: List<TrendEntry>,
    color: Color,
    modifier: Modifier = Modifier,
    range: TrendRange = TrendRange.ALL,
) {
    val now = remember(entries, range) { System.currentTimeMillis() }
    BoxWithConstraints(modifier) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val data = remember(entries, range, widthPx, now) {
            buildTrendChart(entries = entries, range = range, now = now, target = null, widthPx = widthPx)
        }
        if (!data.hasData) return@BoxWithConstraints
        Canvas(Modifier.fillMaxSize()) {
            val offsets = data.points.map {
                Offset(it.fractionX * size.width, it.fractionY * size.height)
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

/** 最近点：跨所有系列按 X 比例找；同一时刻多系列由调用方再对齐。 */
private fun nearestTrendPoint(layout: TrendChartLayout, fraction: Float): TrendNearestPoint? {
    var best: TrendNearestPoint? = null
    var bestDistance = Float.MAX_VALUE
    layout.series.forEachIndexed { index, seriesData ->
        seriesData.points.forEach { point ->
            val distance = abs(point.fractionX - fraction)
            if (distance < bestDistance) {
                bestDistance = distance
                best = TrendNearestPoint(index, point)
            }
        }
    }
    return best
}

private fun DrawScope.drawTargetLabel(
    label: String,
    targetY: Float,
    plotLeft: Float,
    plotRight: Float,
    barTop: Float,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    val layout = measurer.measure(label, style)
    val x = (plotRight - layout.size.width).coerceAtLeast(plotLeft)
    val y = (targetY - layout.size.height - 2.dp.toPx()).coerceIn(barTop, targetY)
    drawText(textLayoutResult = layout, topLeft = Offset(x, y))
}

/** 十字线读数卡：两行文字 + 圆角底，位置夹在绘图区内，不越界。 */
private fun DrawScope.drawScrubReadout(
    timeText: String,
    valueText: String,
    anchorX: Float,
    anchorY: Float,
    plotLeft: Float,
    plotRight: Float,
    plotHeight: Float,
    measurer: TextMeasurer,
    bodyStyle: TextStyle,
    subStyle: TextStyle,
    background: Color,
) {
    if (valueText.isBlank()) return
    val subLayout = measurer.measure(timeText, subStyle)
    val bodyLayout = measurer.measure(valueText, bodyStyle)
    val padding = CardInnerSpacing.toPx()
    val gap = TileInnerPadding.toPx()
    val width = maxOf(subLayout.size.width, bodyLayout.size.width).toFloat() + padding * 2
    val height = subLayout.size.height + bodyLayout.size.height + gap + padding * 2
    val x = (anchorX - width / 2f).coerceIn(plotLeft, (plotRight - width).coerceAtLeast(plotLeft))
    val above = anchorY - height - 10.dp.toPx()
    val y = if (above >= 0f) above else (anchorY + 10.dp.toPx()).coerceAtMost(plotHeight - height)
    drawRoundRect(
        color = background,
        topLeft = Offset(x, y),
        size = Size(width, height),
        cornerRadius = CornerRadius(UiAppearance.resolvedRadius(ShapeRole.Compact).dp.toPx()),
    )
    drawText(subLayout, topLeft = Offset(x + padding, y + padding))
    drawText(bodyLayout, topLeft = Offset(x + padding, y + padding + subLayout.size.height + gap))
}
