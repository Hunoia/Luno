package hunoia.luno.gridpanel.habit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.view.View
import hunoia.luno.bridge.ui.hapticTick
import hunoia.luno.config.model.HabitDayState
import hunoia.luno.config.model.HabitDot
import hunoia.luno.domain.gridpanel.HabitCenter
import hunoia.luno.gridpanel.R
import hunoia.luno.gridpanel.internal.rememberTodayEpochDay
import hunoia.luno.ui.component.PressTextButton
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.page.LunoPageList
import hunoia.luno.ui.component.pressClickable
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoGroupItem
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.theme.HabitDayCheckIconSize
import hunoia.luno.ui.theme.HabitDayDotStroke
import hunoia.luno.ui.theme.HabitTodayDotGap
import hunoia.luno.ui.theme.HabitTodayDotMarkSize
import hunoia.luno.ui.theme.HabitTodayDotSize
import hunoia.luno.ui.theme.MarkColorSize
import hunoia.luno.ui.theme.MinInteractiveSize
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.cardSurfaceColor
import hunoia.luno.ui.theme.toColor
import kotlinx.coroutines.launch

/**
 * 习惯执行面：主页「习惯」tab 嵌入用。
 *
 * 共享主页外壳的顶栏，没有自己的顶栏 / 返回键 / FAB；
 * 新建入口是底栏的功能球，空态里的按钮走同一个入口（[onNewHabit]）。
 * 数据源 [HabitCenter.habitConfig]；打卡 / 撤销走 domain 门面（配置写入，不走 Action）。
 *
 * 这里只做**今天**的状态展示与打卡；行点按 = [HabitDetailScreen]（历史补卡 / 热力图，
 * 编辑入口在详情页顶栏）。三段分组：今日待打卡 / 今日已完成 / 今日无需打卡（跳过）；
 * 组内保持配置顺序。打卡反馈：单次习惯直接写回 + 振动；多次习惯 +1 与撤销（破坏性）走确认弹层。
 */
@Composable
fun HabitListContent(
    onOpenHabit: (habitId: String) -> Unit,
    onNewHabit: () -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
) {
    val config by HabitCenter.habitConfig.collectAsStateWithLifecycle(initialValue = HabitCenter.latestHabitConfig())
    val today = rememberTodayEpochDay()
    val scope = rememberCoroutineScope()
    // 打卡（target=1）不需要确认：直接写回，靠振动反馈防误触；只有撤销（清 streak，破坏性）
    // 和多次习惯的 +1（一次点击多加一格进度）仍走确认弹层（ui-guidelines §3.2-5）。
    var pending by remember { mutableStateOf<PendingHabitCheck?>(null) }
    val view = LocalView.current
    // 分组是纯函数（[groupHabitsToday]）：只在配置 / 日期变化时重算，重组不重建临时列表。
    val groups = remember(config, today) { groupHabitsToday(config, today) }

    LunoPageList(
        modifier = modifier.fillMaxSize(),
        state = state,
    ) {
        lazy {
        if (config.habits.isEmpty()) {
            item {
                EmptyState(
                    message = stringResource(R.string.habit_empty),
                    action = {
                        PressTextButton(onClick = onNewHabit) {
                            Text(stringResource(R.string.habit_add))
                        }
                    },
                )
            }
        } else {
            habitGroupItem(R.string.habit_group_today_due, groups.due, onOpenHabit, view) {
                if (it.checked && it.target == 1) {
                    // 单次习惯直接打卡：振动 + 状态行变化即反馈，不走弹层。
                    view.hapticTick()
                    scope.launch { HabitCenter.incrementHabit(it.habitId, today) }
                } else {
                    pending = it
                }
            }
            habitGroupItem(R.string.habit_group_today_done, groups.done, onOpenHabit, view) {
                pending = it
            }
            habitGroupItem(R.string.habit_group_today_skipped, groups.skipped, onOpenHabit, view) {
                pending = it
            }
        }
        }
    }

    pending?.let { action ->
        ConfirmDialog(
            title = stringResource(
                if (action.checked) {
                    R.string.habit_today_check_confirm_title
                } else {
                    R.string.habit_today_uncheck_confirm_title
                },
            ),
            text = stringResource(
                if (action.checked) {
                    R.string.habit_today_check_confirm_text
                } else {
                    R.string.habit_today_uncheck_confirm_text
                },
                action.name,
                action.nextCount,
                action.target,
            ),
            confirmLabel = stringResource(
                if (action.checked) {
                    R.string.habit_today_check_confirm_action
                } else {
                    R.string.habit_today_uncheck_confirm_action
                },
            ),
            // 撤销今天的打卡是破坏性（清除进度，连续天数可能变化）。
            destructive = !action.checked,
            onDismissRequest = { pending = null },
            onConfirm = {
                if (action.checked) {
                    scope.launch { HabitCenter.incrementHabit(action.habitId, today) }
                } else {
                    scope.launch { HabitCenter.setHabitCheck(action.habitId, today, false) }
                }
                pending = null
            },
        )
    }
}

/** 一次打卡 / 撤销的确认上下文：弹层文案从这里派生，写完由 [HabitListContent] 清空。 */
private data class PendingHabitCheck(
    val habitId: String,
    val name: String,
    val checked: Boolean,
    /** 打卡后要达到的次数（展示用）；撤销时不使用。 */
    val nextCount: Int,
    val target: Int,
)

