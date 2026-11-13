package hunoia.luno.config.model

/**
 * 应用内通知通道 id 收口：跨模块（提醒 / 固定通知隐藏）都要引用，避免字符串漂移。
 *
 * [HABIT_REMINDER_CHANNEL_ID] / [TODO_REMINDER_CHANNEL_ID] 是用户显式开启、面向用户的
 * 常驻提醒通知：固定通知隐藏功能只该隐藏后台前台服务（daemon / float_ball）这类
 * 「系统运行痕迹」，绝不能误伤需要持续提醒用户的常驻通知。
 */
const val HABIT_REMINDER_CHANNEL_ID = "habit_reminder"

/** 待办提醒独立通道：用户可单独关闭，不与习惯提醒混在一起。 */
const val TODO_REMINDER_CHANNEL_ID = "todo_reminder"

/** 趋势记录提醒独立通道：每天至多一条，当天已记录时不发。 */
const val TREND_REMINDER_CHANNEL_ID = "trend_reminder"

/** 面向用户的常驻提醒通道：固定通知隐藏 / 悬浮球通知脉冲都不得误伤。 */
val UserFacingReminderChannelIds: Set<String> =
    setOf(HABIT_REMINDER_CHANNEL_ID, TODO_REMINDER_CHANNEL_ID, TREND_REMINDER_CHANNEL_ID)
