package hunoia.luno.action.execution

import android.content.ContextWrapper
import android.net.Uri
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ButtonTarget
import hunoia.luno.core.domain.ActionFailure
import hunoia.luno.core.domain.ActionResult
import hunoia.luno.core.domain.Capability
import hunoia.luno.core.domain.LaunchSpec
import hunoia.luno.core.domain.QueryParam
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.execute.ExecuteEnvironment
import hunoia.luno.execute.ExecuteMessages
import hunoia.luno.execute.SurfaceHost
import hunoia.luno.actions.LunoActionCatalog
import hunoia.luno.actions.LunoActionFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 执行注册表与参数解析层回归：分发口的三态出口（Unsupported / RequiresCapability /
 * Failed）与必填参数语义都在这里收口，执行器本身不做校验。
 */
class ActionExecutionTest {

    private class FakeExecutor(
        override val supportedTypes: Set<String>,
        private val onExecute: suspend (Action) -> ActionResult = { ActionResult.Success() },
    ) : ActionExecutor {
        val executed = mutableListOf<Action>()
        override suspend fun execute(action: Action, context: ExecutorContext): ActionResult {
            executed += action
            return onExecute(action)
        }
    }

    private class FakeEnvironment(private val context: ContextWrapper) : ExecuteEnvironment {
        override val appContext: ContextWrapper = context
        override fun currentPackageName(): String? = null
    }

    private class FakeSurfaceHost : SurfaceHost {
        override fun showQuickSearch() = Unit
        override fun showSpeechRecognition(): Boolean = false
        override fun showGridPanel(panelId: String, anchorX: Int, anchorY: Int) = Unit
        override fun gridPanelExists(panelId: String): Boolean = true
        override fun showVolumeScrub(stepThresholdDp: Int?): Boolean = false
        override fun showPointer(): Boolean = false
        override fun hideGestureButton(buttonId: String, delayMs: Long) = Unit
        override suspend fun previousApp() = Unit
        override suspend fun launchApp(spec: LaunchSpec): Boolean = false
        override suspend fun openUrl(
            url: String,
            miniWindow: Boolean,
            queryParameters: List<QueryParam>,
            anchor: Pair<Int, Int>?,
        ): Boolean = false
        override suspend fun launchShortcut(dataJson: String): Boolean = false
        override suspend fun openAppDetails(packageName: String, userId: Int): ActionResult =
            ActionResult.Failed(ActionFailure.Unsupported)
        override suspend fun launchInPopup(packageName: String, anchor: Pair<Int, Int>?): ActionResult =
            ActionResult.Failed(ActionFailure.Unsupported)
        override suspend fun launchMiniWindow(packageName: String, anchor: Pair<Int, Int>?): ActionResult =
            ActionResult.Failed(ActionFailure.Unsupported)
        override fun shareFile(): ActionResult = ActionResult.Failed(ActionFailure.Unsupported)
        override fun showScreenPick(): Boolean = false
        override fun showScreenPickPortal(x: Int, y: Int, sessionId: String?): Boolean = false
        override fun showScreenAnnotate(): Boolean = false
        override fun openImageAnnotate(): Boolean = false
        override fun openPhotoRecognize(): Boolean = false
        override fun showOcrSegmentText(text: String): Boolean = false
        override suspend fun showClipboardOcr(uri: Uri, mimeType: String): ActionResult =
            ActionResult.Failed(ActionFailure.Unsupported)
        override suspend fun setFloatBallVisible(visible: Boolean?, hideForMs: Long): ActionResult =
            ActionResult.Failed(ActionFailure.Unsupported)
        override suspend fun setButtonsVisible(
            visible: Boolean,
            target: ButtonTarget,
            buttonIds: List<String>,
            hideForMs: Long,
        ): ActionResult = ActionResult.Failed(ActionFailure.Unsupported)
    }

    private object FakeMessages : ExecuteMessages {
        override fun failureText(reason: ActionFailure): String = ""
        override fun capabilityText(capability: Capability): String = ""
        override fun showToast(text: String) = Unit
    }

    private fun executorContext() = ExecutorContext(
        environment = FakeEnvironment(ContextWrapper(null)),
        surfaces = FakeSurfaceHost(),
        messages = FakeMessages,
        scope = CoroutineScope(Job()),
    )

    private fun stored(typeId: String, vararg params: Pair<String, String>) = StoredAction(
        typeId,
        JsonObject(params.associate { (k, v) -> k to JsonPrimitive(v) }),
    )

