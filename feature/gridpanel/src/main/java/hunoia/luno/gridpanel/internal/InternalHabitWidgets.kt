package hunoia.luno.gridpanel.internal

import android.content.ComponentName
import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hunoia.luno.config.model.HabitConfig
import hunoia.luno.config.model.HabitWidgetParams
import hunoia.luno.config.model.currentStreak
import hunoia.luno.config.model.isDueDay
import hunoia.luno.config.model.lastNDaysDots
import hunoia.luno.core.domain.JsonSerializer
import hunoia.luno.domain.gridpanel.HabitCenter
import hunoia.luno.gridpanel.HabitExtras
import hunoia.luno.gridpanel.R
import hunoia.luno.gridpanel.geometry.ItemCorner
import hunoia.luno.gridpanel.ui.gridPanelOnCardColor
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 习惯部件：打卡（HabitCheckCell）与历史（HabitHistoryCell）。
 *
 * 数据源 [HabitCenter.habitConfig]（JSON DataStore，MultiProcess），经 domain 门面订阅。
 * 打卡写回走 [HabitCenter.toggleHabit]——应用内状态变更，不走 Dispatcher（architecture §1.3）。
 * 编辑态 [enabled] = false：部件点按让给层根拖拽；历史部件非编辑态点击拉起主应用对应编辑页。
 */

/** 双击确认倒计时时长：单点出现圆环，倒计时内再点才打卡。 */
private const val HabitConfirmCountdownMs = 1200

/** 打卡部件只留圆的最小边长：小于该值不显示习惯名（如 1×1）。 */
private val HabitCompactThreshold = 80.dp

/** 解析每实例 params（JSON），失败 / 未配置返回 null。 */
internal fun parseHabitWidgetParams(params: String?): HabitWidgetParams? {
    if (params.isNullOrBlank()) return null
    return runCatching { JsonSerializer.decodeFromString<HabitWidgetParams>(params) }.getOrNull()
}

@Composable
private fun rememberToday(): Long = remember { LocalDate.now().toEpochDay() }

