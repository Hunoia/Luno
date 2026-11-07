package hunoia.luno.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import hunoia.luno.config.model.ColorSource
import hunoia.luno.config.model.Habit
import hunoia.luno.config.model.HabitConfig
import hunoia.luno.config.model.ThemeColorKey
import hunoia.luno.core.LunoLog
import hunoia.luno.domain.gridpanel.HabitCenter
import kotlinx.coroutines.launch

private const val TAG = "HabitWidgetConfig"

/**
 * 习惯部件放置时的配置**弹窗**（appwidget-provider `android:configure`）：
 * 半透明小窗列出全部习惯，选中即写入该实例的 Glance state 并完成放置；
 * 无习惯时给提示。样式为独立浮动窗口（Material 列表卡），非应用内页面弹层。
 */
class HabitWidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        setContent {
            MaterialTheme {
                HabitPickerDialog(
                    appWidgetId = appWidgetId,
                    onSelect = { habitId -> commit(habitId, appWidgetId) },
                )
            }
        }
    }

    /** 写 habitId 到该 widget 实例状态，返回结果给宿主。 */
    private fun commit(habitId: String, appWidgetId: Int) {
        val context = applicationContext
        lifecycleScope.launch {
            // Glance 1.2 的 AppWidgetId 构造是 @RestrictedApi，用 manager 取 GlanceId。
            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(context, glanceId) {
                it[WidgetStateKeys.HABIT_ID] = habitId
                it[WidgetStateKeys.HABIT_PENDING] = false
            }
            // 写后直更：不依赖 WidgetSync 订阅者（与待办弹窗同一策略）。
            runCatching { HabitWidget().updateAll(context) }
                .onFailure { LunoLog.w(TAG, "updateAll after config failed: ${it.message}") }
            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
            )
            finish()
        }
    }
}

@Composable
private fun HabitPickerDialog(appWidgetId: Int, onSelect: (String) -> Unit) {
    val config by HabitCenter.habitConfig.collectAsState(initial = HabitConfig())
    val title = stringResource(R.string.widget_habit_picker_title)
    val empty = stringResource(R.string.widget_habit_picker_empty)
    val unnamed = stringResource(R.string.widget_habit_picker_unnamed)
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 360.dp)
                    .padding(24.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(modifier = Modifier.padding(vertical = 20.dp)) {
                    Text(
                        text = title,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (config.habits.isEmpty()) {
                        Text(
                            text = empty,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                            items(config.habits, key = { it.id }) { habit ->
                                HabitRow(
                                    habit = habit,
                                    unnamed = unnamed,
                                    onClick = { onSelect(habit.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitRow(habit: Habit, unnamed: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(habitColor(habit)),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = habit.name.ifBlank { unnamed },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 习惯色解析：镜像 ui/design `ColorSpec.toColor`（widget 不依赖 ui:design，主题角色映射 MaterialTheme）。 */
@Composable
private fun habitColor(habit: Habit): Color = when (habit.colorSpec.source) {
    ColorSource.Custom -> Color(habit.colorSpec.customColor)
    ColorSource.Theme -> themeColor(habit.colorSpec.themeKey)
}

@Composable
private fun themeColor(key: ThemeColorKey): Color = when (key) {
    ThemeColorKey.Primary -> MaterialTheme.colorScheme.primary
    ThemeColorKey.PrimaryContainer -> MaterialTheme.colorScheme.primaryContainer
    ThemeColorKey.Secondary -> MaterialTheme.colorScheme.secondary
    ThemeColorKey.SecondaryContainer -> MaterialTheme.colorScheme.secondaryContainer
    ThemeColorKey.Tertiary -> MaterialTheme.colorScheme.tertiary
    ThemeColorKey.TertiaryContainer -> MaterialTheme.colorScheme.tertiaryContainer
    ThemeColorKey.Surface -> MaterialTheme.colorScheme.surface
    ThemeColorKey.SurfaceVariant -> MaterialTheme.colorScheme.surfaceVariant
    ThemeColorKey.OnSurface -> MaterialTheme.colorScheme.onSurface
    ThemeColorKey.OnSurfaceVariant -> MaterialTheme.colorScheme.onSurfaceVariant
    ThemeColorKey.Outline -> MaterialTheme.colorScheme.outline
    ThemeColorKey.OutlineVariant -> MaterialTheme.colorScheme.outlineVariant
    ThemeColorKey.SurfaceContainerLow -> MaterialTheme.colorScheme.surfaceContainerLow
    ThemeColorKey.SurfaceContainer -> MaterialTheme.colorScheme.surfaceContainer
    ThemeColorKey.SurfaceContainerHigh -> MaterialTheme.colorScheme.surfaceContainerHigh
}
