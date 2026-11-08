package hunoia.luno.config.model

/**
 * 应用内通知通道 id 收口：跨模块（提醒 / 固定通知隐藏）都要引用，避免字符串漂移。
 *
 * [HABIT_REMINDER] 是用户显式开启、面向用户的常驻提醒通知：
 * 固定通知隐藏功能只该隐藏后台前台服务（daemon / float_ball）这类「系统运行痕迹」，
 * 绝不能误伤需要持续提醒用户的常驻通知。
 */
const val HABIT_REMINDER_CHANNEL_ID = "habit_reminder"
