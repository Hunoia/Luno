package hunoia.luno.runtime.surface

import hunoia.luno.core.domain.ActionRequest
import hunoia.luno.core.domain.DispatchOrigin
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.core.domain.SurfaceActionDispatch

/**
 * Surface 侧的统一执行入口（进程级端口）。
 *
 * Surface 不直接持启动协调器，也不自行执行动作：它只构造 `StoredAction` 并交给这里，
 * 由 `:app` 在进程启动时注入的 Dispatcher 完成 `ActionRequest → Capability Gate → Executor`。
 *
 * 契约在 `:core:domain`，安装点在这里，因此 `floatball` / `gridpanel` / `quicklaunch`
 * 都不需要依赖 `:app` 或 `:feature:execute`；未注入时按「未送达」返回，调用方给失败反馈。
 */
object SurfaceDispatch {

    @Volatile
    private var dispatcher: SurfaceActionDispatch? = null

    /** 由 `:app` 注入：进程级 Dispatcher 门面。 */
    fun install(dispatcher: SurfaceActionDispatch) {
        this.dispatcher = dispatcher
    }

    fun isInstalled(): Boolean = dispatcher != null

    /**
     * 提交一次 Surface 动作。`onSettled` 在主线程回调真实成败；
     * 未注入 Dispatcher 时按未送达处理，不静默吞掉。
     */
    fun dispatch(
        action: StoredAction,
        origin: DispatchOrigin = DispatchOrigin.PANEL,
        onSettled: (Boolean) -> Unit = {},
    ) {
        val request = ActionRequest(stored = action, origin = origin)
        val current = dispatcher
        if (current == null) {
            onSettled(false)
            return
        }
        current.dispatch(request, onSettled)
    }
}
