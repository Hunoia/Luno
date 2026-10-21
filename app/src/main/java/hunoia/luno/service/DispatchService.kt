package hunoia.luno.service

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import hunoia.luno.action.model.StoredAction
import hunoia.luno.core.JsonSerializer

/**
 * 真 IPC 分发口（跑在 :service 进程）。
 *
 * 之前 QuickSearchActivity 靠 `SideGestureService.current` 静态弱引用直调：
 * 同进程能用，跨进程恒 null，且 service 未活时静默丢弃。现在统一走 Binder，
 * 未就绪返回 false 由调用方提示，不再静默丢。
 *
 * 小窗位置调整曾经也在这里（无障碍服务持有全屏 overlay），已移出：它是纯配置任务，
 * 改成页内界面即可，不需要跨进程也不需要无障碍服务存活。
 *
 * 屏幕取点仍留在这一侧 —— 它必须盖在**别的应用**上面，而能这么做的窗口类型只有
 * `TYPE_ACCESSIBILITY_OVERLAY`（只有 AccessibilityService 能 addView）和
 * `TYPE_APPLICATION_OVERLAY`（需要「显示在其他应用上层」权限）。改用后者需要 Activity
 * 持有窗口，那又要引入一个本来不必有的权限，所以维持现状。见 runtime/overlay/PointPickOverlay.kt。
 */
class DispatchService : Service() {

    private val binder = object : IDispatchService.Stub() {
        override fun dispatchAction(actionJson: String): Boolean {
            val action = runCatching {
                JsonSerializer.decodeFromString<StoredAction>(actionJson)
            }.getOrNull() ?: return false
            val svc = SideGestureService.current ?: return false
            if (!svc.isDispatchReady()) return false
            svc.dispatchAction(action)
            return true
        }

        override fun isReady(): Boolean =
            SideGestureService.current?.isDispatchReady() == true

        override fun showPointPicker(initialXDp: Int, initialYdp: Int, cb: IPointPickCallback) {
            val svc = SideGestureService.current ?: return
            Handler(Looper.getMainLooper()).post {
                svc.pointPickOverlay.show(
                    initialXdp = initialXDp.takeIf { it >= 0 },
                    initialYdp = initialYdp.takeIf { it >= 0 },
                    commit = { xDp, yDp -> runCatching { cb.onCommit(xDp, yDp) } },
                    onDismiss = { runCatching { cb.onDismiss() } },
                )
            }
        }

        override fun releaseOrphanAppWidgets(keepIds: IntArray?) {
            // AppWidgetHost 的创建/回收只在 :service 做，避免主进程再挂一个同 HOST_ID 的监听。
            val keep = keepIds?.toSet() ?: emptySet()
            Handler(Looper.getMainLooper()).post {
                runCatching { hunoia.luno.gridpanel.widget.PanelAppWidgetHost.get().releaseOrphans(keep) }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder
}