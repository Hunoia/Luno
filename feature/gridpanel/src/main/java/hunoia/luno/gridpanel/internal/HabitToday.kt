package hunoia.luno.gridpanel.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId

/**
 * 跨零点正确的 epochDay：只在下一个本地午夜唤醒一次重算，不做分钟级轮询
 * （architecture §13：无事件不做功、定时对齐边界）。部件与编辑页共用同一口径，
 * 避免面板常驻或页面长时间打开时仍按「昨天」判定打卡状态。
 */
@Composable
internal fun rememberTodayEpochDay(): Long {
    var today by remember { mutableStateOf(LocalDate.now().toEpochDay()) }
    LaunchedEffect(today) {
        val nextMidnight = LocalDate.now()
            .plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        delay((nextMidnight - System.currentTimeMillis()).coerceAtLeast(1L))
        today = LocalDate.now().toEpochDay()
    }
    return today
}
