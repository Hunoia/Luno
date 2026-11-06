package hunoia.luno.gridpanel.habit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.gridpanel.R
import hunoia.luno.config.model.HabitConfig
import hunoia.luno.config.model.currentStreak
import hunoia.luno.domain.gridpanel.HabitCenter
import hunoia.luno.ui.component.LunoFab
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.toColor
import java.time.LocalDate

/**
 * 习惯管理列表：主设置 → 习惯。
 * 数据源 [HabitCenter.habitConfig]；增删改走 domain 门面（配置写入，不走 Action）。
 */
@Composable
fun HabitListScreen(
    onBack: () -> Unit,
    onEditHabit: (habitId: String?) -> Unit,
) {
    val config by HabitCenter.habitConfig.collectAsStateWithLifecycle(initialValue = HabitConfig())
    val today = LocalDate.now().toEpochDay()
    LunoScreenLazy(
        title = stringResource(R.string.habit_list_title),
        onBack = onBack,
        bottomContentPadding = FloatingContentBottom,
        floatingContent = {
            LunoFab(
                onClick = { onEditHabit(null) },
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.habit_add),
            )
        },
    ) {
        if (config.habits.isEmpty()) {
            item {
                EmptyState(message = stringResource(R.string.habit_empty))
            }
        } else {
            item {
                LunoGroup(title = stringResource(R.string.action_settings_habit_group)) {
                    config.habits.forEachIndexed { index, habit ->
                        val streak = currentStreak(
                            config.checks[habit.id].orEmpty(),
                            today,
                            habit.interval,
                        )
                        LunoSettingsRow(
                            title = habit.name,
                            subtitle = stringResource(R.string.habit_streak_summary, streak),
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
