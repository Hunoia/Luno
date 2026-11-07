package hunoia.luno.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.text.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
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
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.color.ColorProviders
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
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
import androidx.glance.unit.ColorProvider
import hunoia.luno.config.model.ColorSource
import hunoia.luno.config.model.Habit
import hunoia.luno.config.model.HabitDayState
import hunoia.luno.config.model.HabitDot
import hunoia.luno.config.model.ThemeColorKey
import hunoia.luno.config.model.canCheck
import hunoia.luno.config.model.currentStreak
import hunoia.luno.config.model.lastNDaysDots
import hunoia.luno.domain.gridpanel.HabitCenter
import hunoia.luno.gridpanel.HabitExtras
import java.time.LocalDate

/** 部件圆角：镜像内置 `ItemCorner`（gridpanel geometry = 14dp）。 */
internal val ItemCorner = 14.dp

/** 部件内边距：镜像内置 HabitHistoryCell 的 10dp。 */
internal val WidgetInnerPadding = 10.dp

/**
 * 习惯桌面小部件（Glance）：复刻内置历史部件（HabitHistoryCell）——
 * 标题「名 · 连续 N 天」+ 历史点阵网格（近 N 天勾/横杠/空）。
 *
 * 绑定：放置时经 [HabitWidgetConfigActivity] 挑一个习惯，写进每实例 state。
 * 交互：标题点击打开该习惯编辑页；点阵区点击进入打卡确认（中央多帧倒计时环，
 * 1200ms 窗口内再点才打卡；超时取消）。今日已打 / 跳过日点阵不可点（canCheck 门控）。
 *
 * 配色：主色 = `habit.colorSpec` 实时解析（自定义 ARGB / 主题角色，镜像 ui/design
 * `ColorSpec.toColor`），文字/背景走 `GlanceTheme.colors`（Material You 动态主题），
 * 点阵状态色复刻内置 `HabitDayVisual`（fill / border / mark），见 [dotVisual]。
 * 数据源 [HabitCenter.habitConfig]（MultiProcess DataStore），经 domain 门面订阅；
 * 写回走 [HabitCenter.toggleHabit]（应用内状态变更，不走 Dispatcher，architecture §7.5）。
 */
class HabitWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(DpSize(110.dp, 110.dp), DpSize(180.dp, 180.dp), DpSize(260.dp, 260.dp), DpSize(340.dp, 340.dp)),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs: Preferences = getAppWidgetState(context, id)
        val config = HabitCenter.getHabitConfig()
        val habit = config.habitById(prefs[WidgetStateKeys.HABIT_ID] ?: "")
        val today = LocalDate.now().toEpochDay()
        val checks = habit?.let { config.checks[it.id].orEmpty() }.orEmpty()
        val interval = habit?.interval ?: 1
        val streak = habit?.let { currentStreak(checks, today, it.interval) } ?: 0L
        // 待确认态：超时即无意义（下次交互当新起点）。
        val now = System.currentTimeMillis()
        val pendingAt = prefs[WidgetStateKeys.HABIT_PENDING_AT] ?: 0L
        val pending = habit != null &&
            prefs[WidgetStateKeys.HABIT_PENDING] == true &&
            now - pendingAt < ConfirmWindowMillis
        val emptyText = context.getString(
            if (config.hasHabits) R.string.widget_habit_missing else R.string.widget_habit_empty,
        )
        val titleText = habit?.let {
            context.getString(R.string.widget_habit_streak, it.name, streak)
        } ?: ""
        val confirmHint = context.getString(R.string.widget_habit_confirm)

        provideContent {
            HabitContent(
                context = context,
                habit = habit,
                checks = checks,
                interval = interval,
                today = today,
                titleText = titleText,
                emptyText = emptyText,
                confirmHint = confirmHint,
                pending = pending,
                clickable = habit != null && canCheck(checks, today, interval, today),
            )
        }
    }
}

/** 习惯色 → Glance ColorProvider：镜像 ui/design `ColorSpec.toColor`，主题角色映射 [ColorProviders]。 */
private fun habitColor(habit: Habit, colors: ColorProviders): ColorProvider = when (habit.colorSpec.source) {
    ColorSource.Custom -> ColorProvider(Color(habit.colorSpec.customColor))
    ColorSource.Theme -> themeColor(habit.colorSpec.themeKey, colors)
}

