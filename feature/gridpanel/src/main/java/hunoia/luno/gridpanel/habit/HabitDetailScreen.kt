package hunoia.luno.gridpanel.habit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.config.model.HabitConfig
import hunoia.luno.config.model.habitDayState
import hunoia.luno.domain.gridpanel.HabitCenter
import hunoia.luno.gridpanel.R
import hunoia.luno.gridpanel.internal.rememberTodayEpochDay
import hunoia.luno.ui.component.PressIconButton
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.pressClickable
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoGroupItem
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.component.sheet.ConfirmDialog
import kotlinx.coroutines.launch
import hunoia.luno.ui.theme.ContentBottom
import hunoia.luno.ui.theme.HabitCalendarCellGap
import hunoia.luno.ui.theme.HabitCalendarHeaderGap
import hunoia.luno.ui.theme.HabitCalendarPaddingVertical
import hunoia.luno.ui.theme.HabitDayCellMinHeight
import hunoia.luno.ui.theme.HabitDayCellPaddingVertical
import hunoia.luno.ui.theme.HabitDayCheckIconSize
import hunoia.luno.ui.theme.HabitDayDotSize
import hunoia.luno.ui.theme.HabitDayDotStroke
import hunoia.luno.ui.theme.HabitDayTextAlpha
import hunoia.luno.ui.theme.HabitDayTextDotGap
import hunoia.luno.ui.theme.HabitWeekdayAlpha
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SmallShape
import hunoia.luno.ui.theme.toColor
import java.time.LocalDate

/**
 * 习惯详情页：近 30 天补卡 / 改历史 + 近 90 天热力图。
 *
 * 从 [HabitEditScreen] 拆出：编辑页只剩设置字段，历史图表在这里看。
 * 设置与展示彻底分开（顶栏右上编辑 = 编辑页）。
 * 数据源 [HabitCenter.habitConfig]；补卡 / 改历史写回走 domain 门面。
 * 习惯被删除（含编辑页删除后返回）时自动退出；一开始就找不到（部件 / 通知指向已删条目）
 * 则显示「习惯已删除」而不是闪退（同 `TrendDetailScreen` 口径）。
 */
@Composable
fun HabitDetailScreen(
    habitId: String,
    onBack: () -> Unit,
    onEdit: (habitId: String) -> Unit,
) {
    val config by HabitCenter.habitConfig.collectAsStateWithLifecycle(initialValue = HabitCenter.latestHabitConfig())
    val today = rememberTodayEpochDay()
    val habit = config.habitById(habitId)
    val scope = rememberCoroutineScope()
    // 补卡 / 改历史确认：同一时刻最多一个弹层（ui-guidelines §3.2-5）。
    var pendingDay by remember { mutableStateOf<Pair<Long, Boolean>?>(null) }
    // 首帧 config 可能还没真值（冷启动兜底帧）→ habit=null，不能据此退页。
    // 单通道判定：先从持久层读一次确认「这条存在过」，之后跟着配置流走 ——
    // 流里出现过这条就 found；一旦消失（打开期间被删）且 found 就退页。
    var found by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        found = HabitCenter.getHabitConfig().habitById(habitId) != null
        HabitCenter.habitConfig.collect { cfg ->
            if (cfg.habitById(habitId) != null) {
                found = true
            } else if (found == true) {
                onBack()
            }
        }
    }

    if (habit == null) {
        // 还没读到配置 / 记录确实不存在：都给一个可读结局（不留空白页、不闪退）。
        LunoScreen(
            title = stringResource(R.string.habit_list_title),
            onBack = onBack,
            bottomContentPadding = ContentBottom,
        ) {
            EmptyState(message = stringResource(R.string.grid_panel_habit_missing))
        }
        return
    }

    LunoScreen(
        title = habit.name,
        onBack = onBack,
        bottomContentPadding = ContentBottom,
        actions = {
            PressIconButton(onClick = { onEdit(habit.id) }) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.habit_edit_title),
                )
            }
        },
        dialogs = {
            pendingDay?.let { (day, targetChecked) ->
                val date = LocalDate.ofEpochDay(day)
                ConfirmDialog(
                    title = stringResource(
                        if (targetChecked) {
                            R.string.habit_check_confirm_title
                        } else {
                            R.string.habit_uncheck_confirm_title
                        },
                    ),
                    text = stringResource(
                        if (targetChecked) {
                            R.string.habit_check_confirm_text
                        } else {
                            R.string.habit_uncheck_confirm_text
                        },
                        date.monthValue,
                        date.dayOfMonth,
                    ),
                    confirmLabel = stringResource(
                        if (targetChecked) {
                            R.string.habit_check_confirm_action
                        } else {
                            R.string.habit_uncheck_confirm_action
                        },
                    ),
                    destructive = !targetChecked,
                    onDismissRequest = { pendingDay = null },
                    onConfirm = {
                        scope.launch { HabitCenter.setHabitCheck(habitId, day, targetChecked) }
                        pendingDay = null
                    },
                )
            }
        },
    ) {
        RecentChecksGrid(
            habitId = habit.id,
            color = habit.colorSpec.toColor(),
            config = config,
            today = today,
            interval = habit.interval,
            target = habit.safeDailyTarget,
            onDayClick = { day, targetChecked -> pendingDay = day to targetChecked },
        )
        HabitHeatmapCard(
            habitId = habit.id,
            color = habit.colorSpec.toColor(),
            config = config,
            today = today,
            target = habit.safeDailyTarget,
        )
    }
}

