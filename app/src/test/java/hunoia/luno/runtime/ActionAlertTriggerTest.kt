package hunoia.luno.runtime

import hunoia.luno.core.domain.ActionRequest
import hunoia.luno.core.domain.ActionResult
import hunoia.luno.core.domain.DispatchOrigin
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.execute.ExecuteSettlement
import hunoia.luno.runtime.floatball.FloatBallCommand
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 动作落定 → 动效触发：只认成功，动作库引用只传条目 id。 */
class ActionAlertTriggerTest {

    private var observed: Pair<String?, String?>? = null

    init {
        FloatBallCommand.playActionAlert = { typeId, entryId ->
            observed = typeId to entryId
        }
    }

    @After
    fun tearDown() {
        FloatBallCommand.playActionAlert = null
        observed = null
    }

    private fun settlement(stored: StoredAction, result: ActionResult): ExecuteSettlement =
        ExecuteSettlement(
            ActionRequest(stored = stored, origin = DispatchOrigin.GESTURE),
            stored.typeId,
            result,
        )

    @Test
    fun `successful type action passes its type id`() {
        ActionAlertTrigger.observe(
            settlement(StoredAction("system.back"), ActionResult.Success()),
        )
        assertEquals("system.back" to null, observed)
    }

    @Test
    fun `library reference resolves to the entry binding only`() {
        ActionAlertTrigger.observe(
            settlement(
                StoredAction.of("internal.libraryRef", "entryId" to "entry-1"),
                ActionResult.Success(),
            ),
        )
        assertEquals(null to "entry-1", observed)
    }

    @Test
    fun `failed or unauthorized actions do not play`() {
        val failed = ActionResult.Failed(hunoia.luno.core.domain.ActionFailure.ExecutionFailed)
        ActionAlertTrigger.observe(settlement(StoredAction("system.back"), failed))
        assertNull(observed)
        assertEquals(null, observed?.first)
    }
}