package hunoia.luno.runtime.dispatch

import hunoia.luno.core.domain.ActionDispatchPort
import hunoia.luno.core.domain.ActionRequest
import hunoia.luno.core.domain.AwaitableActionDispatch
import hunoia.luno.core.domain.StoredAction

/**
 * 进程级动作分发门面。
 *
 * 接口在 `:core:domain`，安装点在这里：搜索面板 / 链接分发只依赖契约，
 * 不需要反向依赖 `:feature:execute` 或 `:app`。Surface 的异步入口在
 * [hunoia.luno.runtime.surface.SurfaceDispatch]。
 *
 * - [ActionDispatchPort]：同步提交，返回是否送达；
 * - [AwaitableActionDispatch]：链接等需要等待实际执行结果的入口。
 */
object ActionDispatch {

    @Volatile
    private var sender: ActionDispatchPort? = null

    @Volatile
    private var awaitable: AwaitableActionDispatch? = null

    fun installSender(port: ActionDispatchPort?) {
        sender = port
    }

    /**
     * 只应由拥有本进程 Dispatcher 的宿主安装；注销时传 `null`，让调用方回退到 [sender]。
     */
    fun installAwaitable(dispatcher: AwaitableActionDispatch?) {
        awaitable = dispatcher
    }

    /** 同步提交；`false` = 未送达（未安装或对端未就绪）。 */
    fun send(action: StoredAction): Boolean = sender?.dispatch(action) ?: false

    /**
     * 等待执行结果：优先使用本进程 Dispatcher；没有则退回同步提交（只能确认送达）。
     */
    suspend fun dispatchAwait(request: ActionRequest): Boolean =
        awaitable?.dispatch(request) ?: sender?.dispatch(request.stored) ?: false
}
