package hunoia.luno.domain.runtime

import android.content.Context
import hunoia.luno.core.domain.StoredAction

/**
 * 运行态/跨进程入口门面：UI 不直接引用 `:service` 的 DispatchClient 或服务启动器。
 */
interface RuntimeFacade {

    /** 把明确动作转交 `:service` 执行；false = 未送达。 */
    fun dispatch(action: StoredAction): Boolean

    /** 屏幕取点（跨进程无障碍 overlay）；false = 未送达。 */
    fun showPointPicker(
        initialXdp: Int?,
        initialYdp: Int?,
        onCommit: (Int, Int) -> Unit,
        onDismiss: () -> Unit,
        onUnavailable: () -> Unit = {},
    ): Boolean

    /** 权限变化后让 `:service` 重读条件源；false = 未送达。 */
    fun refreshConditionSources(): Boolean

    /** 冷启动按落盘配置恢复常驻服务。 */
    fun restoreServicesOnce(context: Context)
}

/** `RuntimeFacade` 的默认实现：只做委托，不新增状态。 */
object RuntimeActions : RuntimeFacade {

    override fun dispatch(action: StoredAction): Boolean =
        hunoia.luno.service.DispatchClient.dispatch(action)

    override fun showPointPicker(
        initialXdp: Int?,
        initialYdp: Int?,
        onCommit: (Int, Int) -> Unit,
        onDismiss: () -> Unit,
        onUnavailable: () -> Unit,
    ): Boolean = hunoia.luno.service.DispatchClient.showPointPicker(
        initialXdp = initialXdp,
        initialYdp = initialYdp,
        onCommit = onCommit,
        onDismiss = onDismiss,
        onUnavailable = onUnavailable,
    )

    override fun refreshConditionSources(): Boolean =
        hunoia.luno.service.DispatchClient.refreshConditionSources()

    override fun restoreServicesOnce(context: Context) =
        hunoia.luno.service.ServiceAutoStart.restoreOnce(context)
}
