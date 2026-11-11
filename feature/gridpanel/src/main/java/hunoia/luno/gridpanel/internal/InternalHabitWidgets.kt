package hunoia.luno.gridpanel.internal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hunoia.luno.config.model.HabitConfig
import hunoia.luno.config.model.HabitDayState
import hunoia.luno.config.model.HabitWidgetParams
import hunoia.luno.config.model.canCheck
import hunoia.luno.config.model.currentStreak
import hunoia.luno.config.model.lastNDaysDots
import hunoia.luno.core.domain.JsonSerializer
import hunoia.luno.domain.gridpanel.HabitCenter
import hunoia.luno.gridpanel.HabitExtras
import hunoia.luno.gridpanel.LocalGridPanelClose
import hunoia.luno.gridpanel.R
import hunoia.luno.gridpanel.geometry.ItemCorner
import hunoia.luno.gridpanel.habit.HabitDayMarkIcon
import hunoia.luno.gridpanel.habit.HabitDayPalette
import hunoia.luno.gridpanel.habit.HabitProgressArc
import hunoia.luno.gridpanel.habit.HabitTitleGap
import hunoia.luno.gridpanel.habit.borderColor
import hunoia.luno.gridpanel.habit.dotSize
import hunoia.luno.gridpanel.habit.fillColor
import hunoia.luno.gridpanel.habit.habitDayPalette
import hunoia.luno.gridpanel.habit.habitGridGeometry
import hunoia.luno.gridpanel.habit.label
import hunoia.luno.gridpanel.habit.markTint
import hunoia.luno.gridpanel.habit.visual
import hunoia.luno.gridpanel.ui.gridPanelOnCardColor
import hunoia.luno.ui.component.pressClickable
import hunoia.luno.ui.theme.HabitDayDotStroke
import hunoia.luno.ui.theme.toColor
import hunoia.luno.ui.theme.containerShape
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 习惯部件：历史（HabitHistoryCell）。
 *
 * 数据源 [HabitCenter.habitConfig]（JSON DataStore，MultiProcess），经 domain 门面订阅。
 * 打卡写回走 [HabitCenter.incrementHabit]——应用内状态变更，不走 Dispatcher（architecture §1.3）。
 * 编辑态 [enabled] = false：部件点按让给层根拖拽。
 * 交互：标题点击拉起主应用对应编辑页；点阵区点击进入打卡确认（确认态隐藏点阵、
 * 只留中央倒计时环，窗口内再点打卡）。
 */

/** 解析每实例 params（JSON），失败 / 未配置返回 null。 */
internal fun parseHabitWidgetParams(params: String?): HabitWidgetParams? {
    if (params.isNullOrBlank()) return null
    return runCatching { JsonSerializer.decodeFromString<HabitWidgetParams>(params) }.getOrNull()
}

