package hunoia.luno.config.model

/**
 * 应用内通知通道 / 前台服务通知 id 收口：跨模块（提醒 / 固定通知隐藏 / 保活）都要引用，
 * 避免字符串 / 数字漂移。
 *
 * [HABIT_REMINDER_CHANNEL_ID] / [TODO_REMINDER_CHANNEL_ID] / [TREND_REMINDER_CHANNEL_ID]
 * 是用户显式开启、面向用户的常驻提醒通知。
 */
const val HABIT_REMINDER_CHANNEL_ID = "habit_reminder"

/** 待办提醒独立通道：用户可单独关闭，不与习惯提醒混在一起。 */
const val TODO_REMINDER_CHANNEL_ID = "todo_reminder"

/** 趋势记录提醒独立通道：每天至多一条，当天已记录时不发。 */
const val TREND_REMINDER_CHANNEL_ID = "trend_reminder"

/** 保活守护的前台服务通知 id（手势待命 / 无障碍保活）。 */
const val DAEMON_NOTIFICATION_ID = 2001

/** 悬浮球的前台服务通知 id。 */
const val FLOATBALL_NOTIFICATION_ID = 2002

/** 系统 Toast 提示可配置呈现的通知通道（用户可单独管理）。 */
const val TOAST_CHANNEL_ID = "toast_prompt"

/** 系统 Toast 提示通知的固定 id。 */
const val TOAST_NOTIFICATION_ID = 3001

/**
 * 固定通知隐藏只该收走这两个「运行保活」的前台服务通知（daemon / float ball），
 * 其余 Luno 通知（习惯 / 待办 / 趋势 / 识别提醒）都是用户显式开启、面向用户的，
 * 不得因「隐藏 Luno 通知」被收走。精确按 id 匹配，不靠通道豁免——
 * 将来新增任何通知都不会被误伤。
 */
val KeepAliveNotificationIds: Set<Int> =
    setOf(DAEMON_NOTIFICATION_ID, FLOATBALL_NOTIFICATION_ID)
