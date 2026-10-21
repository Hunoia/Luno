package hunoia.luno.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import hunoia.luno.action.model.StoredAction
import hunoia.luno.core.JsonSerializer

/**
 * 分发 IPC 客户端（主进程 / :service 进程通用）。
 *
 * [bind] 由 Application.onCreate 调用，两进程都连。bind 是异步的，
 * 未连上时 [dispatch]/[isReady] 直接返回 false，调用方负责提示。
 */
object DispatchClient {

    /** 未连上时的重试间隔。 */
    private const val RETRY_INTERVAL_MS = 2_000L

    private var stub: IDispatchService? = null
    @Volatile private var bindRequested = false
    @Volatile private var appCtx: Context? = null
    private var lastBindRetryMs = 0L

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            stub = runCatching { IDispatchService.Stub.asInterface(binder) }.getOrNull()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            stub = null
        }
    }

    /**
     * 发起绑定。**只有 bindService 真的成功才记位**：原来无论成败都置 true，
     * 一次异常就让整个进程生命周期都不再重试，之后永远提示"服务未就绪"。
     */
    fun bind(appContext: Context) {
        appCtx = appContext.applicationContext
        if (bindRequested) return
        val bound = runCatching {
            appContext.bindService(
                Intent(appContext, DispatchService::class.java),
                conn,
                Context.BIND_AUTO_CREATE,
            )
        }.getOrDefault(false)
        if (bound) bindRequested = true
    }

    /** 未连上时的轻量重试（限频），避免每次查状态都 bindService。 */
    private fun retryBindIfNeeded() {
        if (bindRequested) return
        val ctx = appCtx ?: return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastBindRetryMs < RETRY_INTERVAL_MS) return
        lastBindRetryMs = now
        bind(ctx)
    }

    /** 分发链就绪。跨进程走 Binder，同进程 bind 未完成时兜底直查。 */
    fun isReady(): Boolean {
        val viaBinder = runCatching { stub?.isReady() == true }.getOrDefault(false)
        if (viaBinder || SideGestureService.current?.isDispatchReady() == true) return true
        retryBindIfNeeded()
        return false
    }

    /** 转交 :service 分发。false = 未送达（未连接或对端未就绪）。 */
    fun dispatch(action: StoredAction): Boolean {
        val s = stub ?: return false
        return runCatching {
            s.dispatchAction(JsonSerializer.encodeToString(action))
        }.getOrDefault(false)
    }

    /**
     * 让 :service 的 AppWidgetHost 回收未引用的 widget id。
     * false = 未送达（只影响资源回收时机，不影响功能）。
     */
    fun releaseOrphanAppWidgets(keepIds: Set<Int>): Boolean {
        val s = stub ?: run {
            retryBindIfNeeded()
            return false
        }
        return runCatching { s.releaseOrphanAppWidgets(keepIds.toIntArray()) }.isSuccess
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 请求 :service 的无障碍服务展示屏幕取点 overlay。
     * 结果经跨进程回调发送，[onCommit]/[onDismiss] 回到主线程执行（dp 坐标，直接写 xDp/yDp 参数）。
     * false = 未送达（服务未连接或未就绪）。
     */
    fun showPointPicker(
        initialXdp: Int?,
        initialYdp: Int?,
        onCommit: (Int, Int) -> Unit,
        onDismiss: () -> Unit,
    ): Boolean {
        val s = stub ?: return false
        val cb = object : IPointPickCallback.Stub() {
            override fun onCommit(xDp: Int, yDp: Int) {
                mainHandler.post { onCommit(xDp, yDp) }
            }

            override fun onDismiss() {
                mainHandler.post { onDismiss() }
            }
        }
        return runCatching {
            s.showPointPicker(initialXdp ?: -1, initialYdp ?: -1, cb)
        }.isSuccess
    }
}
