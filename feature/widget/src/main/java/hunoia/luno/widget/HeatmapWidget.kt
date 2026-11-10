package hunoia.luno.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import hunoia.luno.config.model.Habit
import hunoia.luno.config.model.canCheck
import hunoia.luno.config.model.currentStreak
import hunoia.luno.domain.gridpanel.HabitCenter
import hunoia.luno.gridpanel.HabitExtras
import hunoia.luno.gridpanel.habit.HabitHeatmapGeometry
import hunoia.luno.gridpanel.habit.HabitHeatmapLevelAlpha
import hunoia.luno.gridpanel.habit.HabitHeatmapMaxDays
import hunoia.luno.gridpanel.habit.HabitHeatmapRows
import hunoia.luno.gridpanel.habit.HabitTitleGap
import hunoia.luno.gridpanel.habit.habitHeatmapFirstDay
import hunoia.luno.gridpanel.habit.habitHeatmapGeometry
import hunoia.luno.gridpanel.habit.habitHeatmapLevel
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** 单张热力位图的像素预算（约 288KB，ARGB_8888）；两张合计留足 RemoteViews 事务余量。 */
private const val HeatmapBitmapPixelBudget = 72_000f

/**
 * 习惯热力图桌面小部件（Glance）：单个习惯的近 90 天打卡热力图。
 *
 * 布局：标题「名 · 连续 N 天」+ 7 行 × N 列的热力格；列数 / 格径随部件实际尺寸
 * 自适应（小部件显示较少天数，大部件最多 90 天）。最后一列是本周，今天在真实星期行，
 * 未来格子留空。
 *
 * 渲染：两张白色 Bitmap 叠层 + [ColorFilter.tint]。底层所有有效格统一 tint
 * `onSurface` 淡色（跳过 / 断签 / 未开始只剩这一层）；上层按完成度 1–5 档画不同
 * alpha 的白色，再 tint 习惯色。API 31+ 的 DayNightColorProvider 让深浅色即时跟随。
 *
 * 交互与点阵部件一致：标题 → 习惯编辑页；网格区今天可打卡 → 确认弹窗（+1），
 * 否则 → 编辑页。绑定复用 [HabitWidgetConfigActivity] 的 `HABIT_ID`。
 */
class HeatmapWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val config by HabitCenter.habitConfig.collectAsState(initial = null)
            val prefs = currentState<Preferences>()
            val loaded = config
            if (loaded == null) {
                Box(
                    modifier = GlanceModifier.fillMaxSize().padding(top = WidgetOuterTop),
                ) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .cornerRadius(ItemCorner)
                            .background(GlanceTheme.colors.surface),
                    ) {}
                }
            } else {
                val habitId = prefs[WidgetStateKeys.HABIT_ID].orEmpty()
                val habit = loaded.habitById(habitId)
                val today = LocalDate.now().toEpochDay()
                val checks = habit?.let { loaded.checks[it.id].orEmpty() }.orEmpty()
                val counts = habit?.let { loaded.counts[it.id].orEmpty() }.orEmpty()
                val target = habit?.safeDailyTarget ?: 1
                val interval = habit?.interval ?: 1
                val streak = habit?.let { currentStreak(checks, today, it.interval) } ?: 0L
                val emptyText = context.getString(
                    if (habitId.isNotEmpty()) {
                        R.string.widget_habit_missing
                    } else {
                        R.string.widget_habit_unconfigured
                    },
                )
                val titleText = habit?.let {
                    context.getString(R.string.widget_habit_streak, it.name, streak)
                } ?: ""
                HeatmapContent(
                    context = context,
                    habit = habit,
                    checks = checks,
                    counts = counts,
                    target = target,
                    interval = interval,
                    today = today,
                    titleText = titleText,
                    emptyText = emptyText,
                )
            }
        }
    }
}

@Composable
private fun HeatmapContent(
    context: Context,
    habit: Habit?,
    checks: Set<Long>,
    counts: Map<Long, Int>,
    target: Int,
    interval: Int,
    today: Long,
    titleText: String,
    emptyText: String,
) {
    val colors = GlanceTheme.colors
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(top = WidgetOuterTop),
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(ItemCorner)
                .background(colors.surface)
                .padding(WidgetInnerPadding),
        ) {
            if (habit == null) {
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = emptyText,
                        modifier = GlanceModifier.clickable(
                            onClick = actionStartActivity(openAppIntent(context)),
                        ),
                        style = TextStyle(
                            color = colors.onSurfaceVariant,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
            } else {
                HeatmapBody(
                    context = context,
                    habit = habit,
                    checks = checks,
                    counts = counts,
                    target = target,
                    interval = interval,
                    today = today,
                    titleText = titleText,
                )
            }
        }
    }
}

