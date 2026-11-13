package hunoia.luno.gridpanel.habit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.config.model.HabitConfig
import hunoia.luno.config.model.currentStreak
import hunoia.luno.domain.gridpanel.HabitCenter
import hunoia.luno.gridpanel.R
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.toColor
import java.time.LocalDate

/**
 * 习惯列表内容：主页「习惯」tab 嵌入用。
 *
 * 共享主页外壳的顶栏，没有自己的顶栏 / 返回键 / FAB；新建入口是底栏的功能球，
 * 由调用方接 [onEditHabit]（传 null = 新建）。
 * 数据源 [HabitCenter.habitConfig]；增删改走 domain 门面（配置写入，不走 Action）。
 */
@Composable
fun HabitListContent(
    topPadding: Dp,
    onEditHabit: (habitId: String?) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
) {
    val config by HabitCenter.habitConfig.collectAsStateWithLifecycle(initialValue = HabitConfig())
    val today = LocalDate.now().toEpochDay()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(
            start = PageGutter,
            top = topPadding,
            end = PageGutter,
            bottom = FloatingContentBottom,
        ),
        verticalArrangement = Arrangement.spacedBy(ListSpacing),
    ) {
        if (config.habits.isEmpty()) {
            item {
                EmptyState(message = stringResource(R.string.habit_empty))
            }
        } else {
            item {
                LunoGroup(title = stringResource(R.string.action_settings_habit_group)) {
                    config.habits.forEachIndexed { index, habit ->
                        val checks = config.checks[habit.id].orEmpty()
                        val streak = currentStreak(checks, today, habit.interval)
                        val target = habit.safeDailyTarget
                        val subtitle = if (target > 1) {
                            stringResource(
                                R.string.habit_today_progress,
                                config.countOf(habit.id, today, target),
                                target,
                                streak,
                            )
                        } else {
                            stringResource(R.string.habit_streak_summary, streak)
                        }
                        LunoSettingsRow(
                            title = habit.name,
                            subtitle = subtitle,
                            leading = {
                                Box(
                                    Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(habit.colorSpec.toColor()),
                                )
                            },
                            shape = segmentedShape(index, config.habits.size),
                            onClick = { onEditHabit(habit.id) },
                        )
                    }
                }
            }
        }
    }
}
