package hunoia.luno.action.executor

import android.content.ContextWrapper
import android.net.Uri
import hunoia.luno.action.controller.IntentController
import hunoia.luno.action.execution.ExecutorContext
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ButtonTarget
import hunoia.luno.core.domain.ActionFailure
import hunoia.luno.core.domain.ActionResult
import hunoia.luno.core.domain.Capability
import hunoia.luno.core.domain.LaunchSpec
import hunoia.luno.core.domain.QueryParam
import hunoia.luno.execute.ExecuteEnvironment
import hunoia.luno.execute.ExecuteMessages
import hunoia.luno.execute.SurfaceHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 启动类执行器：当前应用严格解析与 launchAnchor 的透传语义。 */
class AppExecutorsTest {

    private val launchAnchor = 120 to 340
    private val touchPosition = 900 to 1600

    private class FakeEnvironment(var foreground: String?) : ExecuteEnvironment {
        override val appContext = ContextWrapper(null)
        override fun currentPackageName(): String? = foreground
    }

    private class RecordingSurfaces : SurfaceHost {
        var launchedSpec: LaunchSpec? = null
        var popup: Pair<String, Pair<Int, Int>?>? = null
        var miniWindow: Pair<String, Pair<Int, Int>?>? = null
        var openUrlAnchor: Pair<Int, Int>? = null

        override fun showQuickSearch() = Unit
        override fun showSpeechRecognition(): Boolean = false
        override fun showGridPanel(panelId: String, anchorX: Int, anchorY: Int) = Unit
        override fun gridPanelExists(panelId: String): Boolean = true
        override fun showVolumeScrub(stepThresholdDp: Int?): Boolean = false
        override fun showPointer(): Boolean = false
        override fun hideGestureButton(buttonId: String, delayMs: Long) = Unit
        override suspend fun previousApp() = Unit
        override suspend fun launchApp(spec: LaunchSpec): Boolean {
            launchedSpec = spec
            return true
        }
        override suspend fun openUrl(
            url: String,
            miniWindow: Boolean,
            queryParameters: List<QueryParam>,
            anchor: Pair<Int, Int>?,
        ): Boolean {
            openUrlAnchor = anchor
            return true
        }
        override suspend fun launchShortcut(dataJson: String): Boolean = true
        override suspend fun openAppDetails(packageName: String, userId: Int): ActionResult =
            ActionResult.Success()
        override suspend fun launchInPopup(packageName: String, anchor: Pair<Int, Int>?): ActionResult {
            popup = packageName to anchor
            return ActionResult.Success()
        }
        override suspend fun launchMiniWindow(packageName: String, anchor: Pair<Int, Int>?): ActionResult {
            miniWindow = packageName to anchor
            return ActionResult.Success()
        }
        override fun shareFile(): ActionResult = ActionResult.Success()
        override fun showScreenPick(): Boolean = false
        override fun showScreenPickPortal(x: Int, y: Int, sessionId: String?): Boolean = false
        override fun showScreenAnnotate(): Boolean = false
        override fun openImageAnnotate(): Boolean = false
        override fun showOcrSegmentText(text: String): Boolean = false
        override suspend fun showClipboardOcr(uri: Uri, mimeType: String): ActionResult =
            ActionResult.Success()
        override suspend fun setFloatBallVisible(visible: Boolean?, hideForMs: Long): ActionResult =
            ActionResult.Success()
        override suspend fun setButtonsVisible(
            visible: Boolean,
            target: ButtonTarget,
            buttonIds: List<String>,
            hideForMs: Long,
        ): ActionResult = ActionResult.Success()
    }

    private object FakeMessages : ExecuteMessages {
        override fun failureText(reason: ActionFailure): String = ""
        override fun capabilityText(capability: Capability): String = ""
        override fun showToast(text: String) = Unit
    }

    private fun context(
        environment: ExecuteEnvironment,
        surfaces: SurfaceHost,
        foregroundHint: String? = null,
    ) = ExecutorContext(
        environment = environment,
        surfaces = surfaces,
        messages = FakeMessages,
        scope = CoroutineScope(Job()),
        touchPosition = touchPosition,
        launchAnchor = launchAnchor,
        foregroundHint = foregroundHint,
    )