@Composable
private fun HeatmapBody(
    context: Context,
    habit: Habit,
    checks: Set<Long>,
    counts: Map<Long, Int>,
    target: Int,
    interval: Int,
    today: Long,
    titleText: String,
) {
    val colors = GlanceTheme.colors
    val size = LocalSize.current
    val innerW = (size.width - WidgetInnerPadding * 2).coerceAtLeast(24.dp)
    val innerH = (size.height - WidgetOuterTop - WidgetInnerPadding * 2).coerceAtLeast(24.dp)
    val titleSizeSp = (innerW.value * 0.11f).coerceIn(10f, 14f)
    val titleHeight = maxOf(singleLineTextHeight(context, titleSizeSp), 9.dp)
    val gridHeight = (innerH - titleHeight - HabitTitleGap).coerceAtLeast(16.dp)
    val geometry = habitHeatmapGeometry(innerW, gridHeight)
    val habitColor = habitColor(habit, colors)
    val neutral = colors.onSurface
    val (baseBitmap, levelBitmap) = buildHeatmapBitmaps(
        context = context,
        geometry = geometry,
        today = today,
        checks = checks,
        counts = counts,
        target = target,
    )

    Column(modifier = GlanceModifier.fillMaxSize()) {
        // 标题行：主色点 + 「名 · N 天」；点击打开编辑页。
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(onClick = actionStartActivity(HabitExtras.editIntent(context, habit.id))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = GlanceModifier
                    .size(9.dp)
                    .cornerRadius(4.5.dp)
                    .background(habitColor),
            ) {}
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = titleText,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = colors.onSurface,
                    fontSize = titleSizeSp.sp,
                ),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height(HabitTitleGap))
        val canCheckToday = canCheck(checks, today, interval, today)
        val todayCount = if (today in checks) target else (counts[today] ?: 0)
        val gridDescription = context.getString(
            if (canCheckToday) R.string.widget_habit_check else R.string.widget_habit_edit,
            habit.name,
        )
        val gridAction = if (canCheckToday) {
            actionStartActivity(
                WidgetConfirmDialogActivity.intent(
                    context,
                    WidgetConfirmDialogActivity.ConfirmKind.HABIT_CHECK,
                    habit.id,
                    habit.name,
                    count = todayCount,
                    target = target,
                ),
            )
        } else {
            actionStartActivity(HabitExtras.editIntent(context, habit.id))
        }
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .defaultWeight()
                .semantics { contentDescription = gridDescription }
                .clickable(onClick = gridAction),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = GlanceModifier.size(
                    geometry.step * geometry.cols,
                    geometry.step * HabitHeatmapRows,
                ),
            ) {
                // 底层：所有有效日期一格淡色（跳过 / 断签 / 未开始只剩这一层）。
                Image(
                    provider = ImageProvider(baseBitmap),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    colorFilter = ColorFilter.tint(neutral.alpha(context, 0.12f)),
                )
                // 上层：按完成度 1–5 档的白色 alpha，tint 后成为习惯色深浅。
                Image(
                    provider = ImageProvider(levelBitmap),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    colorFilter = ColorFilter.tint(habitColor),
                )
            }
        }
    }
}

/**
 * 生成两张同尺寸白色位图：底层所有有效格（alpha 满），上层仅按完成度 1–5 档画。
 *
 * 位图尺寸按 dp × 缩放比计算：RemoteViews 经 Binder 传位图，单次事务约 1MB，
 * 大部件（13×7 格 @18dp）按屏幕 density 出图两张会直接超限。这里给每张图设像素预算，
 * 超预算时降采样、由 ImageView 平滑放大；小部件仍用满 density，格子保持清晰。
 *
 * 几何 / 分档收口在 `:feature:gridpanel` 的 `HabitHeatmapGeometry`（architecture §7.5）。
 */
private fun buildHeatmapBitmaps(
    context: Context,
    geometry: HabitHeatmapGeometry,
    today: Long,
    checks: Set<Long>,
    counts: Map<Long, Int>,
    target: Int,
): Pair<Bitmap, Bitmap> {
    val requestedDensity = context.resources.displayMetrics.density
    val widthDp = (geometry.step.value * geometry.cols).coerceAtLeast(1f)
    val heightDp = (geometry.step.value * HabitHeatmapRows).coerceAtLeast(1f)
    val budgetScale = sqrt(HeatmapBitmapPixelBudget / (widthDp * heightDp))
    val scale = minOf(requestedDensity, budgetScale).coerceAtLeast(1f)
    val stepPx = (geometry.step.value * scale).coerceAtLeast(1f)
    val cellPx = (stepPx * 0.72f).coerceAtLeast(1f)
    val widthPx = (stepPx * geometry.cols).roundToInt().coerceAtLeast(1)
    val heightPx = (stepPx * HabitHeatmapRows).roundToInt().coerceAtLeast(1)
    val base = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val levels = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val baseCanvas = Canvas(base)
    val levelCanvas = Canvas(levels)
    val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    val levelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    val radius = cellPx * 0.22f
    val inset = (stepPx - cellPx) / 2f
    // 最后一列 = 本周（周一开头）；今天落在真实星期行，未来格子留空。
    val firstDay = habitHeatmapFirstDay(today, geometry.cols)
    val minDay = today - (HabitHeatmapMaxDays - 1)
    for (col in 0 until geometry.cols) {
        for (row in 0 until HabitHeatmapRows) {
            val day = firstDay + col * HabitHeatmapRows + row
            if (day > today || day < minDay) continue
            val left = col * stepPx + inset
            val top = row * stepPx + inset
            val rect = RectF(left, top, left + cellPx, top + cellPx)
            baseCanvas.drawRoundRect(rect, radius, radius, basePaint)
            val level = habitHeatmapLevel(day, checks, counts, target)
            if (level > 0) {
                levelPaint.alpha = HabitHeatmapLevelAlpha[level - 1]
                levelCanvas.drawRoundRect(rect, radius, radius, levelPaint)
            }
        }
    }
    return base to levels
}
