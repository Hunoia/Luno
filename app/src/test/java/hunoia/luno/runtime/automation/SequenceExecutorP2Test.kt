package hunoia.luno.runtime.automation

import hunoia.luno.core.domain.ActionResult
import hunoia.luno.core.domain.StepOutcome
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.config.model.BooleanMode
import hunoia.luno.config.model.ConditionItem
import hunoia.luno.config.model.ConditionType
import hunoia.luno.config.model.NetworkType
import hunoia.luno.config.model.RingerMode
import hunoia.luno.config.model.RuleStep
import hunoia.luno.config.model.RuleStepKind
import hunoia.luno.config.model.VariableCompareOp
import hunoia.luno.config.model.WhenGroup
import hunoia.luno.runtime.condition.ConditionContext
import hunoia.luno.runtime.condition.compareValues
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SequenceExecutorP2Test {

    private fun ctx(variables: Map<String, String> = emptyMap()) = ConditionContext(
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
        variables = variables,
    )

    private fun action() = RuleStep(
        kind = RuleStepKind.ACTION,
        action = StoredAction.of("test.action"),
    )

    private fun executor() = SequenceExecutor(
        runSingleAction = { _, _, _ -> StepOutcome.Done(ActionResult.Success()) },
        resolveTemplate = { text, _, _ -> text },
    )

    @Test
    fun `bounded repeat runs body exactly count times`() = runBlocking {
        var calls = 0
        val executor = SequenceExecutor(
            runSingleAction = { _, _, _ ->
                calls++
                StepOutcome.Done(ActionResult.Success())
            },
            resolveTemplate = { text, _, _ -> text },
        )
        val result = executor.runStepsWithResult(
            listOf(
                RuleStep(
                    kind = RuleStepKind.REPEAT,
                    repeatCount = 3,
                    thenSteps = listOf(action()),
                ),
            ),
            ctx(),
        )
        assertEquals(3, calls)
        assertEquals(4, result.records.size)
        assertTrue(result.records.all { it.ok })
    }

    @Test
    fun `return stops following steps and exposes return value`() = runBlocking {
        var calls = 0
        val executor = SequenceExecutor(
            runSingleAction = { _, _, _ ->
                calls++
                StepOutcome.Done(ActionResult.Success())
            },
            resolveTemplate = { text, _, _ -> if (text == "ret") "answer" else text },
        )
        val result = executor.runStepsWithResult(
            listOf(
                RuleStep(kind = RuleStepKind.RETURN, varTemplate = "ret"),
                action(),
            ),
            ctx(),
        )
        assertEquals(0, calls)
        assertEquals("answer", result.variables["return"])
        assertEquals(1, result.records.size)
    }

    @Test
    fun `disabled step is recorded but not executed`() = runBlocking {
        var calls = 0
        val executor = SequenceExecutor(
            runSingleAction = { _, _, _ ->
                calls++
                StepOutcome.Done(ActionResult.Success())
            },
            resolveTemplate = { text, _, _ -> text },
        )
        val records = executor.runSteps(
            listOf(action().copy(enabled = false)),
            ctx(),
        )
        assertEquals(0, calls)
        assertTrue(records.single().disabled)
        assertTrue(records.single().ok)
    }

    @Test
    fun `wait until evaluates variable condition without sleeping when already true`() = runBlocking {
        val condition = WhenGroup(
            mode = BooleanMode.AND,
            items = listOf(
                ConditionItem(
                    type = ConditionType.VAR_COMPARE,
                    compareVarName = "count",
                    compareOp = VariableCompareOp.GREATER_OR_EQUALS,
                    compareValue = "3",
                ),
            ),
        )
        val records = executor().runSteps(
            listOf(
                RuleStep(
                    kind = RuleStepKind.WAIT_UNTIL,
                    condition = condition,
                    waitTimeoutMs = 5_000L,
                    waitIntervalMs = 500L,
                ),
            ),
            ctx(variables = mapOf("var.count" to "3")),
        )
        assertTrue(records.single().ok)
        assertTrue(records.single().message.startsWith("condition_met"))
    }

    @Test
    fun `wait until refresh updates context for following steps`() = runBlocking {
        var calls = 0
        val live = ctx().copy(packageName = "com.live")
        val condition = WhenGroup(
            items = listOf(
                ConditionItem(type = ConditionType.FOREGROUND_APP, packageNames = listOf("com.live")),
            ),
        )
        val executor = SequenceExecutor(
            runSingleAction = { _, _, _ ->
                calls++
                StepOutcome.Done(ActionResult.Success())
            },
            resolveTemplate = { text, _, _ -> text },
            refreshContext = { live },
        )
        val result = executor.runStepsWithResult(
            listOf(
                RuleStep(
                    kind = RuleStepKind.WAIT_UNTIL,
                    condition = condition,
                    waitTimeoutMs = 1_000L,
                    waitIntervalMs = 100L,
                ),
                RuleStep(
                    kind = RuleStepKind.IF,
                    condition = condition,
                    thenSteps = listOf(action()),
                ),
            ),
            ctx().copy(packageName = "com.old"),
        )
        assertEquals(1, calls)
        assertTrue(result.records[1].message.startsWith("branch=then"))
    }

    @Test
    fun `variable comparison handles numeric and text operators`() {
        assertTrue(compareValues("10", VariableCompareOp.GREATER, "9"))
        assertFalse(compareValues("10", VariableCompareOp.LESS, "9"))
        assertTrue(compareValues("abc", VariableCompareOp.CONTAINS, "b"))
        assertFalse(compareValues("abc", VariableCompareOp.IS_EMPTY, ""))
        assertTrue(compareValues("", VariableCompareOp.IS_EMPTY, ""))
    }
}
