package hunoia.luno.action.api

import android.accessibilityservice.AccessibilityService
import android.content.Context
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.GestureSettings
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ActionExecutorTest {

    @Test
    fun `unregistered action returns Ignored`() = runBlocking {
        val executor = ActionExecutor(listOf(successHandler))
        val result = executor.execute(Action("unknown"), fakeContext())
        assertEquals(ActionExecutionResult.Ignored, result)
    }

    @Test
    fun `resolver returning null returns Ignored`() = runBlocking {
        val executor = ActionExecutor(
            handlers = listOf(successHandler),
            resolveAction = { null },
        )
        val result = executor.execute(Action("success"), fakeContext())
        assertEquals(ActionExecutionResult.Ignored, result)
    }

    @Test
    fun `handler success returns Success`() = runBlocking {
        val executor = ActionExecutor(listOf(successHandler))
        val result = executor.execute(Action("success"), fakeContext())
        assertEquals(ActionExecutionResult.Success, result)
    }

    @Test
    fun `handler exception returns Failed`() = runBlocking {
        val executor = ActionExecutor(listOf(failHandler))
        val result = executor.execute(Action("fail"), fakeContext())
        assertEquals(ActionExecutionResult.Failed("boom"), result)
    }

    @Test
    fun `duplicate actionId throws on construction`() {
        val exception = assertThrows(IllegalStateException::class.java) {
            ActionExecutor(listOf(FakeHandler("dup"), FakeHandler("dup")))
        }
        assertNotNull(exception.message)
    }

    private fun fakeContext() = ActionHandlerContext(
        accessibilityService = mockk(relaxed = true),
        appContext = mockk(relaxed = true),
        scope = CoroutineScope(Dispatchers.Default),
        actionSettings = ActionSettings(),
        advancedSettings = AdvancedSettings(),
        gestureSettings = GestureSettings(),
        showToast = {},
        showLongToast = {},
    )

    private class FakeHandler(
        override val supportedActions: Set<String>,
        private val result: ActionExecutionResult = ActionExecutionResult.Success,
    ) : ActionHandler {
        constructor(id: String, result: ActionExecutionResult) : this(setOf(id), result)
        constructor(id: String) : this(setOf(id))

        override suspend fun handle(action: Action, context: ActionHandlerContext): ActionExecutionResult = result
    }

    private val successHandler = FakeHandler("success")
    private val failHandler = FakeHandler("fail", ActionExecutionResult.Failed("boom"))
}
