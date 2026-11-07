package hunoia.luno.gridpanel

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * 习惯面板部件 → 主应用跳转链路的跨层常量。
 *
 * 网格面板是独立窗口（服务侧 overlay），部件不能直接拿 NavController；复用链接分发的
 * `LinkExtras` 模式：部件经 `LocalContext` 发一个显式 `Intent`（带 habitId extra）拉起
 * [hunoia.luno.ui.MainActivity]，Activity 写入 `PendingHabitEdit`，导航图消费一次即进编辑页。
 * 组件名与 extra key 收口于此，避免 feature 层 import app 层。
 */
object HabitExtras {

    /** 主界面 Activity（显式组件名，跨进程可用）。 */
    const val ACTIVITY_MAIN = "hunoia.luno.ui.MainActivity"

    /** 要打开的编辑页习惯 id（`String`）。 */
    const val EXTRA_EDIT_HABIT_ID = "hunoia.luno.extra.EDIT_HABIT_ID"

    /** 拉起主界面并进指定习惯编辑页；内置部件与桌面部件共用同一个 Intent 口径。 */
    fun editIntent(context: Context, habitId: String): Intent = Intent().apply {
        component = ComponentName(context.packageName, ACTIVITY_MAIN)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        putExtra(EXTRA_EDIT_HABIT_ID, habitId)
    }
}
