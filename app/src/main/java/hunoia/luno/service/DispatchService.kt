package hunoia.luno.service

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.core.domain.JsonSerializer

/**
 * 真 IPC 分发口（跑在 :service 进程）：跨进程拿不到静态引用，统一走 Binder，
 * 未就绪返回 false 由调用方提示，不静默丢弃。
 */
class DispatchService : Service() {

    private val binder = object : IDispatchService.Stub() {
        override fun dispatchAction(actionJson: String, idempotencyKey: String?): Boolean {
            val action = runCatching {
                JsonSerializer.decodeFromString<StoredAction>(actionJson)
            }.getOrNull() ?: return false
            val svc = SideGestureService.current ?: return false
            if (!svc.isDispatchReady()) return false
            svc.dispatchAction(action, idempotencyKey)
            return true
        }

        override fun isReady(): Boolean =
            SideGestureService.current?.isDispatchReady() == true

        override fun releaseOrphanAppWidgets(keepIds: IntArray?) {
            // AppWidgetHost 的创建/回收只在 :service 做，避免主进程再挂一个同 HOST_ID 的监听。
            val keep = keepIds?.toSet() ?: emptySet()
            Handler(Looper.getMainLooper()).post {
                runCatching { hunoia.luno.gridpanel.widget.PanelAppWidgetHost.get().releaseOrphans(keep) }
            }
        }

        override fun refreshOcrEngine() {
            Handler(Looper.getMainLooper()).post {
                SideGestureService.current?.refreshOcrEngine()
            }
        }

    }

    override fun onBind(intent: Intent?): IBinder = binder
}