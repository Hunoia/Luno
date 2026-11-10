package hunoia.luno.widget

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
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
import androidx.glance.color.ColorProviders
import androidx.glance.currentState
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
import hunoia.luno.gridpanel.habit.HabitDayMark
import hunoia.luno.gridpanel.habit.HabitDayMarkTint
import hunoia.luno.gridpanel.habit.HabitDayRole
import hunoia.luno.gridpanel.habit.HabitDayVisual
import hunoia.luno.gridpanel.habit.HabitTitleGap
import hunoia.luno.gridpanel.habit.dotSize
import hunoia.luno.gridpanel.habit.habitGridGeometry
import hunoia.luno.gridpanel.habit.visual
import java.time.LocalDate

/** 部件圆角：镜像内置 `ItemCorner`（gridpanel geometry = 14dp）。 */
internal val ItemCorner = 14.dp

/** 部件内边距：镜像内置 HabitHistoryCell 的 10dp。 */
internal val WidgetInnerPadding = 10.dp

/** 部件外边距（仅顶部）：习惯 / 待办共用；底部保持贴 cell 底。 */
internal val WidgetOuterTop = 14.dp

/**
 * 习惯桌面小部件（Glance）：复刻内置历史部件（HabitHistoryCell）——
 * 标题「名 · 连续 N 天」+ 历史点阵网格（近 N 天勾/横杠/空）。
 *
 * 绑定：放置时经 [HabitWidgetConfigActivity] 挑一个习惯，写进每实例 state。
 * 交互：标题点击打开该习惯编辑页；点阵区今天可打卡时 → 普通确认弹窗（[WidgetConfirmDialogActivity]），
 * 已打卡 / 跳过时 → 直接打开编辑页。
 *
 * 配色：主色 = `habit.colorSpec` 实时解析（自定义 ARGB / 主题角色，镜像 ui/design
 * `ColorSpec.toColor`），文字/背景走 `GlanceTheme.colors`（Material You 动态主题），
 * 点阵状态色复刻内置 `HabitDayVisual`（fill / border / mark），见 [HabitDayVisual]。
 * 数据源 [HabitCenter.habitConfig]（MultiProcess DataStore），经 domain 门面订阅；
 * 写回走 [HabitCenter.incrementHabit]（应用内状态变更，不走 Dispatcher，architecture §7.5）。
 */
class HabitWidget : GlanceAppWidget() {

    // Exact：LocalSize = 实际绑定尺寸。Responsive 下 LocalSize 只是最接近的尺寸桶，
    // 但 RemoteViews 会按真实尺寸拉伸，几何按桶算会让点阵留白偏大（内置部件用实际约束则正常）。
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // 数据与每实例状态必须在**组合内**读取：Glance 的 session 在 45s 内是复用的，
            // update() 只重跑 provideContent 的组合，不会再次执行 provideGlance。
            // 在外面读一次再当参数传进来，部件会一直显示 session 创建时的旧数据
            // （打卡后仍停在旧点阵 / 待办完成后行不消失）。
            // 首帧用 null 表示「数据流还没到」，不能拿默认空配置冒充已加载：
            // 否则已绑定的部件会先闪一下「习惯已删除」。
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
                // 未绑定（放置配置被取消 / 旧版本没写入 id）不能报「习惯已删除」。
                val emptyText = context.getString(
                    if (habitId.isNotEmpty()) R.string.widget_habit_missing else R.string.widget_habit_unconfigured,
                )
                val titleText = habit?.let {
                    context.getString(R.string.widget_habit_streak, it.name, streak)
                } ?: ""

