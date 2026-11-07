package hunoia.luno.widget

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * 桌面小部件的每实例状态（Glance `Preferences` state，按 widget id 隔离）。
 *
 * 双击确认只依赖两个瞬时键：pending 标记 + 首点时间戳。超时窗口由
 * [ConfirmWindowMillis] 给出，超时后下一次交互当作新起点；重绘时若已超时
 * 也自动清除（避免部件一直停留在「再点确认」）。
 */
internal object WidgetStateKeys {
    /** 习惯部件绑定的习惯 id（config Activity 写入）。 */
    val HABIT_ID = stringPreferencesKey("habit_id")

    /** 习惯打卡双击确认：是否已进入待确认。 */
    val HABIT_PENDING = booleanPreferencesKey("habit_pending")
    /** 习惯打卡首点时间戳（epoch millis）。 */
    val HABIT_PENDING_AT = longPreferencesKey("habit_pending_at")

    /** 待办勾选待确认的 todo id（空串 = 无）。 */
    val TODO_PENDING_ID = stringPreferencesKey("todo_pending_id")
    /** 待办勾选首点时间戳（epoch millis）。 */
    val TODO_PENDING_AT = longPreferencesKey("todo_pending_at")
}

/**
 * 双击确认窗口：与面板 `rememberConfirmProgress` 同值，保证桌面部件与内置面板口径一致。
 *
 * **同值镜像**（architecture §7.5）：唯一事实源是 ui/design `Dimension.kt` 的
 * `MotionConfirmCountdown = 1200`（ui-guidelines §2.4）。`:feature:widget` 不依赖
 * `:ui:design`（模块门禁），故在此镜像；改值须两侧同步，且更新 ui-guidelines 口径。
 */
internal const val ConfirmWindowMillis = 1_200L
