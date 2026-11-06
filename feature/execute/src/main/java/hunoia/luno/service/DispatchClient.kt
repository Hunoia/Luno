package hunoia.luno.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.core.domain.JsonSerializer

/**
 * 分发 IPC 客户端（主进程 / :service 进程通用）。
 *
 * [bind] 由 Application.onCreate 调用，两进程都连。bind 是异步的，
 * 未连上时 [dispatch]/[isReady] 直接返回 false，调用方负责提示。
 */
object DispatchClient {

    /**
     * 由 `:app` 注入：要绑定的分发服务组件（`DispatchService`）。
     * 执行口不认识宿主 Android 组件，只认这个 Class。
     */
    @Volatile var serviceClass: Class<*>? = null

    /** 由 `:app` 注入：同进程内已就绪的兜底判断（服务进程自己查询时用）。 */
    @Volatile var localReady: () -> Boolean = { false }

    /** 未连上时的重试间隔。 */
    private const val RETRY_INTERVAL_MS = 2_000L

    @Volatile private var stub: IDispatchService? = null
    @Volatile private var appCtx: Context? = null

    private val connection = DispatchConnection(retryIntervalMs = RETRY_INTERVAL_MS)

    private val conn: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            stub = runCatching { IDispatchService.Stub.asInterface(binder) }.getOrNull()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            stub = null
            connection.onServiceDisconnected()
        }

        override fun onBindingDied(name: ComponentName?) {
            // 宿主进程已死：必须 unbind 后重新 bind，系统不会复用这个连接。
            stub = null
            connection.onBindingDied()
            appCtx?.let { runCatching { it.unbindService(conn) } }
        }

        override fun onNullBinding(name: ComponentName?) {
            stub = null
            connection.onNullBinding()
            appCtx?.let { runCatching { it.unbindService(conn) } }
        }
    }

    /**
     * 发起绑定。只有 bindService 真的成功才记 `bindRequested`：失败也置位会让整个
     * 进程生命周期都不再重试，之后永远提示「服务未就绪」。
     */
    fun bind(appContext: Context) {
        appCtx = appContext.applicationContext
        if (connection.bindRequested) return
        val component = serviceClass ?: return
        val bound = runCatching {
            appContext.bindService(
                Intent(appContext, component),
                conn,
                Context.BIND_AUTO_CREATE,
            )
        }.getOrDefault(false)
        connection.onBindAttempt(bound)
    }

    /** 未连上时的轻量重试（限频），避免每次查状态都 bindService。 */
    private fun retryBindIfNeeded() {
        if (!connection.shouldRetryBind()) return
        val ctx = appCtx ?: return
        bind(ctx)
    }

    /** 分发链就绪。跨进程走 Binder，同进程 bind 未完成时兜底直查。 */
    fun isReady(): Boolean {
        val viaBinder = runCatching { stub?.isReady() == true }.getOrDefault(false)
        if (viaBinder || localReady()) return true
        retryBindIfNeeded()
        return false
    }

    /** 转交 :service 分发。false = 未送达（未连接或对端未就绪）。键原样透传，不在转发层补键。 */
    fun dispatch(action: StoredAction, idempotencyKey: String? = null): Boolean {
        val s = stub ?: run {
            retryBindIfNeeded()
            return false
        }
        return runCatching {
            s.dispatchAction(JsonSerializer.encodeToString(action), idempotencyKey)
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

    /** OCR 模型导入/删除后，让 :service 重新加载引擎（未连接返回 false）。 */
    fun refreshOcrEngine(): Boolean {
        val s = stub ?: run {
            retryBindIfNeeded()
            return false
        }
        return runCatching { s.refreshOcrEngine() }.isSuccess
    }

}
