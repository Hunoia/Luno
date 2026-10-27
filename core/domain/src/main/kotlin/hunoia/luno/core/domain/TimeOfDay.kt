package hunoia.luno.core.domain

import java.util.Calendar

/**
 * 分钟/星期取值的 60s 缓存：环境采集与条件求值共用，跨整分重读一次日历，
 * 时区改动最多 60s 自纠。ThreadLocal 避免 Main / IO 两条求值路径竞争 Calendar。
 */
private val minuteOfDayCalendar = ThreadLocal.withInitial { Calendar.getInstance() }

// @Volatile: 两个线程同时跨过整分时最多各算一次并写入同样的值，不需要加锁。
@Volatile private var cachedMinuteOfDay = -1
@Volatile private var cachedDayOfWeek = -1
@Volatile private var cachedAtSecond = -1L

private fun refreshCalendarCacheIfNeeded() {
    val nowSecond = System.currentTimeMillis() / 1000L
    if (cachedMinuteOfDay >= 0 && nowSecond - cachedAtSecond < 60L) return
    val calendar = minuteOfDayCalendar.get()
    calendar.timeInMillis = System.currentTimeMillis()
    cachedMinuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    cachedDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
    cachedAtSecond = nowSecond
}

fun nowMinuteOfDay(): Int {
    refreshCalendarCacheIfNeeded()
    return cachedMinuteOfDay
}

/** Calendar.DAY_OF_WEEK 常量（SUNDAY=1..SATURDAY=7）。 */
fun nowDayOfWeek(): Int {
    refreshCalendarCacheIfNeeded()
    return cachedDayOfWeek
}

fun formatTimeOfDay(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
