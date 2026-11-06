package hunoia.luno.service

import hunoia.luno.core.domain.ActionFailure
import hunoia.luno.core.domain.ActionResult
import hunoia.luno.core.domain.Capability
import org.junit.Assert.assertEquals
import org.junit.Test

/** 跨进程三态线格式：主进程与 :service 必须能无损往返（architecture §12.1）。 */
class DispatchSettlementCodecTest {

    @Test
    fun `success 往返保留 message`() {
        val wire = DispatchSettlementCodec.encode("a.b", ActionResult.Success("done"))
        assertEquals(DispatchSettlementCodec.SUCCESS, wire.status)
        assertEquals(ActionResult.Success("done"), DispatchSettlementCodec.decode(wire.status, wire.reason, wire.capability, wire.message, wire.userMessage))
    }

    @Test
    fun `failed 往返保留 reason 与文案`() {
        val result = ActionResult.Failed(ActionFailure.PermissionDenied, "denied", "请先授权")
        val wire = DispatchSettlementCodec.encode("a.b", result)
        assertEquals(DispatchSettlementCodec.FAILED, wire.status)
        assertEquals(
            result,
            DispatchSettlementCodec.decode(wire.status, wire.reason, wire.capability, wire.message, wire.userMessage),
        )
    }

    @Test
    fun `requires capability 往返保留能力`() {
        val result = ActionResult.RequiresCapability(Capability.Shizuku)
        val wire = DispatchSettlementCodec.encode("a.b", result)
        assertEquals(DispatchSettlementCodec.REQUIRES_CAPABILITY, wire.status)
        assertEquals(
            result,
            DispatchSettlementCodec.decode(wire.status, wire.reason, wire.capability, wire.message, wire.userMessage),
        )
    }

    @Test
    fun `未知序号回退为失败而不是崩溃`() {
        val decoded = DispatchSettlementCodec.decode(
            status = 99,
            reason = 99,
            capability = 99,
            message = "boom",
            userMessage = null,
        )
        assertEquals(ActionResult.Failed(ActionFailure.ExecutionFailed, "boom"), decoded)
    }
}
