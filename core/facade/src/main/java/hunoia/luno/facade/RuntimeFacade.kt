package hunoia.luno.facade

import android.content.Context
import hunoia.luno.core.domain.DispatchOrigin
import hunoia.luno.core.domain.StoredAction

/**
 * 运行态/跨进程入口门面：UI 不直接引用 `:service` 的 DispatchClient 或服务启动器。
 */
interface RuntimeFacade {

    /** 把明确动作转交 `:service` 执行；false = 未送达（未就绪 / 超时）。 */
    suspend fun dispatch(
        action: StoredAction,
        origin: DispatchOrigin = DispatchOrigin.EXTERNAL_INTENT,
    ): Boolean

    /** 冷启动按落盘配置恢复常驻服务。 */
    fun restoreServicesOnce(context: Context)
}

/** `RuntimeFacade` 的默认实现：只做委托，不新增状态。 */
