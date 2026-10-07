package hunoia.luno.service

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import hunoia.luno.action.model.StoredAction
import hunoia.luno.config.model.MiniWindowSettings
import hunoia.luno.core.JsonSerializer

/**
 * 真 IPC 分发口（跑在 :service 进程）。
 *
 * 之前 QuickSearchActivity 靠 `SideGestureService.current` 静态弱引用直调：
 * 同进程能用，跨进程恒 null，且 service 未活时静默丢弃。现在统一走 Binder，
 * 未就绪返回 false 由调用方提示，不再静默丢。
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

        override fun showMiniWindowAdjust(initialJson: String, cb: IMiniWindowAdjustCallback) {
            val svc = SideGestureService.current ?: return
            val initial = runCatching {
                JsonSerializer.decodeFromString<MiniWindowSettings>(initialJson)
            }.getOrNull() ?: return
            // oneway 回调跑在 Binder 线程，ComposeView 需主线程 Looper，必须投递回服务主线程。
            Handler(Looper.getMainLooper()).post {
                svc.miniWindowAdjustOverlay.show(
                    initial = initial,
                    commit = { s -> runCatching { cb.onCommit(JsonSerializer.encodeToString(s)) } },
                    onDismiss = { runCatching { cb.onDismiss() } },
                )
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
