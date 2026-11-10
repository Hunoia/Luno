package hunoia.luno.domain.gridpanel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import hunoia.luno.core.LunoLog as Log
import hunoia.luno.gridpanel.GridPanelOverlay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * debug-only：网格面板进出场探针（调动效不用装完再去找手势触发）。
 *
 * ```
 * adb shell am broadcast -n hunoia.luno.debug/hunoia.luno.domain.gridpanel.GridPanelProbeReceiver \
 *     -a hunoia.luno.debug.GRID_PANEL_PROBE [-es panel_id <id>] [-ei x 540] [-ei y 1200] [-ez close true]
 * ```
 *
 * 不传 `panel_id` 取配置里的第一块面板；`--ez close true` 同返回键（先让组合消费，
 * 没消费就播退场动画关闭）。
 * 必须跑在 `:service` 进程（面板浮窗的宿主进程），清单里已指定。
 */
class GridPanelProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra("close", false)) {
            GridPanelOverlay.dismissFromBack()
            return
        }
        val anchorX = intent.getIntExtra("x", 0)
        val anchorY = intent.getIntExtra("y", 0)
        val settings = runBlocking { GridPanelCenter.actionSettings.first() }
        val panels = settings.actionGridPanels.panels
        val panelId = intent.getStringExtra("panel_id")?.takeIf { it.isNotBlank() }
            ?: panels.firstOrNull()?.id
        if (panelId == null) {
            Log.w("GridPanelProbe", "no grid panel configured, nothing to show")
            return
        }
        Log.i("GridPanelProbe", "show panel=$panelId anchor=($anchorX,$anchorY) total=${panels.size}")
        GridPanelOverlay.show(panelId, anchorX, anchorY)
    }
}