@Composable
internal fun HabitHistoryCell(
    modifier: Modifier = Modifier,
    spanX: Int = 2,
    spanY: Int = 2,
    enabled: Boolean = true,
    params: String? = null,
    /** 指向的习惯被删 / 未配置且面板里有习惯时，点击空态 → 打开选择页原位重选。 */
    onReconfigure: (() -> Unit)? = null,
) {
    val config by HabitCenter.habitConfig.collectAsState(initial = HabitConfig())
    val habit = config.habitById(parseHabitWidgetParams(params)?.habitId.orEmpty())
    val onCard = gridPanelOnCardColor
    if (habit == null) {
        EmptyHabitCell(
            modifier = modifier,
            configHasHabits = config.hasHabits,
            enabled = enabled,
            onReconfigure = onReconfigure,
        )
        return
    }
    val today = rememberTodayEpochDay()
    val checks = config.checks[habit.id].orEmpty()
    val streak = currentStreak(checks, today, habit.interval)
    val habitColor = habit.colorSpec.toColor()
    val palette = habitDayPalette(primary = habitColor, neutral = onCard)
    val context = LocalContext.current
    // 点标题进编辑页后把面板一起关掉（镜像 InternalRecentsWidget 的 closePanel）。
    val closePanel = LocalGridPanelClose.current
    // 点阵区：今天可打卡 → 内联倒计时环确认；已打 / 跳过 → 打开编辑页（与桌面部件同口径）。
    val canCheckToday = canCheck(checks, today, habit.interval, today)
    var pending by remember { mutableStateOf(false) }
    val progress = rememberConfirmProgress(
        key = true.takeIf { pending },
        onTimeout = { pending = false },
    )
    // 进入编辑态（enabled=false）时取消未完成的确认。
    LaunchedEffect(enabled) {
        if (!enabled) pending = false
    }
    val scope = rememberCoroutineScope()
    val confirmHint = stringResource(
        if (canCheckToday) R.string.grid_panel_habit_confirm_hint else R.string.grid_panel_habit_edit_hint,
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(containerShape(ItemCorner)),
    ) {
        // 自适应列数行数：几何公式与桌面部件共用（HabitGridGeometry），只在这里扣除 10dp 内边距。
        val contentW = (maxWidth - 20.dp).coerceAtLeast(24.dp)
        val contentH = (maxHeight - 20.dp).coerceAtLeast(24.dp)
        val grid = habitGridGeometry(contentW, contentH)
        val target = habit.safeDailyTarget
        val dots = lastNDaysDots(
            checks = checks,
            today = today,
            interval = habit.interval,
            n = grid.cols * grid.rows,
            counts = config.counts[habit.id].orEmpty(),
            target = target,
        )
        // sp → dp 需要 fontScale；圆点高度限制用实际日期文本高度，避免大字体下裁剪。
        val dateTextHeight = with(LocalDensity.current) { grid.dateSizeSp.sp.toDp() }
        val dotSize = grid.dotSize(dateTextHeight)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
        ) {
            // 标题行：主色点 + 「名 · streak 天」；点击打开主应用对应编辑页（并关面板）。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (enabled) {
                            Modifier.pressClickable {
                                context.startActivity(HabitExtras.editIntent(context, habit.id))
                                closePanel()
                            }
                        } else {
                            Modifier
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(habitColor),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.grid_panel_habit_title, habit.name, streak),
                    style = MaterialTheme.typography.titleSmall,
                    color = onCard,
                    fontSize = (contentW.value * 0.11f).coerceIn(10f, 14f).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(HabitTitleGap))
            // 点阵区域：点击进入打卡确认（倒计时环浮中央）；倒计时内再点打卡今天。
            // 确认态隐藏点阵、不加遮罩背景，只留中央进度环（与桌面部件同口径）。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .semantics { contentDescription = confirmHint }
                    .then(
                        if (enabled) {
                            Modifier.pressClickable {
                                when {
                                    !canCheckToday -> {
                                        context.startActivity(HabitExtras.editIntent(context, habit.id))
                                        closePanel()
                                    }
                                    pending -> {
                                        pending = false
                                        scope.launch { HabitCenter.incrementHabit(habit.id, today) }
                                    }
                                    else -> pending = true
                                }
                            }
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (!pending) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        for (row in 0 until grid.rows) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            ) {
                                for (col in 0 until grid.cols) {
                                    val dot = dots.getOrNull(row * grid.cols + col)
                                    if (dot != null) {
                                        HabitDotView(
                                            state = dot.state,
                                            count = dot.count,
                                            target = dot.target,
                                            palette = palette,
                                            day = LocalDate.ofEpochDay(dot.day).dayOfMonth,
                                            dateSizeSp = grid.dateSizeSp,
                                            dotSize = dotSize,
                                            modifier = Modifier.weight(1f),
                                        )
                                    } else {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
                // 倒计时：隐藏点阵、不加遮罩，只留中央进度环（弱色轨道 + 主色进度弧，从 -90° 扫）。
                if (pending) {
                    val ring = (minOf(contentW, contentH) * 0.5f).coerceIn(40.dp, 64.dp)
                    val stroke = (ring.value * 0.07f).dp.coerceIn(3.dp, 5.dp)
                    Canvas(Modifier.size(ring)) {
                        drawConfirmRing(
                            progress = progress,
                            accent = habitColor,
                            track = onCard.copy(alpha = 0.25f),
                            strokeWidth = stroke.toPx(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitDotView(
    state: HabitDayState,
    count: Int,
    target: Int,
    palette: HabitDayPalette,
    day: Int,
    dateSizeSp: Float,
    dotSize: Dp,
    modifier: Modifier = Modifier,
) {
    val visual = state.visual()
    val stateLabel = state.label()
    val partial = target > 1 && count in 1 until target
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 日期文本在上、点在下（与近 30 天网格一致）。
        Text(
            text = day.toString(),
            fontSize = dateSizeSp.sp,
            lineHeight = dateSizeSp.sp,
            color = palette.neutral.copy(alpha = 0.5f),
            maxLines = 1,
        )
        Spacer(Modifier.height(1.dp))
        Box(
            Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(visual.fillColor(palette))
                .border(width = HabitDayDotStroke, color = visual.borderColor(palette), shape = CircleShape)
                .semantics { contentDescription = "$day 日 · $stateLabel" },
            contentAlignment = Alignment.Center,
        ) {
            // 点阵与打卡圆、编辑页日历共用同一套「勾 / 横杠」标记。
            HabitDayMarkIcon(
                mark = visual.mark,
                color = visual.markTint(palette),
                modifier = Modifier.size(dotSize * 0.7f),
            )
            if (partial) {
                HabitProgressArc(
                    progress = count.toFloat() / target,
                    color = palette.primary,
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = (dotSize.value * 0.12f).dp.coerceIn(1.5.dp, 3.dp),
                )
            }
        }
    }
}

/**
 * 空态：无习惯或指向习惯被删。
 *
 * 指向习惯被删但面板里还有其他习惯、且非编辑态时**可点击**：走面板层「原位重选」——
 * 打开选择页重新添加，新部件替换本项（位置不变）。无习惯可重选时仅提示。
 */
@Composable
internal fun EmptyHabitCell(
    modifier: Modifier,
    configHasHabits: Boolean,
    enabled: Boolean,
    onReconfigure: (() -> Unit)?,
) {
    val onCard = gridPanelOnCardColor
    val clickable = enabled && configHasHabits && onReconfigure != null
    val hint = stringResource(R.string.grid_panel_habit_missing_reselect)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(containerShape(ItemCorner))
            .alpha(if (clickable) 0.65f else 0.5f)
            .then(
                if (clickable) {
                    Modifier
                        .pressClickable { onReconfigure?.invoke() }
                        .semantics { contentDescription = hint }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(
                when {
                    clickable -> R.string.grid_panel_habit_missing_reselect
                    configHasHabits -> R.string.grid_panel_habit_missing
                    else -> R.string.grid_panel_habit_empty
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = onCard,
            textAlign = TextAlign.Center,
        )
    }
}