/**
 * 近 90 天热力图：纯展示，不响应点击；颜色 / 目标用当前配置。
 *
 * 与面板热力图部件共用 [HabitHeatmapGrid] 与同一套几何 / 分档；这里按可用宽度铺满
 * （最多 13 列），窄屏自动减列，不做横向滚动。
 */
@Composable
private fun HabitHeatmapCard(
    habitId: String,
    color: androidx.compose.ui.graphics.Color,
    config: HabitConfig,
    today: Long,
    target: Int,
) {
    val description = stringResource(R.string.habit_heatmap_desc)
    LunoGroup(title = stringResource(R.string.habit_heatmap)) {
        LunoGroupItem(shape = segmentedShape(0, 1)) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter, vertical = HabitCalendarPaddingVertical),
                contentAlignment = Alignment.Center,
            ) {
                HabitHeatmapGrid(
                    geometry = habitHeatmapGeometryFillWidth(maxWidth),
                    today = today,
                    checks = config.checks[habitId].orEmpty(),
                    counts = config.counts[habitId].orEmpty(),
                    target = target,
                    habitColor = color,
                    baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    modifier = Modifier.semantics { contentDescription = description },
                )
            }
        }
    }
}

/** 近 30 天补 / 改打卡：标准日历网格（星期头固定一~日，today 落真实星期列、前导空格对齐首日）。 */
@Composable
private fun RecentChecksGrid(
    habitId: String,
    color: androidx.compose.ui.graphics.Color,
    config: HabitConfig,
    today: Long,
    interval: Int,
    target: Int,
    onDayClick: (day: Long, targetChecked: Boolean) -> Unit,
) {
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    val palette = habitDayPalette(primary = color, neutral = outline)
    val checks = config.checks[habitId].orEmpty()
    val weekdays = stringArrayResource(R.array.habit_weekdays)
    // 标准日历：today 在末行、其真实星期列（一=0…日=6）；窗口 today-29..today，
    // today 右侧（未来）与窗口前（前导对齐）留空。
    val todayPos = 4 * 7 + (LocalDate.ofEpochDay(today).dayOfWeek.value - 1)
    LunoGroup(title = stringResource(R.string.habit_recent_checks)) {
        LunoGroupItem(shape = segmentedShape(0, 1)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter, vertical = HabitCalendarPaddingVertical),
            ) {
                // 顶部星期单字（一~日），与下方列对齐。
                Row(modifier = Modifier.fillMaxWidth()) {
                    weekdays.forEach { weekday ->
                        Text(
                            text = weekday,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = outline.copy(alpha = HabitWeekdayAlpha),
                        )
                    }
                }
                Spacer(Modifier.height(HabitCalendarHeaderGap))
                // 35 槽：pos = row*7+col；dayOffset = todayPos-pos（0=today，>0=过去，<0=未来）。
                for (row in 0 until 5) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(HabitCalendarCellGap),
                    ) {
                        for (col in 0 until 7) {
                            val dayOffset = todayPos - (row * 7 + col)
                            if (dayOffset < 0 || dayOffset > 29) {
                                Spacer(
                                    Modifier
                                        .weight(1f)
                                        .heightIn(min = HabitDayCellMinHeight),
                                )
                            } else {
                                val day = today - dayOffset
                                val state = habitDayState(checks, day, interval, today)
                                val checked = config.isChecked(habitId, day)
                                val count = config.countOf(habitId, day, target)
                                val date = LocalDate.ofEpochDay(day)
                                DayDotCell(
                                    month = date.monthValue,
                                    day = date.dayOfMonth,
                                    visual = state.visual(),
                                    stateLabel = state.label(),
                                    isToday = dayOffset == 0,
                                    palette = palette,
                                    count = count,
                                    target = target,
                                    onClick = { onDayClick(day, !checked) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 日历格子：视觉语义由 [HabitDayVisual] 统一派生，与面板部件的四态口径一致
 * （跳过日 = 半透明主色 + 横杠）。多次习惯在圆点内画进度弧；点击只发事件，
 * 写盘在确认弹层之后。
 */
@Composable
private fun DayDotCell(
    month: Int,
    day: Int,
    visual: HabitDayVisual,
    stateLabel: String,
    isToday: Boolean,
    palette: HabitDayPalette,
    count: Int,
    target: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val cellDesc = stringResource(R.string.habit_day_cell_desc, month, day, stateLabel)
    Column(
        modifier = modifier
            // heightIn 而非固定 height：fontScale 1.4 / 2.0 下文字可长高、不裁切（ui-guidelines §1.5）。
            .heightIn(min = HabitDayCellMinHeight)
            .clip(SmallShape)
            .then(
                if (isToday) {
                    Modifier.border(HabitDayDotStroke, palette.primary, SmallShape)
                } else {
                    Modifier
                },
            )
            .pressClickable(onClick = onClick)
            .semantics { contentDescription = cellDesc }
            .padding(vertical = HabitDayCellPaddingVertical),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) palette.primary else palette.neutral.copy(alpha = HabitDayTextAlpha),
        )
        Spacer(Modifier.height(HabitDayTextDotGap))
        Box(
            modifier = Modifier
                .size(HabitDayDotSize)
                .clip(CircleShape)
                .background(visual.fillColor(palette))
                .border(HabitDayDotStroke, visual.borderColor(palette), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            HabitDayMarkIcon(
                mark = visual.mark,
                color = visual.markTint(palette),
                modifier = Modifier.size(HabitDayCheckIconSize),
            )
            // 多次习惯的部分完成：与面板点阵 / 桌面部件同一套进度弧语义。
            if (target > 1 && count in 1 until target) {
                HabitProgressArc(
                    progress = count.toFloat() / target,
                    color = palette.primary,
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}
