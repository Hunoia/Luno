package hunoia.luno.runtime.automation

import hunoia.luno.action.model.ActionFailure
import hunoia.luno.action.model.ActionResult
import hunoia.luno.core.domain.StepOutcome
import hunoia.luno.action.model.Capability
import hunoia.luno.action.model.StoredAction
import hunoia.luno.config.model.NetworkType
import hunoia.luno.config.model.RingerMode
import hunoia.luno.config.model.RuleStep
import hunoia.luno.config.model.RuleStepKind
import hunoia.luno.config.model.StepFailure
import hunoia.luno.runtime.condition.ConditionContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 步骤失败策略回归：STOP / SKIP / RETRY 与「能力缺失不重试」。 */
class SequenceExecutorFailurePolicyTest {

    private fun ctx() = ConditionContext(
        nowMs = 0L,
        packageName = "",
        isLockScreen = false,
        isLauncher = false,
        isLandscape = false,
        isKeyboardInput = false,
        isCharging = false,
        batteryLevel = 50,
        minuteOfDay = 0,
        dayOfWeek = 1,
        networkType = NetworkType.NONE,
        headphonesConnected = false,
        bluetoothAdapterOn = null,
        bluetoothAudioConnected = null,
        airplaneMode = false,
        ringerMode = RingerMode.NORMAL,
        volumePercent = emptyMap(),
        isMusicPlaying = false,
        isTorchOn = false,
        isDndOn = false,
        appChangedTo = null,
        screenEvent = null,
        chargingChangedTo = null,
        volumeChanged = null,
    )

    private fun actionStep(
        onFailure: String = StepFailure.STOP,
        retryCount: Int = 0,
    ) = RuleStep(
        kind = RuleStepKind.ACTION,
        action = StoredAction.of("test.action"),
        onFailure = onFailure,
        retryCount = retryCount,
        retryIntervalMs = 0L,
    )

    private fun executor(
        failuresBeforeSuccess: Int = 0,
        result: () -> ActionResult = {
            ActionResult.Failed(ActionFailure.ExecutionFailed, "boom")
        },
    ): Pair<SequenceExecutor, () -> Int> {
        var calls = 0
        val executor = SequenceExecutor(
            runSingleAction = { _, _, _ ->
                calls++
                if (calls <= failuresBeforeSuccess) {
                    StepOutcome.Done(result())
                } else {
                    StepOutcome.Done(ActionResult.Success())
                }
            },
            resolveTemplate = { text, _, _ -> text },
        )
        return executor to { calls }
    }

    @Test
    fun `stop interrupts after first failure`() = runBlocking {
        val (executor, calls) = executor(failuresBeforeSuccess = 5)
        val records = executor.runSteps(
            listOf(actionStep(), actionStep()),
            ctx(),
        )
        assertEquals(1, calls())
        assertEquals(1, records.size)
        assertFalse(records[0].ok)
        assertEquals("boom", records[0].message)
    }

    @Test
    fun `skip records failure and continues`() = runBlocking {
        val (executor, calls) = executor(failuresBeforeSuccess = 1)
        val records = executor.runSteps(
            listOf(
                actionStep(onFailure = StepFailure.SKIP),
                actionStep(),
            ),
            ctx(),
        )
        assertEquals(2, calls())
        assertEquals(2, records.size)
        assertFalse(records[0].ok)
        assertEquals("boom", records[0].message)
        assertTrue(records[0].skipped)
        assertTrue(records[1].ok)
    }

    @Test
    fun `retry succeeds after configured attempts`() = runBlocking {
        val (executor, calls) = executor(failuresBeforeSuccess = 2)
        val records = executor.runSteps(
            listOf(actionStep(onFailure = StepFailure.RETRY, retryCount = 2)),
            ctx(),
        )
        assertEquals(3, calls())
        assertEquals(1, records.size)
        assertTrue(records[0].ok)
        assertEquals("dispatched_retry_2", records[0].message)
    }

    @Test
    fun `retry exhausted falls back to stop`() = runBlocking {
        val (executor, calls) = executor(failuresBeforeSuccess = 5)
        val records = executor.runSteps(
            listOf(
                actionStep(onFailure = StepFailure.RETRY, retryCount = 1),
                actionStep(),
            ),
            ctx(),
        )
        assertEquals(2, calls())
        assertEquals(1, records.size)
        assertFalse(records[0].ok)
        assertTrue(records[0].message.contains("attempts=2"))
    }

    @Test
    fun `requires capability is not retried`() = runBlocking {
        val (executor, calls) = executor(
            failuresBeforeSuccess = 5,
            result = { ActionResult.RequiresCapability(Capability.Accessibility) },
        )
        val records = executor.runSteps(
            listOf(actionStep(onFailure = StepFailure.RETRY, retryCount = 5)),
            ctx(),
        )
        assertEquals(1, calls())
        assertEquals(1, records.size)
        assertFalse(records[0].ok)
        assertEquals("requires_capability", records[0].message)
    }
}
