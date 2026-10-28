package hunoia.luno.core.domain

/**
 * 动作分发的同步提交端口。
 *
 * 返回值只表示「是否送达」，不表示执行成功；执行三态由 [SurfaceActionDispatch]
 * 的 `onSettled` 或具体调用方另行处理。实现方负责选择本进程 Dispatcher 或跨进程 IPC。
 */
fun interface ActionDispatchPort {
    fun dispatch(action: StoredAction): Boolean
}

/**
 * 可等待的动作分发端口。
 *
 * 给需要真实执行结果的触发/解析链路使用（例如链接分发）：
 * `ActionRequest -> this -> Dispatcher`，返回值表示实际执行是否成功。
 */
fun interface AwaitableActionDispatch {
    suspend fun dispatch(request: ActionRequest): Boolean
}

/**
 * Surface 侧的统一执行入口。
 *
 * Surface 不持 Dispatcher，也不自行执行动作：它只构造 [ActionRequest] 并交给这里，
 * 由 `:app` 注入的进程级实现完成 `Capability Gate -> Executor`。
 */
fun interface SurfaceActionDispatch {
    fun dispatch(request: ActionRequest, onSettled: (Boolean) -> Unit)
}