    private fun registry(
        executors: List<ActionExecutor>,
        isCapabilityAvailable: (Capability) -> Boolean = { true },
    ) = ActionExecutorRegistry(executors, LunoActionCatalog, isCapabilityAvailable)

    private fun resolver(registry: ActionExecutorRegistry) =
        ActionResolver(registry, LunoActionCatalog, LunoActionFactory)

    @Test
    fun `duplicate type id registration is rejected`() {
        val duplicate = FakeExecutor(setOf("custom.a"))
        try {
            registry(listOf(duplicate, FakeExecutor(setOf("custom.a"))))
            fail("重复 typeId 应在构造时炸出来")
        } catch (_: IllegalStateException) {
        }
    }

    @Test
    fun `unregistered type yields unsupported`() = runBlocking {
        val registry = registry(listOf(FakeExecutor(setOf("shell.custom"))))
        assertEquals(
            ActionResult.Failed(ActionFailure.Unsupported),
            registry.execute(Action.None, executorContext()),
        )
    }

    @Test
    fun `unavailable capability short-circuits before executing`() = runBlocking {
        val executor = FakeExecutor(setOf("accessibility.lockScreen"))
        val registry = registry(listOf(executor), isCapabilityAvailable = { false })
        assertEquals(
            ActionResult.RequiresCapability(Capability.Accessibility),
            registry.execute(Action.LockScreen, executorContext()),
        )
        assertTrue(executor.executed.isEmpty())
    }

    @Test
    fun `available capability lets the executor run`() = runBlocking {
        val executor = FakeExecutor(setOf("accessibility.lockScreen"))
        val registry = registry(listOf(executor), isCapabilityAvailable = { true })
        assertEquals(
            ActionResult.Success(),
            registry.execute(Action.LockScreen, executorContext()),
        )
        assertEquals(listOf<Action>(Action.LockScreen), executor.executed)
    }

    @Test
    fun `executor exception becomes execution failed`() = runBlocking {
        val boom = FakeExecutor(setOf("shell.custom")) { throw IllegalStateException("boom") }
        val registry = registry(listOf(boom))
        val result = registry.execute(Action.CustomCommand("id"), executorContext())
        assertEquals(ActionFailure.ExecutionFailed, (result as ActionResult.Failed).reason)
        assertEquals("boom", result.message)
    }

    @Test
    fun `cancellation is never swallowed as a failed result`() = runBlocking {
        val cancel = FakeExecutor(setOf("shell.custom")) { throw CancellationException("scope closed") }
        val registry = registry(listOf(cancel))
        try {
            registry.execute(Action.CustomCommand("id"), executorContext())
            fail("CancellationException 必须放行，否则序列/协程取消失效")
        } catch (_: CancellationException) {
        }
    }

    @Test
    fun `blank required text parameter is rejected before executing`() = runBlocking {
        val executor = FakeExecutor(setOf("shell.custom"))
        val resolver = resolver(registry(listOf(executor)))
        val result = resolver.resolveFromLibrary(stored("shell.custom", "command" to "  "), executorContext())
        assertEquals(ActionFailure.InvalidParameter, (result as ActionResult.Failed).reason)
        assertTrue(executor.executed.isEmpty())
    }

    @Test
    fun `params without templates pass through untouched`() = runBlocking {
        val executor = FakeExecutor(setOf("shell.custom"))
        val resolver = resolver(registry(listOf(executor)))
        resolver.resolveFromLibrary(stored("shell.custom", "command" to "id -u"), executorContext())
        assertEquals("id -u", (executor.executed.single() as Action.CustomCommand).command)
    }

    @Test
    fun `unknown type id resolves to unsupported instead of crashing the dispatch`() = runBlocking {
        val resolver = resolver(registry(listOf(FakeExecutor(setOf("shell.custom")))))
        val result = resolver.resolveFromLibrary(stored("nope.thing", "x" to "1"), executorContext())
        assertEquals(ActionFailure.Unsupported, (result as ActionResult.Failed).reason)
    }

    @Test
    fun `invalid enum parameter resolves to invalid parameter with the key named`() = runBlocking {
        val resolver = resolver(registry(emptyList()))
        val result = resolver.resolveFromLibrary(
            stored("system.volume", "direction" to "bogus"),
            executorContext(),
        )
        val failed = result as ActionResult.Failed
        assertEquals(ActionFailure.InvalidParameter, failed.reason)
        assertTrue(failed.message.orEmpty().contains("direction"))
    }
}