@Composable
internal fun HabitCheckCell(
    modifier: Modifier = Modifier,
    spanX: Int = 2,
    spanY: Int = 2,
    enabled: Boolean = true,
    params: String? = null,
) {
    val config by HabitCenter.habitConfig.collectAsState(initial = HabitConfig())
    val habit = config.habitById(parseHabitWidgetParams(params)?.habitId.orEmpty())
    val onCard = gridPanelOnCardColor
    if (habit == null) {
        EmptyHabitCell(modifier = modifier, configHasHabits = config.hasHabits)
        return
    }
    val today = rememberToday()
    val checks = config.checks[habit.id].orEmpty()
    val checked = config.isChecked(habit.id, today)
    // 打卡日 = 今天可打卡（已有记录的覆盖周期内，或尚无记录可随时开始）；
    // 跳过日 = 非 due 且今天未打：半透明主色、不可点，改历史只走编辑页。
    val dueToday = checks.isEmpty() || isDueDay(checks, today, habit.interval)
    val clickable = enabled && !checked && dueToday
    val habitColor = Color(habit.color)
    val scope = rememberCoroutineScope()

    // 双击确认：单点起主圆边缘倒计时，倒计时内再点确认；超时取消。
    var pending by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(pending) {
        if (pending) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(HabitConfirmCountdownMs))
            pending = false
        }
    }
    // 进入编辑态（enabled=false）时取消未完成的确认。
    LaunchedEffect(enabled) {
        if (!enabled) pending = false
    }

    val checkDesc = stringResource(R.string.grid_panel_habit_check_desc)
    val checkedDesc = stringResource(R.string.grid_panel_habit_checked)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(ItemCorner))
            .semantics { contentDescription = if (checked) checkedDesc else "${habit.name} $checkDesc" }
            .then(
                if (clickable) {
                    Modifier.clickable {
                        if (pending) {
                            pending = false
                            scope.launch { HabitCenter.toggleHabit(habit.id, today) }
                        } else {
                            pending = true
                        }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val side = minOf(maxWidth, maxHeight)
        val compact = side < HabitCompactThreshold
        val circleSize = (side * 0.56f).coerceIn(24.dp, 72.dp)
        val strokeWidth = (circleSize * 0.07f).coerceIn(2.dp, 4.dp)
        // 已打 = 实心主色 + 勾；打卡日（due 未打）= 空心主色描边（待打卡感）；
        // 跳过日（非 due 未打）= 半透明主色（不可点，改历史只走编辑页）。
        val fillColor = when {
            checked -> habitColor
            dueToday -> Color.Transparent
            else -> habitColor.copy(alpha = 0.3f)
        }
        val borderColor = when {
            checked || dueToday -> habitColor
            else -> habitColor.copy(alpha = 0.15f)
        }
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(circleSize)
                        .clip(CircleShape)
                        .background(fillColor)
                        .border(width = strokeWidth, color = borderColor, shape = CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (checked) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(circleSize * 0.5f),
                        )
                    }
                }
                // 倒计时画在主圆边缘（同尺寸 Canvas，不再另画大圆）。
                if (pending) {
                    val ring = strokeWidth * 0.8f
                    Canvas(Modifier.size(circleSize)) {
                        val w = ring.toPx()
                        val inset = w / 2f
                        drawArc(
                            color = habitColor,
                            startAngle = -90f,
                            sweepAngle = 360f * progress.value,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - w, size.height - w),
                            style = Stroke(width = w, cap = StrokeCap.Round),
                        )
                    }
                }
            }
            if (!compact) {
                Spacer(Modifier.height((side * 0.05f).coerceIn(4.dp, 10.dp)))
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onCard,
                    fontSize = (side.value * 0.12f).coerceIn(10f, 15f).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
internal fun HabitHistoryCell(
    modifier: Modifier = Modifier,
    spanX: Int = 2,
    spanY: Int = 2,
    enabled: Boolean = true,
    params: String? = null,
) {
    val config by HabitCenter.habitConfig.collectAsState(initial = HabitConfig())
    val habit = config.habitById(parseHabitWidgetParams(params)?.habitId.orEmpty())
    val onCard = gridPanelOnCardColor
    if (habit == null) {
        EmptyHabitCell(modifier = modifier, configHasHabits = config.hasHabits)
        return
    }
    val today = rememberToday()
    val checks = config.checks[habit.id].orEmpty()
    val streak = currentStreak(checks, today, habit.interval)
    val habitColor = Color(habit.color)
    val context = LocalContext.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(ItemCorner))
            .then(
                if (enabled) {
                    Modifier.clickable {
                        context.startActivity(
                            Intent().apply {
                                component = ComponentName(context.packageName, HabitExtras.ACTIVITY_MAIN)
                                putExtra(HabitExtras.EXTRA_EDIT_HABIT_ID, habit.id)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        // 自适应列数行数：格宽/格高按可用空间决定，内容用 weight(1f) 均分填满、不裁剪。
        val contentW = (maxWidth - 20.dp).coerceAtLeast(24.dp)
        val contentH = (maxHeight - 20.dp).coerceAtLeast(24.dp)
        val titleH = 20.dp
        val gap = 6.dp
        val gridH = (contentH - titleH - gap).coerceAtLeast(20.dp)
        val minCellW = 26.dp
        val minCellH = 30.dp
        val cols = (contentW / minCellW).toInt().coerceIn(3, 8)
        val rows = (gridH / minCellH).toInt().coerceIn(1, 4)
        val cellW = contentW / cols
        val dots = lastNDaysDots(checks, today, habit.interval, cols * rows)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
            Spacer(Modifier.height(gap))
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                for (row in 0 until rows) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        for (col in 0 until cols) {
                            val dot = dots.getOrNull(row * cols + col)
                            if (dot != null) {
                                HabitDotView(
                                    checked = dot.checked,
                                    due = dot.due,
                                    isToday = dot.day == today,
                                    color = habitColor,
                                    onCard = onCard,
                                    day = LocalDate.ofEpochDay(dot.day).dayOfMonth,
                                    cellWidth = cellW,
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
    }
}

@Composable
private fun HabitDotView(
    checked: Boolean,
    due: Boolean,
    isToday: Boolean,
    color: Color,
    onCard: Color,
    day: Int,
    cellWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val dotSize = (cellWidth * 0.46f).coerceIn(6.dp, 14.dp)
    val dateSize = (cellWidth.value * 0.30f).coerceIn(6f, 9f).sp
    // 点色四态：已打 = 实心主色；今天未打 = 空心主色描边（需打卡）；
    // 跳过（due 未打非今天）= 主色半透明；非 due = 弱灰。
    val dotColor = when {
        checked -> color
        isToday -> Color.Transparent
        due -> color.copy(alpha = 0.3f)
        else -> onCard.copy(alpha = 0.12f)
    }
    val outlined = isToday && !checked
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 日期文本在上、点在下（与近 30 天网格一致）。
        Text(
            text = day.toString(),
            fontSize = dateSize,
            color = onCard.copy(alpha = 0.5f),
            maxLines = 1,
        )
        Spacer(Modifier.height(1.dp))
        Box(
            Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor)
                .then(
                    if (outlined) {
                        Modifier.border(width = 1.5.dp, color = color, shape = CircleShape)
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

/** 空态：无习惯或指向习惯被删。面板是 overlay，不做页面跳转，仅提示。 */
@Composable
private fun EmptyHabitCell(modifier: Modifier, configHasHabits: Boolean) {
    val onCard = gridPanelOnCardColor
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(ItemCorner))
            .alpha(0.5f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(
                if (configHasHabits) R.string.grid_panel_habit_missing else R.string.grid_panel_habit_empty,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = onCard,
            textAlign = TextAlign.Center,
        )
    }
}
