package hunoia.luno.gridpanel.habit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.config.model.HabitMaxSnoozeMinutes
import hunoia.luno.config.model.HabitMinSnoozeMinutes
import hunoia.luno.gridpanel.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** 「推迟时长」文案（分钟 → 中文），通知按钮与编辑页共用。 */
@Composable
internal fun snoozeDurationLabel(minutes: Int): String {
    val safe = minutes.coerceIn(HabitMinSnoozeMinutes, HabitMaxSnoozeMinutes)
    val hours = safe / 60
    val rest = safe % 60
    return when {
        hours == 0 -> stringResource(R.string.habit_snooze_duration_minutes, rest)
        rest == 0 -> stringResource(R.string.habit_snooze_duration_hours, hours)
        else -> stringResource(R.string.habit_snooze_duration_hours_minutes, hours, rest)
    }
}

/**
 * 推迟时长选择：圆形分钟表盘（0–59 分、60 刻度、拖动 / 点按）+ 0–6 小时快捷档。
 *
 * 总范围 1 分钟 – 6 小时：小时为 0 时分钟至少 1，选 6 小时时分钟固定 0。
 * 表盘只负责分钟，小时由快捷档决定，两者组合成 [HabitMaxSnoozeMinutes] 以内的总时长。
 */
@Composable
internal fun HabitSnoozeDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (minutes: Int) -> Unit,
) {
    var total by remember {
        mutableIntStateOf(initialMinutes.coerceIn(HabitMinSnoozeMinutes, HabitMaxSnoozeMinutes))
    }
    val hour = total / 60
    val minute = total % 60
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.habit_snooze_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MinuteDial(
                    minute = minute,
                    enabled = hour < 6,
                    onMinuteChange = { picked ->
                        val safeMinute = if (hour == 0) {
                            picked.coerceIn(1, 59)
                        } else {
                            picked.coerceIn(0, 59)
                        }
                        total = hour * 60 + safeMinute
                    },
                )
                Text(
                    text = snoozeDurationLabel(total),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.habit_snooze_hours_unit),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    (0..6).forEach { h ->
                        FilterChip(
                            selected = h == hour,
                            onClick = {
                                total = when {
                                    h == 6 -> HabitMaxSnoozeMinutes
                                    h == 0 -> (total % 60).coerceAtLeast(HabitMinSnoozeMinutes)
                                    else -> h * 60 + (total % 60)
                                }
                            },
                            label = {
                                Text(
                                    text = h.toString(),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(total) }) {
                Text(stringResource(hunoia.luno.design.R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.habit_reminder_cancel))
            }
        },
    )
}

/** 60 刻度的分钟表盘：12 点方向为 0 分，顺时针递增；拖动 / 点按都能改。 */
@Composable
private fun MinuteDial(
    minute: Int,
    enabled: Boolean,
    onMinuteChange: (Int) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainerHighest
    val dialModifier = Modifier
        .size(220.dp)
        .then(
            if (enabled) {
                Modifier.pointerInput(Unit) {
                    fun update(position: Offset) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = position.x - cx
                        val dy = position.y - cy
                        if (dx == 0f && dy == 0f) return
                        var degrees = Math.toDegrees(
                            atan2(dy.toDouble(), dx.toDouble()),
                        ).toFloat() + 90f
                        if (degrees < 0f) degrees += 360f
                        onMinuteChange(((degrees / 6f).roundToInt()) % 60)
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        update(down.position)
                        down.consume()
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { change ->
                                if (change.pressed) {
                                    update(change.position)
                                    change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
            } else {
                Modifier
            },
        )
    Box(modifier = dialModifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = minOf(size.width, size.height) / 2f - 6.dp.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(color = surface, radius = radius, center = center)
            // 已选区间的淡色弧，让「转了多少」一眼可见。
            drawArc(
                color = primary.copy(alpha = 0.16f),
                startAngle = -90f,
                sweepAngle = minute * 6f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round),
            )
            for (i in 0 until 60) {
                val major = i % 5 == 0
                val angle = Math.toRadians((i * 6 - 90).toDouble())
                val outer = radius - 2.dp.toPx()
                val inner = outer - if (major) 11.dp.toPx() else 5.dp.toPx()
                val start = Offset(
                    center.x + (cos(angle) * inner).toFloat(),
                    center.y + (sin(angle) * inner).toFloat(),
                )
                val end = Offset(
                    center.x + (cos(angle) * outer).toFloat(),
                    center.y + (sin(angle) * outer).toFloat(),
                )
                drawLine(
                    color = if (i == minute) primary else track,
                    start = start,
                    end = end,
                    strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            val thumbAngle = Math.toRadians((minute * 6 - 90).toDouble())
            val thumbRadius = radius - 20.dp.toPx()
            drawCircle(
                color = primary,
                radius = 9.dp.toPx(),
                center = Offset(
                    center.x + (cos(thumbAngle) * thumbRadius).toFloat(),
                    center.y + (sin(thumbAngle) * thumbRadius).toFloat(),
                ),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = minute.toString(),
                style = MaterialTheme.typography.displaySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else track,
            )
            Text(
                text = stringResource(R.string.habit_snooze_minutes_unit),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