/** 主题角色 → Glance 色；Glance 1.2 无 surfaceContainer* / outlineVariant，回退 surfaceVariant / outline。 */
private fun themeColor(key: ThemeColorKey, colors: ColorProviders): ColorProvider = when (key) {
    ThemeColorKey.Primary -> colors.primary
    ThemeColorKey.PrimaryContainer -> colors.primaryContainer
    ThemeColorKey.Secondary -> colors.secondary
    ThemeColorKey.SecondaryContainer -> colors.secondaryContainer
    ThemeColorKey.Tertiary -> colors.tertiary
    ThemeColorKey.TertiaryContainer -> colors.tertiaryContainer
    ThemeColorKey.Surface -> colors.surface
    ThemeColorKey.SurfaceVariant -> colors.surfaceVariant
    ThemeColorKey.OnSurface -> colors.onSurface
    ThemeColorKey.OnSurfaceVariant -> colors.onSurfaceVariant
    ThemeColorKey.Outline -> colors.outline
    ThemeColorKey.OutlineVariant -> colors.outline
    ThemeColorKey.SurfaceContainerLow,
    ThemeColorKey.SurfaceContainer,
    ThemeColorKey.SurfaceContainerHigh,
    -> colors.surfaceVariant
}

/** 动态 ColorProvider 无法 apply alpha，这里用 getColor(context) 解析成具体 ARGB 再缩放。 */
internal fun ColorProvider.alpha(context: Context, a: Float): ColorProvider =
    ColorProvider(getColor(context).copy(alpha = a))

@Composable
private fun HabitContent(
    context: Context,
    habit: Habit?,
    checks: Set<Long>,
    interval: Int,
    today: Long,
    titleText: String,
    emptyText: String,
    confirmHint: String,
    pending: Boolean,
    clickable: Boolean,
) {
    val colors = GlanceTheme.colors
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(ItemCorner)
            .background(colors.surface)
            .padding(WidgetInnerPadding),
    ) {
        if (habit == null) {
            Text(
                text = emptyText,
                modifier = GlanceModifier.clickable(onClick = actionStartActivity(openAppIntent(context))),
                style = TextStyle(
                    color = colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                ),
            )
        } else {
            HabitBody(
                context = context,
                habit = habit,
                checks = checks,
                interval = interval,
                today = today,
                titleText = titleText,
                confirmHint = confirmHint,
                pending = pending,
                clickable = habit != null && canCheck(checks, today, interval, today),
            )
        }
    }
}