    @Test
    fun `launch app uses launch anchor instead of touch position`() = runBlocking {
        val surfaces = RecordingSurfaces()
        AppExecutors().execute(
            Action.LaunchApp(packageName = "com.example", miniWindow = true),
            context(FakeEnvironment(null), surfaces),
        )
        assertEquals(launchAnchor, surfaces.launchedSpec?.anchor)
    }

    @Test
    fun `open url forwards launch anchor`() = runBlocking {
        val surfaces = RecordingSurfaces()
        IntentExecutors(IntentController(ContextWrapper(null))).execute(
            Action.OpenUrl(url = "https://example.com", miniWindow = true),
            context(FakeEnvironment(null), surfaces),
        )
        assertEquals(launchAnchor, surfaces.openUrlAnchor)
    }

    @Test
    fun `popup with no foreground app fails without launching`() = runBlocking {
        val surfaces = RecordingSurfaces()
        val result = AppExecutors().execute(Action.Popup, context(FakeEnvironment(null), surfaces))
        assertEquals(ActionFailure.NoForegroundApp, (result as ActionResult.Failed).reason)
        assertNull(surfaces.popup)
    }

    @Test
    fun `mini window with no foreground app fails without launching`() = runBlocking {
        val surfaces = RecordingSurfaces()
        val result = AppExecutors().execute(Action.MiniWindow, context(FakeEnvironment(""), surfaces))
        assertEquals(ActionFailure.NoForegroundApp, (result as ActionResult.Failed).reason)
        assertNull(surfaces.miniWindow)
    }

    @Test
    fun `popup and mini window use foreground app with launch anchor`() = runBlocking {
        val surfaces = RecordingSurfaces()
        val executor = AppExecutors()
        executor.execute(Action.Popup, context(FakeEnvironment("com.wechat"), surfaces))
        executor.execute(Action.MiniWindow, context(FakeEnvironment("com.wechat"), surfaces))
        assertEquals("com.wechat" to launchAnchor, surfaces.popup)
        assertEquals("com.wechat" to launchAnchor, surfaces.miniWindow)
    }

    @Test
    fun `luno itself is allowed when it is the real foreground app`() = runBlocking {
        val surfaces = RecordingSurfaces()
        AppExecutors().execute(Action.MiniWindow, context(FakeEnvironment("hunoia.luno"), surfaces))
        assertEquals("hunoia.luno" to launchAnchor, surfaces.miniWindow)
    }

    @Test
    fun `foreground hint overrides live window resolution`() = runBlocking {
        val surfaces = RecordingSurfaces()
        AppExecutors().execute(
            Action.MiniWindow,
            context(FakeEnvironment(null), surfaces, foregroundHint = "com.android.settings"),
        )
        assertEquals("com.android.settings" to launchAnchor, surfaces.miniWindow)
    }

    @Test
    fun `empty hint means no foreground app even if live resolution succeeds`() = runBlocking {
        val surfaces = RecordingSurfaces()
        val popup = AppExecutors().execute(
            Action.Popup,
            context(FakeEnvironment("com.wechat"), surfaces, foregroundHint = ""),
        )
        val mini = AppExecutors().execute(
            Action.MiniWindow,
            context(FakeEnvironment("com.wechat"), surfaces, foregroundHint = ""),
        )
        assertEquals(ActionFailure.NoForegroundApp, (popup as ActionResult.Failed).reason)
        assertEquals(ActionFailure.NoForegroundApp, (mini as ActionResult.Failed).reason)
        assertNull(surfaces.popup)
        assertNull(surfaces.miniWindow)
    }

    @Test
    fun `null hint falls back to live window resolution`() = runBlocking {
        val surfaces = RecordingSurfaces()
        AppExecutors().execute(
            Action.MiniWindow,
            context(FakeEnvironment("com.wechat"), surfaces, foregroundHint = null),
        )
        assertEquals("com.wechat" to launchAnchor, surfaces.miniWindow)
    }
}
