package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 待办提醒推迟时长的默认值（分钟）：与习惯默认一致。 */
const val TodoDefaultSnoozeMinutes = 30

/** 待办提醒推迟时长下限（分钟）。 */
const val TodoMinSnoozeMinutes = 1

/** 待办提醒推迟时长上限（分钟）：6 小时。 */
const val TodoMaxSnoozeMinutes = 6 * 60

/**
 * 待办提醒：**本地墙钟**（epochDay + 当天分钟），不存 epoch millis。
 *
 * 用户设的是「11 月 12 日 09:00」而不是某个绝对时刻：切换时区 / 手动改系统时间后，
 * 调度器按同一墙钟重算，提醒仍落在用户看到的那个时间点。
 *
 * 只存用户意图，不存「是否已弹过」等运行态；运行态在
 * `TodoReminderScheduler` 的 SharedPreferences 里（不随备份走，恢复备份不会补弹旧提醒）。
 */
@Serializable
@Keep
data class TodoReminder(
    /** [LocalDate.toEpochDay]：本地日历日。 */
    val day: Long,
    /** 当天分钟数 0..1439。 */
    val minuteOfDay: Int,
) {

    val safeMinuteOfDay: Int get() = minuteOfDay.coerceIn(0, MinutesPerDay - 1)

    val hour: Int get() = safeMinuteOfDay / 60

    val minute: Int get() = safeMinuteOfDay % 60

    /** 该墙钟在 [zone] 下的绝对时刻；跨 DST 时仍按「当天该钟点」解释。 */
    fun toEpochMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.ofEpochDay(day)
            .atStartOfDay(zone)
            .plusMinutes(safeMinuteOfDay.toLong())
            .toInstant()
            .toEpochMilli()

    fun isPast(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        toEpochMillis(zone) <= nowMillis

    /** 调度标记的唯一键：`day:minuteOfDay`。 */
    fun key(): String = "$day:$safeMinuteOfDay"

    companion object {
        const val MinutesPerDay = 24 * 60
    }
}

/** 提醒文案的语义分类；具体「今天 / 明天 / 几月几日」由各模块用自己的字符串资源渲染。 */
enum class TodoReminderLabelKind {
    OVERDUE,
    TODAY,
    TOMORROW,
    DATE,
}

data class TodoReminderLabel(
    val kind: TodoReminderLabelKind,
    val month: Int,
    val dayOfMonth: Int,
    val hour: Int,
    val minute: Int,
)

/** 提醒的展示分类：已过 → OVERDUE；今天 / 明天 / 其它日期。 */
fun TodoReminder.label(
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): TodoReminderLabel {
    val date = LocalDate.ofEpochDay(day)
    val kind = if (isPast(nowMillis, zone)) {
        TodoReminderLabelKind.OVERDUE
    } else {
        // 分类必须相对调用方传入的 nowMillis，而不是墙钟当下：否则固定时间注入的
        // 测试 / 重放会在跨天后把「今天」误判成「其它日期」。
        val nowDate = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        when (day - nowDate.toEpochDay()) {
            0L -> TodoReminderLabelKind.TODAY
            1L -> TodoReminderLabelKind.TOMORROW
            else -> TodoReminderLabelKind.DATE
        }
    }
    return TodoReminderLabel(
        kind = kind,
        month = date.monthValue,
        dayOfMonth = date.dayOfMonth,
        hour = hour,
        minute = minute,
    )
}
