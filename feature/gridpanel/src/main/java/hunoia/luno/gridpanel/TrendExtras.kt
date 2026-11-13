package hunoia.luno.gridpanel

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * 趋势面板部件 / 提醒通知 → 主应用跳转链路的跨层常量。
 *
 * 网格面板是独立窗口（服务侧 overlay），部件不能直接拿 NavController；复用链接分发的
 * `LinkExtras` 模式：部件经 `LocalContext` 发一个显式 `Intent` 拉起
 * [hunoia.luno.ui.MainActivity]，Activity 写入 `PendingTrendRecord`，导航图消费一次即进详情页。
 * 组件名与 extra key 收口于此，避免 feature 层 import app 层。
 */
object TrendExtras {

    /** 主界面 Activity（显式组件名，跨进程可用）。 */
    const val ACTIVITY_MAIN = "hunoia.luno.ui.MainActivity"

    /** 打开趋势详情页的标记（`Boolean`，存在即触发）。 */
    const val EXTRA_OPEN_TREND = "hunoia.luno.extra.OPEN_TREND"

    /** 目标指标 id（`String`）：存在 = 进该指标详情；不存在 = 只打开列表页。 */
    const val EXTRA_TREND_METRIC_ID = "hunoia.luno.extra.TREND_METRIC_ID"

    /** 打开详情后立即弹出快速记录（`Boolean`）。 */
    const val EXTRA_TREND_OPEN_RECORD = "hunoia.luno.extra.TREND_OPEN_RECORD"

    /** 打开趋势列表页。 */
    fun openListIntent(context: Context): Intent = Intent().apply {
        component = ComponentName(context.packageName, ACTIVITY_MAIN)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        putExtra(EXTRA_OPEN_TREND, true)
    }

    /**
     * 打开指定指标详情；[openRecord] = true 时详情页自动弹出快速记录。
     * 内置部件标题 / 主体、提醒通知点击共用同一个 Intent 口径。
     */
    fun openDetailIntent(context: Context, metricId: String, openRecord: Boolean): Intent =
        Intent().apply {
            component = ComponentName(context.packageName, ACTIVITY_MAIN)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_OPEN_TREND, true)
            putExtra(EXTRA_TREND_METRIC_ID, metricId)
            putExtra(EXTRA_TREND_OPEN_RECORD, openRecord)
        }
}
