package hunoia.luno.service

import hunoia.luno.core.domain.ActionFailure
import hunoia.luno.core.domain.ActionResult
import hunoia.luno.core.domain.Capability

/**
 * 跨进程三态的线格式：只传枚举序号 + 文案，不序列化 `ActionResult` 的实现细节。
 * 主进程与 :service 共用同一份编码，避免两边 when 分支漂移。
 */
object DispatchSettlementCodec {

    const val SUCCESS = 0
    const val FAILED = 1
    const val REQUIRES_CAPABILITY = 2

    data class Wire(
        val typeId: String,
        val status: Int,
        val reason: Int,
        val capability: Int,
        val message: String?,
        val userMessage: String?,
    )

    fun encode(typeId: String, result: ActionResult): Wire = when (result) {
        is ActionResult.Success -> Wire(
            typeId = typeId,
            status = SUCCESS,
            reason = -1,
            capability = -1,
            message = result.message,
            userMessage = null,
        )

        is ActionResult.Failed -> Wire(
            typeId = typeId,
            status = FAILED,
            reason = result.reason.ordinal,
            capability = -1,
            message = result.message,
            userMessage = result.userMessage,
        )

        is ActionResult.RequiresCapability -> Wire(
            typeId = typeId,
            status = REQUIRES_CAPABILITY,
            reason = -1,
            capability = result.capability.ordinal,
            message = null,
            userMessage = null,
        )
    }

    fun decode(
        status: Int,
        reason: Int,
        capability: Int,
        message: String?,
        userMessage: String?,
    ): ActionResult = when (status) {
        SUCCESS -> ActionResult.Success(message)
        REQUIRES_CAPABILITY -> ActionResult.RequiresCapability(
            Capability.entries.getOrElse(capability) { Capability.None },
        )

        else -> ActionResult.Failed(
            reason = ActionFailure.entries.getOrElse(reason) { ActionFailure.ExecutionFailed },
            message = message,
            userMessage = userMessage,
        )
    }
}