                HabitContent(
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

/** 习惯色 → Glance ColorProvider：镜像 ui/design `ColorSpec.toColor`，主题角色映射 [ColorProviders]。 */
internal fun habitColor(habit: Habit, colors: ColorProviders): ColorProvider = when (habit.colorSpec.source) {
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

@Composable
private fun HabitContent(
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
    // 外层只负责顶部外边距（透明），卡片背景放在内层，才能真的从外边距下方开始。
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
                HabitBody(
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
private fun HabitBody(
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
    // 几何与内置历史部件共用（HabitGridGeometry），这里扣除顶部外边距 + 内边距。
    val innerW = (size.width - WidgetInnerPadding * 2).coerceAtLeast(24.dp)
    val innerH = (size.height - WidgetOuterTop - WidgetInnerPadding * 2).coerceAtLeast(24.dp)
    val titleSizeSp = (innerW.value * 0.11f).coerceIn(10f, 14f)
    // 标题行实际高度按 TextView 字体度量算（可能 > 20dp），避免把点阵区算高后底部裁切。
    val titleHeight = maxOf(singleLineTextHeight(context, titleSizeSp), 9.dp)
    val grid = habitGridGeometry(innerW, innerH, titleHeight)
    val dots = lastNDaysDots(
        checks = checks,
        today = today,
        interval = interval,
        n = grid.cols * grid.rows,
        counts = counts,
        target = target,
    )
    // Glance 的 TextView 没有 lineHeight 控制，单行高度 ≈ fontMetrics(top..bottom)，
    // 比 fontSize 大；按这个高度留位，避免点阵圆点底部被裁。
    val dateTextHeight = singleLineTextHeight(context, grid.dateSizeSp)
    val dotSize = grid.dotSize(dateTextHeight)
    // 点阵配色：角色 → 实际色；状态 → 角色的映射由内置 HabitDayVisual 唯一提供。
    val habitColor = habitColor(habit, colors)
    val neutral = colors.onSurface
    val error = colors.error
    val dateColor = neutral.alpha(context, 0.50f)

    Column(
        modifier = GlanceModifier.fillMaxSize(),
    ) {
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
        // 点阵区域：今天可打卡 → 普通确认弹窗；已打卡 / 跳过 → 跳转习惯编辑页。
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
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Top,
            ) {
                for (row in 0 until grid.rows) {
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight(),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        for (col in 0 until grid.cols) {
                            val dot = dots.getOrNull(row * grid.cols + col)
                            if (dot != null) {
                                HabitDotCell(
                                    context = context,
                                    dot = dot,
                                    habitColor = habitColor,
                                    neutral = neutral,
                                    error = error,
                                    dateColor = dateColor,
                                    dateSizeSp = grid.dateSizeSp,
                                    dotSize = dotSize,
                                    modifier = GlanceModifier.defaultWeight(),
                                )
                            } else {
                                Spacer(GlanceModifier.defaultWeight())
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 角色 → Glance 色；状态 → 角色由内置 [HabitDayVisual] 唯一提供，这里只做宿主色解析。 */
private fun roleColor(
    role: HabitDayRole?,
    primary: ColorProvider,
    neutral: ColorProvider,
    error: ColorProvider,
): ColorProvider? = when (role) {
    HabitDayRole.PRIMARY -> primary
    HabitDayRole.NEUTRAL -> neutral
    HabitDayRole.ERROR -> error
    null -> null
}

private fun HabitDayMark.drawableRes(): Int = when (this) {
    HabitDayMark.CHECK -> R.drawable.widget_check
    HabitDayMark.MINUS -> R.drawable.widget_minus
    HabitDayMark.NONE -> 0
}

/** 点阵单格：日期文本 + 圆点，颜色/标记全部来自共享 [HabitDayVisual]。 */
@Composable
private fun HabitDotCell(
    context: Context,
    dot: HabitDot,
    habitColor: ColorProvider,
    neutral: ColorProvider,
    error: ColorProvider,
    dateColor: ColorProvider,
    dateSizeSp: Float,
    dotSize: Dp,
    modifier: GlanceModifier = GlanceModifier,
) {
    val visual = dot.state.visual()
    // DUE（今日待打卡）用「填充圆 + 内部主色环」：外缘是平滑的 20% 主色填充圆，
    // 环收在填充内部（83% 外径）且提高到 80%，既看得清，也不让环的弧线成为最外轮廓。
    val isDue = dot.state == HabitDayState.DUE
    val fill = if (isDue) {
        habitColor.alpha(context, 0.20f)
    } else {
        roleColor(visual.fillRole, habitColor, neutral, error)
            .withAlpha(context, visual.fillAlpha)
    }
    val ring = if (isDue) {
        habitColor.alpha(context, 0.80f)
    } else {
        roleColor(visual.borderRole, habitColor, neutral, error)
            .withAlpha(context, visual.borderAlpha)
    }
    val markTint = when (visual.markTint) {
        HabitDayMarkTint.WHITE -> ColorProvider(Color.White)
        HabitDayMarkTint.PRIMARY -> habitColor
        HabitDayMarkTint.NONE -> null
    }
    val stateLabel = dot.state.label(context)
    val day = dayOfMonthOf(dot.day)
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = day.toString(),
            style = TextStyle(color = dateColor, fontSize = dateSizeSp.sp),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(1.dp))
        Box(
            modifier = GlanceModifier
                .size(dotSize)
                .cornerRadius(dotSize / 2)
                .background(fill)
                .semantics { contentDescription = "$day 日 · $stateLabel" },
            contentAlignment = Alignment.Center,
        ) {
            // 描边环：空心描边近似（内置 border 1.5dp）；不再缩放，保持之前的小环。
            if (visual.borderRole != null) {
                Image(
                    provider = ImageProvider(R.drawable.widget_ring),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    colorFilter = ColorFilter.tint(ring),
                )
            }
            if (visual.mark != HabitDayMark.NONE && markTint != null) {
                Image(
                    provider = ImageProvider(visual.mark.drawableRes()),
                    contentDescription = null,
                    modifier = GlanceModifier.size(dotSize * 0.7f),
                    colorFilter = ColorFilter.tint(markTint),
                )
            }
            // 多次习惯的部分完成：白色进度弧 + 习惯色 tint，满目标由实心 + 勾表达。
            if (dot.isPartial) {
                val density = context.resources.displayMetrics.density
                val dotPx = (dotSize.value * density).toInt().coerceAtLeast(4)
                Image(
                    provider = ImageProvider(progressArcCache.get(dot.progress, dotPx)),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    colorFilter = ColorFilter.tint(habitColor),
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

/** 单行 TextView 高度（dp）：用 fontMetrics 的 top/bottom，含字体上下留白，比 fontSize 保守。 */
internal fun singleLineTextHeight(context: Context, sizeSp: Float): Dp {
    val metrics = context.resources.displayMetrics
    val paint = Paint().apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, metrics)
    }
    val fm = paint.fontMetrics
    return ((fm.bottom - fm.top) / metrics.density).dp
}

/** epochDay → 月内日（与内置 HabitDotView 一致）。 */
private fun dayOfMonthOf(epochDay: Long): Int = LocalDate.ofEpochDay(epochDay).dayOfMonth

/** 部分完成进度弧的白色位图缓存：只按「像素尺寸 + 进度百分比」区分，tint 由 Glance 负责。 */
private val progressArcCache = object : android.util.LruCache<String, Bitmap>(48) {
    fun get(progress: Float, sizePx: Int): Bitmap {
        val key = "$sizePx:${(progress.coerceIn(0f, 1f) * 1000).toInt()}"
        get(key)?.let { return it }
        val bitmap = drawProgressArc(progress, sizePx)
        put(key, bitmap)
        return bitmap
    }
}

private fun drawProgressArc(progress: Float, sizePx: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val stroke = (sizePx * 0.12f).coerceAtLeast(2f)
    val inset = stroke / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
    }
    canvas.drawArc(
        RectF(inset, inset, sizePx - inset, sizePx - inset),
        -90f,
        360f * progress.coerceIn(0f, 1f),
        false,
        paint,
    )
    return bitmap
}

/** 打开 Luno 主界面（无目标习惯，进主页）。 */
internal fun openAppIntent(context: Context): Intent = Intent().apply {
    component = ComponentName(context.packageName, HabitExtras.ACTIVITY_MAIN)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}
