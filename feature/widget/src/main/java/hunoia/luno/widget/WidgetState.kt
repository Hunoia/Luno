package hunoia.luno.widget

import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * 桌面小部件的每实例状态（Glance `Preferences` state，按 widget id 隔离）。
 *
 * 习惯部件只持久化绑定的习惯 id；打卡 / 待办完成不再在 RemoteViews 里维护 pending 状态，
 * 改由 [WidgetConfirmDialogActivity] 的普通确认弹窗完成（确认后才写回域门面）。
 */
internal object WidgetStateKeys {
    /** 习惯部件绑定的习惯 id（config Activity 写入）。 */
    val HABIT_ID = stringPreferencesKey("habit_id")
}