@Composable
private fun HabitBody(
    context: Context,
    habit: Habit,
    checks: Set<Long>,
    interval: Int,
    today: Long,
    titleText: String,
    confirmHint: String,
    pending: Boolean,
    clickable: Boolean,
) {
    val colors = GlanceTheme.colors
    val size = LocalSize.current
    // 点阵自适应（镜像内置 HabitHistoryCell 公式）：标题 20dp + 间距 6dp + 内边距 10dp×2。
    val contentW = (size.width - 20.dp).coerceAtLeast(24.dp)
    val contentH = (size.height - 20.dp).coerceAtLeast(24.dp)
    val titleH = 20.dp
    val gap = 6.dp
    val gridH = (contentH - titleH - gap).coerceAtLeast(20.dp)
    val minCellW = 22.dp
    val minCellH = 30.dp
    val cols = (contentW / minCellW).toInt().coerceIn(3, 8)
    val rows = (gridH / minCellH).toInt().coerceIn(1, 4)
    val cellW = ((contentW - (cols - 1).dp) / cols).coerceAtLeast(12.dp)
    val cellH = ((gridH - (rows - 1).dp) / rows).coerceAtLeast(16.dp)
    val dots = lastNDaysDots(checks, today, interval, cols * rows)
    // 点阵配色：习惯色实时解析 + 中性/错误角色按 onSurface / error 缩放 alpha（镜像 HabitDayVisual）。
    val habitColor = habitColor(habit, colors)
    val neutral = colors.onSurface
    val errorDim = colors.error.alpha(context, 0.70f)
    val neutralRing = neutral.alpha(context, 0.15f)
    val neutralFill = neutral.alpha(context, 0.12f)
    val dateColor = neutral.alpha(context, 0.50f)

    Column(
        modifier = GlanceModifier.fillMaxSize(),
    ) {
        // 标题行：主色点 + 「名 · N 天」；点击打开编辑页。
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(onClick = actionStartActivity(editHabitIntent(context, habit.id))),
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
                    fontSize = (contentW.value * 0.11f).coerceIn(10f, 14f).sp,
                ),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height(gap))
        // 点阵区域：点击进入打卡确认（中央倒计时环）；倒计时内再点打卡今天。
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .defaultWeight()
                .semantics { contentDescription = confirmHint }
                .then(
                    if (clickable) {
                        GlanceModifier.clickable(onClick = actionRunCallback<HabitCheckCallback>())
                    } else {
                        GlanceModifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Top,
            ) {
                for (row in 0 until rows) {
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight(),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        for (col in 0 until cols) {
                            val dot = dots.getOrNull(row * cols + col)
                            if (dot != null) {
                                HabitDotCell(
                                    context = context,
                                    dot = dot,
                                    habitColor = habitColor,
                                    errorDim = errorDim,
                                    neutralRing = neutralRing,
                                    neutralFill = neutralFill,
                                    dateColor = dateColor,
                                    cellWidth = cellW,
                                    cellHeight = cellH,
                                    modifier = GlanceModifier.defaultWeight(),
                                )
                            } else {
                                Spacer(GlanceModifier.defaultWeight())
                            }
                        }
                    }
                }
            }
            // 待确认胶囊：主色实底 + 确认提示（无 scrim，不遮点阵；替换原黑色遮罩 + 位图进度环）。
            if (pending) {
                Box(
                    modifier = GlanceModifier
                        .background(habitColor)
                        .cornerRadius(16.dp)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = confirmHint,
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 点阵单格视觉：镜像内置 HabitDayVisual（fill + border + mark）；Glance 无 border → ring 矢量近似。 */
private data class DotVisual(
    val fill: ColorProvider,
    val ring: ColorProvider?,
    val mark: DotMark,
    val markTint: ColorProvider,
)

private enum class DotMark { NONE, CHECK, MINUS }

/** 状态 → 视觉：与内置 `HabitDayVisual` 同口径（DUE=空心描边、OVERDUE=中性底+错误描边）。 */
private fun dotVisual(
    context: Context,
    state: HabitDayState,
    habitColor: ColorProvider,
    errorDim: ColorProvider,
    neutralRing: ColorProvider,
    neutralFill: ColorProvider,
): DotVisual {
    val transparent = ColorProvider(Color.Transparent)
    return when (state) {
        // 已打 = 实心主色 + 白勾（border 同色不可见）。
        HabitDayState.CHECKED -> DotVisual(habitColor, null, DotMark.CHECK, ColorProvider(Color.White))
        // 待打 = 空心：透明底 + 主色描边。
        HabitDayState.DUE -> DotVisual(transparent, habitColor, DotMark.NONE, transparent)
        // 跳过 = 主色 30% 底 + 主色 15% 描边 + 主色横杠（标记用不透明主色，保证对比度）。
        HabitDayState.SKIPPED -> DotVisual(
            habitColor.alpha(context, 0.30f),
            habitColor.alpha(context, 0.15f),
            DotMark.MINUS,
            habitColor,
        )
        // 断签 = 中性 12% 底 + 错误 70% 描边（红圈）。
        HabitDayState.OVERDUE -> DotVisual(neutralFill, errorDim, DotMark.NONE, transparent)
        // 未开始 = 中性 12% 底 + 中性 15% 描边。
        HabitDayState.INACTIVE -> DotVisual(neutralFill, neutralRing, DotMark.NONE, transparent)
    }
}

private fun DotMark.drawableRes(): Int = when (this) {
    DotMark.CHECK -> R.drawable.widget_check
    DotMark.MINUS -> R.drawable.widget_minus
    DotMark.NONE -> 0
}

/** 点阵单格：日期文本 + 圆点（勾 / 横杠 / 空），镜像内置 HabitDotView（含描边与无障碍描述）。 */
@Composable
private fun HabitDotCell(
    context: Context,
    dot: HabitDot,
    habitColor: ColorProvider,
    errorDim: ColorProvider,
    neutralRing: ColorProvider,
    neutralFill: ColorProvider,
    dateColor: ColorProvider,
    cellWidth: Dp,
    cellHeight: Dp,
    modifier: GlanceModifier = GlanceModifier,
) {
    val dateSize = (cellWidth.value * 0.28f).coerceIn(6f, 9f)
    val dateSizeDp = dateSize.dp
    val gap = 1.dp
    val safety = 1.dp
    val maxDotByW = (cellWidth * 0.75f).coerceIn(6.dp, 18.dp)
    val maxDotByH = (cellHeight - dateSizeDp - gap - safety).coerceAtLeast(4.dp)
    val dotSize = minOf(maxDotByW, maxDotByH)
    val visual = dotVisual(
        context = context,
        state = dot.state,
        habitColor = habitColor,
        errorDim = errorDim,
        neutralRing = neutralRing,
        neutralFill = neutralFill,
    )
    val stateLabel = dot.state.label(context)
    val day = dayOfMonthOf(dot.day)
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = day.toString(),
            style = TextStyle(
                color = dateColor,
                fontSize = dateSize.sp,
            ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(gap))
        Box(
            modifier = GlanceModifier
                .size(dotSize)
                .cornerRadius(dotSize / 2)
                .background(visual.fill)
                .semantics { contentDescription = "$day 日 · $stateLabel" },
            contentAlignment = Alignment.Center,
        ) {
            // 描边环：空心描边近似（内置 border 1.5dp）。
            if (visual.ring != null) {
                Image(
                    provider = ImageProvider(R.drawable.widget_ring),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    colorFilter = ColorFilter.tint(visual.ring),
                )
            }
            if (visual.mark != DotMark.NONE) {
                Image(
                    provider = ImageProvider(visual.mark.drawableRes()),
                    contentDescription = null,
                    modifier = GlanceModifier.size(dotSize * 0.7f),
                    colorFilter = ColorFilter.tint(visual.markTint),
                )
            }
        }
    }
}

/** 状态文案：与内置 `HabitDayState.label()` 同一套说法（无障碍 / 视觉共用）。
 *  注：Glance 组合里不能用 `stringResource`（需 LocalConfiguration，Glance 不提供），
 *  故用 `context.getString`（在 provideGlance / 非组合处解析字符串）。 */
private fun HabitDayState.label(context: Context): String = context.getString(
    when (this) {
        HabitDayState.CHECKED -> R.string.widget_habit_day_checked
        HabitDayState.DUE -> R.string.widget_habit_day_due
        HabitDayState.SKIPPED -> R.string.widget_habit_day_skipped
        HabitDayState.OVERDUE -> R.string.widget_habit_day_overdue
        HabitDayState.INACTIVE -> R.string.widget_habit_day_inactive
    },
)

/** epochDay → 月内日（与内置 HabitDotView 一致）。 */
private fun dayOfMonthOf(epochDay: Long): Int = LocalDate.ofEpochDay(epochDay).dayOfMonth

/** 打开 Luno 主界面（无目标习惯，进主页）。 */
private fun openAppIntent(context: Context): Intent = Intent().apply {
    component = ComponentName(context.packageName, HabitExtras.ACTIVITY_MAIN)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}

/** 打开 Luno 主界面并进 [habitId] 的编辑页。 */
private fun editHabitIntent(context: Context, habitId: String): Intent = openAppIntent(context).apply {
    putExtra(HabitExtras.EXTRA_EDIT_HABIT_ID, habitId)
}

/**
 * 点阵区打卡双击确认回调：首点置待确认态，窗口内再点完成打卡，
 * 窗口内再点才真正打卡；超时/再次首点都从新起点开始。
 * 已打 / 跳过（clickable=false）时点阵不可点，不会走到这里。
 */
class HabitCheckCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val widget = HabitWidget()
        val prefs: Preferences = widget.getAppWidgetState(context, glanceId)
        val now = System.currentTimeMillis()
        val pending = prefs[WidgetStateKeys.HABIT_PENDING] == true
        val pendingAt = prefs[WidgetStateKeys.HABIT_PENDING_AT] ?: 0L
        if (pending && now - pendingAt < ConfirmWindowMillis) {
            val habitId = prefs[WidgetStateKeys.HABIT_ID]
            val config = HabitCenter.getHabitConfig()
            val habit = config.habitById(habitId ?: "")
            if (habit != null) HabitCenter.toggleHabit(habit.id, LocalDate.now().toEpochDay())
            updateAppWidgetState(context, glanceId) {
                it[WidgetStateKeys.HABIT_PENDING] = false
                it.remove(WidgetStateKeys.HABIT_PENDING_AT)
            }
            widget.update(context, glanceId)
        } else {
            updateAppWidgetState(context, glanceId) {
                it[WidgetStateKeys.HABIT_PENDING] = true
                it[WidgetStateKeys.HABIT_PENDING_AT] = now
            }
            widget.update(context, glanceId)
        }
    }
}
