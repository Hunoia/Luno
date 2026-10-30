package hunoia.luno.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Handler
import android.os.Looper
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
     * 发起绑定。只有 bindService 真的成功才记 `bindRequested`：失败也置位会让整个
     * 进程生命周期都不再重试，之后永远提示「服务未就绪」。
     */
    fun bind(appContext: Context) {
        appCtx = appContext.applicationContext
        if (bindRequested) return
        val component = serviceClass ?: return
        val bound = runCatching {
            appContext.bindService(
                Intent(appContext, component),
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
        if (viaBinder || localReady()) return true
        retryBindIfNeeded()
        return false
    }

    /** 转交 :service 分发。false = 未送达（未连接或对端未就绪）。 */
    fun dispatch(action: StoredAction): Boolean {
        val s = stub ?: run {
            retryBindIfNeeded()
            return false
        }
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

    /** 权限授予/撤销后让 :service 重读 Wi-Fi/蓝牙条件源；false = 未送达。 */
    fun refreshConditionSources(): Boolean {
        val s = stub ?: run {
            retryBindIfNeeded()
            return false
        }
        return runCatching { s.refreshConditionSources() }.isSuccess
    }

    /** OCR 模型导入/删除后，让 :service 重新加载引擎（未连接返回 false）。 */
    fun refreshOcrEngine(): Boolean {
        val s = stub ?: run {
            retryBindIfNeeded()
            return false
        }
        return runCatching { s.refreshOcrEngine() }.isSuccess
    }

    /** 运行中的规则 id；服务未连接时返回空集（列表页按「未运行」展示）。 */
    fun runningRuleIds(): Set<String> {
        val s = stub ?: run {
            retryBindIfNeeded()
            return emptySet()
        }
        return runCatching {
            JsonSerializer.decodeFromString<List<String>>(s.runningRuleIds()).toSet()
        }.getOrDefault(emptySet())
    }

    /** 停止一条规则的所有运行实例；false = 未送达或本来就没在跑。 */
    fun stopRule(ruleId: String): Boolean {
        val s = stub ?: run {
            retryBindIfNeeded()
            return false
        }
        return runCatching { s.stopRule(ruleId) }.getOrDefault(false)
    }

    /**
     * 编辑页测试运行：条件按 :service 当前快照求值，[executeSteps] 为 false 时只检查条件。
     * [onResult] 回到主线程；null 表示服务未就绪或返回不可解析。
     */
    fun runRuleTest(
        ruleJson: String,
        executeSteps: Boolean,
        onResult: (String?) -> Unit,
    ): Boolean {
        val s = stub ?: run {
            retryBindIfNeeded()
            return false
        }
        val cb = object : IRuleTestCallback.Stub() {
            override fun onResult(resultJson: String) {
                // 结果模型归自动化模块，这里只透传 JSON，避免执行口反向依赖自动化。
                mainHandler.post { onResult(resultJson) }
            }
        }
        return runCatching {
            s.runRuleTest(ruleJson, executeSteps, cb)
        }.getOrDefault(false)
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
        onUnavailable: () -> Unit = {},
    ): Boolean {
        val s = stub ?: return false
        val cb = object : IPointPickCallback.Stub() {
            override fun onCommit(xDp: Int, yDp: Int) {
                mainHandler.post { onCommit(xDp, yDp) }
            }

            override fun onDismiss() {
                mainHandler.post { onDismiss() }
            }

            override fun onUnavailable() {
                mainHandler.post { onUnavailable() }
            }
        }
        return runCatching {
            s.showPointPicker(initialXdp ?: -1, initialYdp ?: -1, cb)
        }.isSuccess
    }
}
