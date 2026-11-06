package hunoia.luno.gridpanel.internal

import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import hunoia.luno.config.model.habitDayState
import hunoia.luno.config.model.lastNDaysDots
import hunoia.luno.core.domain.JsonSerializer
import hunoia.luno.domain.gridpanel.HabitCenter
import hunoia.luno.gridpanel.HabitExtras
import hunoia.luno.gridpanel.LocalGridPanelClose
import hunoia.luno.gridpanel.PanelOpenTapGuardMillis
import hunoia.luno.gridpanel.R
import hunoia.luno.gridpanel.geometry.ItemCorner
import hunoia.luno.gridpanel.habit.HabitDayMarkIcon
import hunoia.luno.gridpanel.habit.HabitDayPalette
import hunoia.luno.gridpanel.habit.borderColor
import hunoia.luno.gridpanel.habit.fillColor
import hunoia.luno.gridpanel.habit.habitDayPalette
import hunoia.luno.gridpanel.habit.label
import hunoia.luno.gridpanel.habit.markTint
import hunoia.luno.gridpanel.habit.visual
import hunoia.luno.gridpanel.ui.gridPanelOnCardColor
import hunoia.luno.ui.theme.HabitDayDotStroke
import hunoia.luno.ui.theme.toColor
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 习惯部件：打卡（HabitCheckCell）与历史（HabitHistoryCell）。
 *
 * 数据源 [HabitCenter.habitConfig]（JSON DataStore，MultiProcess），经 domain 门面订阅。
 * 打卡写回走 [HabitCenter.toggleHabit]——应用内状态变更，不走 Dispatcher（architecture §1.3）。
 * 编辑态 [enabled] = false：部件点按让给层根拖拽；历史部件非编辑态点击拉起主应用对应编辑页。
 */

/** 打卡部件只留圆的最小边长：小于该值不显示习惯名（如 1×1）。 */
private val HabitCompactThreshold = 80.dp

