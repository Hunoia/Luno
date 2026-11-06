package hunoia.luno.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import hunoia.luno.core.domain.ActionResult
import hunoia.luno.core.domain.DispatchOrigin
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.core.domain.JsonSerializer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/** 跨进程提交结果：送达 / 未就绪 / 超时。 */
enum class SendOutcome { Delivered, NotReady, Timeout }

/**
 * 分发 IPC 客户端（主进程 / :service 进程通用）。
 *
 * [bind] 由 Application.onCreate 调用，两进程都连。bind 是异步的，
 * 未连上时 [send]/[await]/[isReady] 返回未就绪或 null，调用方负责提示。
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

    /** 提交（把动作交给 :service）的硬上限；正常是毫秒级。 */
    private const val HANDOFF_TIMEOUT_MS = 500L

    /** 等待执行三态的上限；执行本身可能启动应用 / 跑 shell，不能按 500ms 算。 */
    private const val RESULT_TIMEOUT_MS = 10_000L

    private val noopCallback = object : IDispatchCallback.Stub() {
        override fun onSettled(
            typeId: String?,
            status: Int,
            reason: Int,
            capability: Int,
            message: String?,
            userMessage: String?,
        ) = Unit
    }

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

    /** 分发链就绪。跨进程走 Binder（有界等待），同进程绑完成时兜底直查。 */
    suspend fun isReady(): Boolean {
        if (localReady()) return true
        val s = stub ?: run {
            retryBindIfNeeded()
            return false
        }
        val viaBinder = try {
            withContext(Dispatchers.IO) {
                withTimeout(HANDOFF_TIMEOUT_MS) {
                    runInterruptible { s.isReady() }
                }
            }
        } catch (_: TimeoutCancellationException) {
            false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        if (!viaBinder) retryBindIfNeeded()
        return viaBinder
    }

    /**
     * 转交 :service 分发，只等“已受理”，不等执行结果。
     * 超时 = 我们停止等待，不保证 :service 没收到；不自动重试。
     */
    suspend fun send(
        action: StoredAction,
        idempotencyKey: String? = null,
        origin: DispatchOrigin = DispatchOrigin.PANEL,
    ): SendOutcome {
        val delivered = handoff(action, idempotencyKey, origin, noopCallback)
            ?: return SendOutcome.Timeout
        return if (delivered) SendOutcome.Delivered else SendOutcome.NotReady
    }

    /**
     * 转交并等待真实三态。null = 未送达 / 超时 / 取消；调用方按失败处理，不自动重试。
     * 取消当前协程会立即停止等待，:service 侧已开始的执行不会被强制打断。
     */
    suspend fun await(
        action: StoredAction,
        idempotencyKey: String? = null,
        origin: DispatchOrigin = DispatchOrigin.PANEL,
    ): ActionResult? {
        val deferred = CompletableDeferred<ActionResult>()
        val callback = object : IDispatchCallback.Stub() {
            override fun onSettled(
                typeId: String?,
                status: Int,
                reason: Int,
                capability: Int,
                message: String?,
                userMessage: String?,
            ) {
                deferred.complete(
                    DispatchSettlementCodec.decode(status, reason, capability, message, userMessage),
                )
            }
        }
        val delivered = handoff(action, idempotencyKey, origin, callback) ?: return null
        if (!delivered) return null
        return withTimeoutOrNull(RESULT_TIMEOUT_MS) { deferred.await() }
    }

    /** true = 已受理，false = 未就绪，null = 提交超时。 */
    private suspend fun handoff(
        action: StoredAction,
        idempotencyKey: String?,
        origin: DispatchOrigin,
        callback: IDispatchCallback,
    ): Boolean? {
        val s = stub ?: run {
            retryBindIfNeeded()
            return false
        }
        val json = JsonSerializer.encodeToString(action)
        return try {
            withContext(Dispatchers.IO) {
                withTimeout(HANDOFF_TIMEOUT_MS) {
                    runInterruptible {
                        s.dispatchAction(json, idempotencyKey, origin.name, callback)
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
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