@Composable
private fun habitStatusText(row: HabitToday): String = when {
    row.checkedToday -> if (row.target > 1) {
        stringResource(R.string.habit_status_progress, row.count, row.target, row.streak)
    } else {
        stringResource(R.string.habit_status_done, row.streak)
    }
    row.state == HabitDayState.SKIPPED -> stringResource(R.string.habit_status_skipped, row.streak)
    row.state == HabitDayState.OVERDUE -> stringResource(R.string.habit_status_overdue, row.streak)
    else -> stringResource(R.string.habit_status_due, row.streak)
}

private fun androidx.compose.foundation.lazy.LazyListScope.habitGroupItem(
    titleRes: Int,
    rows: List<HabitToday>,
    onOpenHabit: (String) -> Unit,
    view: View,
    onCheck: (PendingHabitCheck) -> Unit,
) {
    if (rows.isEmpty()) return
    item {
        LunoGroup(title = stringResource(titleRes)) {
            rows.forEachIndexed { index, row ->
                HabitTodayRow(
                    row = row,
                    subtitle = habitStatusText(row),
                    shape = segmentedShape(index, rows.size),
                    onOpen = { onOpenHabit(row.habitId) },
                    onCheck = { onCheck(row.toPendingCheck()) },
                )
            }
        }
    }
}

private fun HabitToday.toPendingCheck(): PendingHabitCheck = PendingHabitCheck(
    habitId = habitId,
    name = name,
    checked = !checkedToday,
    nextCount = nextCount,
    target = target,
)

/**
 * 今日习惯行：色点 + 名称 / 状态副标题 / 近 7 天点阵 + 右侧打卡按钮。
 * 行点按 = 详情页；打卡按钮点按 = 确认弹层（每次 +1 / 撤销今天）。
 */
@Composable
private fun HabitTodayRow(
    row: HabitToday,
    subtitle: String,
    onOpen: () -> Unit,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.small,
) {
    LunoGroupItem(
        shape = shape,
        // 分割线左起点越过色点，对齐文字列（16 边距 + 色点 16 + 间距 12）。
        dividerStartInset = PageGutter + MarkColorSize + 12.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinInteractiveSize)
                .pressClickable(onClick = onOpen)
                .padding(horizontal = PageGutter, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(MarkColorSize)
                    .clip(CircleShape)
                    .background(row.habit.colorSpec.toColor()),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.heightIn(min = 6.dp))
                HabitTodayDots(
                    dots = row.dots,
                    primary = row.habit.colorSpec.toColor(),
                )
            }
            Spacer(Modifier.width(8.dp))
            // 跳过日今天不用打卡：不画按钮（避免一个无效的空圈）。
            if (row.state != HabitDayState.SKIPPED) {
                HabitCheckButton(
                    row = row,
                    onClick = onCheck,
                )
            }
        }
    }
}

/** 近 7 天点阵：纯展示，不可点（避免 7 个命中区挤在行尾）。 */
@Composable
private fun HabitTodayDots(
    dots: List<HabitDot>,
    primary: Color,
) {
    val palette = habitDayPalette(primary = primary, neutral = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(
        horizontalArrangement = Arrangement.spacedBy(HabitTodayDotGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dots.forEach { dot ->
            val visual = dot.state.visual()
            Box(
                modifier = Modifier
                    .size(HabitTodayDotSize)
                    .clip(CircleShape)
                    .background(visual.fillColor(palette))
                    .border(HabitDayDotStroke, visual.borderColor(palette), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                HabitDayMarkIcon(
                    mark = visual.mark,
                    color = visual.markTint(palette),
                    modifier = Modifier.size(HabitTodayDotMarkSize),
                )
                // 多次习惯的部分完成：进度弧语义与日历格 / 面板点阵同源（HabitDot.progress）。
                if (dot.isPartial) {
                    HabitProgressArc(
                        progress = dot.progress,
                        color = primary,
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = HabitDayDotStroke,
                    )
                }
            }
        }
    }
}

/**
 * 打卡按钮：48dp 命中区。已达标 = 实心主色勾（再点 = 撤销今天）；
 * 未达标 = 空心描边（进行中画进度弧）。
 */
@Composable
private fun HabitCheckButton(
    row: HabitToday,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val circle = 24.dp
    val clickLabel = stringResource(
        if (row.checkedToday) {
            R.string.habit_today_uncheck_confirm_title
        } else {
            R.string.habit_today_check_confirm_title
        },
    )
    Box(
        modifier = modifier
            .size(MinInteractiveSize)
            .clip(CircleShape)
            .pressClickable(
                // 已达标也能点（撤销今天）；跳过日不画按钮（见调用处）。
                enabled = row.state != HabitDayState.SKIPPED,
                onClickLabel = clickLabel,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (row.checkedToday) {
            Box(
                Modifier
                    .size(circle)
                    .clip(CircleShape)
                    .background(scheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(HabitDayCheckIconSize),
                )
            }
        } else {
            Box(
                Modifier
                    .size(circle)
                    .clip(CircleShape)
                    .background(cardSurfaceColor())
                    .border(2.dp, scheme.primary.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (row.isPartial) {
                    HabitProgressArc(
                        progress = row.progress,
                        color = scheme.primary,
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}
