package hunoia.luno.gridpanel.internal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hunoia.luno.config.model.HabitConfig
import hunoia.luno.config.model.canCheck
import hunoia.luno.config.model.currentStreak
import hunoia.luno.domain.gridpanel.HabitCenter
import hunoia.luno.gridpanel.HabitExtras
import hunoia.luno.gridpanel.LocalGridPanelClose
import hunoia.luno.gridpanel.R
import hunoia.luno.gridpanel.geometry.ItemCorner
import hunoia.luno.gridpanel.habit.HabitHeatmapGrid
import hunoia.luno.gridpanel.habit.HabitTitleGap
import hunoia.luno.gridpanel.habit.HabitTitleHeight
import hunoia.luno.gridpanel.habit.habitHeatmapGeometry
import hunoia.luno.gridpanel.ui.gridPanelOnCardColor
import hunoia.luno.ui.theme.containerShape
import hunoia.luno.ui.theme.toColor
import kotlinx.coroutines.launch

/**
 * 习惯部件：热力图（HabitHeatmapCell）。
 *
 * 与桌面 Glance 热力图部件同口径：单个习惯的近 90 天，7 行 × 自适应列数（最多 13 列），
 * 底层所有有效日淡色、上层按完成度 1–5 档叠习惯色；几何 / 分档共用
 * [hunoia.luno.gridpanel.habit.HabitHeatmapGeometry]（architecture §7.5）。
 *
 * 数据源 [HabitCenter.habitConfig]；打卡写回走 [HabitCenter.incrementHabit]（应用内状态变更，
 * 不走 Dispatcher）。交互与点阵部件一致：标题 → 编辑页；网格区今天可打卡 → 内联倒计时环
 * 确认（确认态隐藏热力图、只留中央环，窗口内再点 +1），否则 → 编辑页。
 * 编辑态 [enabled] = false：点按让给层根拖拽。
 */
@Composable
internal fun HabitHeatmapCell(
    modifier: Modifier = Modifier,
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
    val counts = config.counts[habit.id].orEmpty()
    val target = habit.safeDailyTarget
    val streak = currentStreak(checks, today, habit.interval)
    val habitColor = habit.colorSpec.toColor()
    val context = LocalContext.current
    val closePanel = LocalGridPanelClose.current
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
        // 与点阵部件一致：扣 10dp 内边距，再扣固定标题行高得到网格区。
        val contentW = (maxWidth - 20.dp).coerceAtLeast(24.dp)
        val contentH = (maxHeight - 20.dp).coerceAtLeast(24.dp)
        val gridHeight = (contentH - HabitTitleHeight - HabitTitleGap).coerceAtLeast(16.dp)
        val geometry = habitHeatmapGeometry(contentW, gridHeight)

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
                            Modifier.clickable {
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .semantics { contentDescription = confirmHint }
                    .then(
                        if (enabled) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
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
                    HabitHeatmapGrid(
                        geometry = geometry,
                        today = today,
                        checks = checks,
                        counts = counts,
                        target = target,
                        habitColor = habitColor,
                        baseColor = onCard.copy(alpha = 0.12f),
                    )
                }
                // 倒计时：隐藏热力图、不加遮罩，只留中央进度环（与点阵部件同口径）。
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


