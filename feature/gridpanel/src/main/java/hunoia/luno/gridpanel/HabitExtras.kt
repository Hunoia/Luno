package hunoia.luno.gridpanel

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * 习惯面板部件 → 主应用跳转链路的跨层常量。
 *
 * 网格面板是独立窗口（服务侧 overlay），部件不能直接拿 NavController；复用链接分发的
 * `LinkExtras` 模式：部件经 `LocalContext` 发一个显式 `Intent`（带 habitId extra）拉起
 * [hunoia.luno.ui.MainActivity]，Activity 写入 `PendingHabitDetail`，导航图消费一次即进详情页。
 * 组件名与 extra key 收口于此，避免 feature 层 import app 层。
 *
 * 跳的是**详情页**（历史补卡 / 热力图），不是编辑页：部件 / 提醒的用户意图是「看看这个习惯」，
 * 编辑是详情页右上角再点一步（habit 与 trend 同一口径：`PendingTrendRecord` → `TrendDetail`）。
 */
object HabitExtras {

    /** 主界面 Activity（显式组件名，跨进程可用）。 */
    const val ACTIVITY_MAIN = "hunoia.luno.ui.MainActivity"

    /** 要打开的习惯详情 id（`String`）。 */
    const val EXTRA_DETAIL_HABIT_ID = "hunoia.luno.extra.DETAIL_HABIT_ID"

    /** 拉起主界面并进指定习惯详情页；内置部件与桌面部件共用同一个 Intent 口径。 */
    fun detailIntent(context: Context, habitId: String): Intent = Intent().apply {
        component = ComponentName(context.packageName, ACTIVITY_MAIN)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        putExtra(EXTRA_DETAIL_HABIT_ID, habitId)
    }
}