/** 解析每实例 params（JSON），失败 / 未配置返回 null。 */
internal fun parseHabitWidgetParams(params: String?): HabitWidgetParams? {
    if (params.isNullOrBlank()) return null
    return runCatching { JsonSerializer.decodeFromString<HabitWidgetParams>(params) }.getOrNull()
}

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
    val today = rememberTodayEpochDay()
    val checks = config.checks[habit.id].orEmpty()
    val state = habitDayState(checks, today, habit.interval, today)
    // 尚未开始：部件允许随时开始，视觉与「待打卡」一致；编辑页日历仍显示未开始。
    val shownState = if (state == HabitDayState.INACTIVE) HabitDayState.DUE else state
    // 跳过日不可提前打卡，已打不可重复；逾期（补打）与未开始都仍可点。
    val clickable = enabled && canCheck(checks, today, habit.interval, today)
    val habitColor = habit.colorSpec.toColor()
    val palette = habitDayPalette(primary = habitColor, neutral = onCard)
    val visual = shownState.visual()
    val fillColor = visual.fillColor(palette)
    val borderColor = visual.borderColor(palette)
    val stateLabel = shownState.label()
    val confirmHint = stringResource(R.string.grid_panel_habit_confirm_hint)
    val scope = rememberCoroutineScope()

    // 双击确认：单点起主圆边缘倒计时，倒计时内再点确认；超时取消。
    var pending by remember { mutableStateOf(false) }
    val progress = rememberConfirmProgress(
        key = true.takeIf { pending },
        onTimeout = { pending = false },
    )
    // 进入编辑态（enabled=false）时取消未完成的确认。
    LaunchedEffect(enabled) {
        if (!enabled) pending = false
    }

    val cellDesc = stringResource(
        R.string.grid_panel_habit_state_desc,
        habit.name,
        if (pending) confirmHint else stateLabel,
    )
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(ItemCorner))
            .semantics { contentDescription = cellDesc }
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
        // 视觉语义由 shownState.visual() 统一派生（HabitDayVisual.kt）：
        // 已打 = 实心 + 勾；待打 = 空心描边；跳过 = 半透明主色 + 横杠；断签 = 弱灰 + 错误色描边。
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
                        .then(
                            // 倒计时时无静态描边：主圆描边由 Canvas 进度环代替（主圆变倒计时）。
                            if (pending) {
                                Modifier
                            } else {
                                Modifier.border(width = strokeWidth, color = borderColor, shape = CircleShape)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    HabitDayMarkIcon(
                        mark = visual.mark,
                        color = visual.markTint(palette),
                        modifier = Modifier.size(circleSize * 0.5f),
                    )
                }
                // 倒计时 = 主圆描边本身：弱色轨道 + 主色进度弧（从 -90° 扫），不另叠一层。
                if (pending) {
                    val ring = strokeWidth
                    Canvas(Modifier.size(circleSize)) {
                        val w = ring.toPx()
                        val inset = w / 2f
                        drawArc(
                            color = onCard.copy(alpha = 0.25f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - w, size.height - w),
                            style = Stroke(width = w, cap = StrokeCap.Round),
                        )
                        drawArc(
                            color = habitColor,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
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
    val today = rememberTodayEpochDay()
    val checks = config.checks[habit.id].orEmpty()
    val streak = currentStreak(checks, today, habit.interval)
    val habitColor = habit.colorSpec.toColor()
    val palette = habitDayPalette(primary = habitColor, neutral = onCard)
    val context = LocalContext.current
    // 部件组合创建 = 面板本次出场：打开瞬间的穿透抬手不算一次点击。
    // 跳编辑页是跨进程 startActivity，副作用最重，除了面板根的指针抑制再挡一层。
    val openedAt = remember { SystemClock.elapsedRealtime() }
    // 点条目进编辑页后把面板一起关掉（镜像 InternalRecentsWidget 的 closePanel）。
    val closePanel = LocalGridPanelClose.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(ItemCorner))
            .then(
                if (enabled) {
                    Modifier.clickable {
                        if (SystemClock.elapsedRealtime() - openedAt < PanelOpenTapGuardMillis) {
                            return@clickable
                        }
                        context.startActivity(
                            Intent().apply {
                                component = ComponentName(context.packageName, HabitExtras.ACTIVITY_MAIN)
                                putExtra(HabitExtras.EXTRA_EDIT_HABIT_ID, habit.id)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            },
                        )
                        closePanel()
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
        val minCellW = 22.dp
        val minCellH = 30.dp
        val cols = (contentW / minCellW).toInt().coerceIn(3, 8)
        val rows = (gridH / minCellH).toInt().coerceIn(1, 4)
        // 实际格宽 / 行高（扣除 1dp 间距），供点径按宽高双限取小、不裁剪。
        val cellW = ((contentW - (cols - 1).dp) / cols).coerceAtLeast(12.dp)
        val cellH = ((gridH - (rows - 1).dp) / rows).coerceAtLeast(16.dp)
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
                                    state = dot.state,
                                    palette = palette,
                                    day = LocalDate.ofEpochDay(dot.day).dayOfMonth,
                                    cellWidth = cellW,
                                    cellHeight = cellH,
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
    state: HabitDayState,
    palette: HabitDayPalette,
    day: Int,
    cellWidth: Dp,
    cellHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val visual = state.visual()
    val stateLabel = state.label()
    val dateSize = (cellWidth.value * 0.28f).coerceIn(6f, 9f).sp
    // 文字框紧排：lineHeight = dateSize，框高 ≈ dateSize，消除大空隙与裁剪。
    val textH = with(LocalDensity.current) { dateSize.toDp() }
    val gap = 1.dp
    val safety = 2.dp
    // 点径按宽高双限取小：宽限（cellW·0.60，填满格宽减小左右空距）与高限
    // （cellH − 文字 − 间距 − 安全边距），保证 textH + gap + dotSize ≤ cellH，任意尺寸不裁剪。
    val maxDotByW = (cellWidth * 0.60f).coerceIn(6.dp, 18.dp)
    val maxDotByH = (cellHeight - textH - gap - safety).coerceAtLeast(4.dp)
    val dotSize = minOf(maxDotByW, maxDotByH)
    val dotColor = visual.fillColor(palette)
    val borderColor = visual.borderColor(palette)
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 日期文本在上、点在下（与近 30 天网格一致）。
        Text(
            text = day.toString(),
            fontSize = dateSize,
            lineHeight = dateSize,
            color = palette.neutral.copy(alpha = 0.5f),
            maxLines = 1,
        )
        Spacer(Modifier.height(gap))
        Box(
            Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor)
                .border(width = HabitDayDotStroke, color = borderColor, shape = CircleShape)
                .semantics { contentDescription = "$day 日 · $stateLabel" },
            contentAlignment = Alignment.Center,
        ) {
            // 点阵与打卡圆、编辑页日历共用同一套「勾 / 横杠」标记。
            HabitDayMarkIcon(
                mark = visual.mark,
                color = visual.markTint(palette),
                modifier = Modifier.size(dotSize * 0.7f),
            )
        }
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
