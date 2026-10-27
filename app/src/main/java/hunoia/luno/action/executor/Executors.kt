package hunoia.luno.action.executor

import hunoia.luno.R
import hunoia.luno.action.controller.AccessibilityController
import hunoia.luno.action.controller.IntentController
import hunoia.luno.action.controller.ShellController
import hunoia.luno.action.execution.ActionExecutor
import hunoia.luno.action.execution.ExecutorContext
import hunoia.luno.action.model.Action
import hunoia.luno.core.domain.ActionFailure
import hunoia.luno.core.domain.ActionResult
import hunoia.luno.action.model.resolveButtonTargetIds
import hunoia.luno.bridge.system.copyText
import hunoia.luno.config.ConfigProvider
import hunoia.luno.core.Events
import hunoia.luno.runtime.floatball.FloatBallService
import hunoia.luno.runtime.floatball.FloatBallTempHideEvent
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import android.view.Choreographer
import kotlin.coroutines.resume

/** 注入触点前隐藏触钮的时长：要盖住整段注入（tap 100ms / 长按 durationMs）之后再让触钮回来。 */
private const val BUTTON_HIDE_MS = 250L

/** 隐藏触钮后等它真正不可触的帧数（WM 事务要等一次 traversal 才落到输入分发）。 */
private const val BUTTON_HIDE_SETTLE_FRAMES = 2

class AccessibilityExecutors(private val controller: AccessibilityController) : ActionExecutor {

    override val supportedTypes: Set<String> = setOf(
        "accessibility.back", "accessibility.home", "accessibility.recents",
        "accessibility.tap", "accessibility.longPress", "accessibility.doubleTap",
        "accessibility.scroll", "accessibility.swipe",
        "accessibility.screenshot", "accessibility.screenPick", "accessibility.screenLinks",
        "accessibility.splitScreen",
        "accessibility.powerButton", "accessibility.notificationPanel",
        "accessibility.quickPanel", "accessibility.lockScreen"
    )

    override suspend fun execute(action: Action, context: ExecutorContext): ActionResult {
        return when (action) {
            is Action.Back -> executionResult(controller.back())
            is Action.Home -> executionResult(controller.home())
            is Action.Recents -> executionResult(controller.recents())
            is Action.Tap -> {
                val (x, y) = action.targetPoint(context)
                    ?: return ActionResult.Failed(ActionFailure.InvalidParameter)
                hideButtonAndSettle(context)
                executionResult(controller.click(x, y))
            }
            is Action.LongPress -> {
                val (x, y) = action.targetPoint(context)
                    ?: return ActionResult.Failed(ActionFailure.InvalidParameter)
                hideButtonAndSettle(context)
                executionResult(controller.longPress(x, y, action.durationMs))
            }
            is Action.Swipe -> {
                val performed = when (action.direction) {
                    hunoia.luno.action.model.SwipeDirection.UP -> controller.swipeUp()
                    hunoia.luno.action.model.SwipeDirection.DOWN -> controller.swipeDown()
                    hunoia.luno.action.model.SwipeDirection.LEFT -> controller.swipeLeft()
                    hunoia.luno.action.model.SwipeDirection.RIGHT -> controller.swipeRight()
                    hunoia.luno.action.model.SwipeDirection.TO_TOP -> controller.scrollToTop()
                }
                executionResult(performed)
            }
            is Action.DoubleTap -> {
                val (x, y) = action.targetPoint(context)
                    ?: return ActionResult.Failed(ActionFailure.InvalidParameter)
                hideButtonAndSettle(context)
                executionResult(controller.doubleTap(x, y))
            }
            is Action.ScrollFocused -> executionResult(controller.scrollFocused(action.forward))
            is Action.Screenshot -> {
                // fire-and-forget：takeScreenshot 的回调是异步且无失败语义，
                // 这里拿不到真实成败，统一按成功收口（失败静默）。
                context.scope.launch {
                    delay(200)
                    controller.takeScreenshot()
                }
                ActionResult.Success()
            }
            is Action.ScreenPick -> {
                // 拾取层必须挂在无障碍服务窗口上（TYPE_ACCESSIBILITY_OVERLAY 规则，
                // 与屏幕取点同款）；执行能走到这里就说明 a11y 侧活着。
                val shown = hunoia.luno.service.SideGestureService.current?.showScreenPick() == true
                if (shown) ActionResult.Success() else ActionResult.Failed(ActionFailure.ExecutionFailed)
            }
            is Action.ScreenLinks -> {
                // 与 screenPick 同款：只在无障碍服务里的 overlay 层上挂窗。
                val shown = hunoia.luno.service.SideGestureService.current?.showScreenLinks() == true
                if (shown) ActionResult.Success() else ActionResult.Failed(ActionFailure.ExecutionFailed)
            }
            is Action.SplitScreen -> executionResult(controller.toggleSplitScreen())
            is Action.PowerButton -> executionResult(controller.powerDialog())
            is Action.NotificationPanel -> executionResult(controller.notificationPanel())
            is Action.QuickPanel -> executionResult(controller.quickPanel())
            is Action.LockScreen -> executionResult(controller.lockScreen())
            else -> ActionResult.Failed(ActionFailure.Unsupported)
        }
    }

    private fun executionResult(performed: Boolean): ActionResult =
        if (performed) ActionResult.Success() else ActionResult.Failed(ActionFailure.ExecutionFailed)

    /**
     * 点击/长按落点：勾了「使用触发位置坐标」只认手势触点，否则参数坐标优先再退触点。
     * 两者都没有判参数无效（分发器已前置拦截，这里兜底直接执行路径）。
     */
    private fun Action.Tap.targetPoint(context: ExecutorContext): Pair<Int, Int>? =
        if (useTriggerPoint) context.touchPosition
        else pointPx(context.appContext) ?: context.touchPosition

    private fun Action.LongPress.targetPoint(context: ExecutorContext): Pair<Int, Int>? =
        if (useTriggerPoint) context.touchPosition
        else pointPx(context.appContext) ?: context.touchPosition

    private fun Action.DoubleTap.targetPoint(context: ExecutorContext): Pair<Int, Int>? =
        if (useTriggerPoint) context.touchPosition
        else pointPx(context.appContext) ?: context.touchPosition

    /**
     * 先隐藏触钮并等它真的不可触再注入：`hideGestureButton` 只加 `FLAG_NOT_TOUCHABLE`
     * 并发一笔 WM 事务，不同步生效；不等落地，点击会被触钮自己的捕获窗口吃掉。
     * WMS 从 App 侧观测不到，按帧等待是最近似做法（主 overlay 本身不挡注入）。
     */
    private suspend fun hideButtonAndSettle(context: ExecutorContext) {
        context.hideGestureButton(BUTTON_HIDE_MS)
        awaitFrames(BUTTON_HIDE_SETTLE_FRAMES)
    }

    /** 等 N 个 vsync（走 Choreographer，分发链路都在 Main 上）。 */
    private suspend fun awaitFrames(count: Int) = suspendCancellableCoroutine { cont ->
        val choreographer = Choreographer.getInstance()
        var remaining = count
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                remaining--
                if (remaining > 0) choreographer.postFrameCallback(this) else cont.resume(Unit)
            }
        }
        choreographer.postFrameCallback(callback)
        cont.invokeOnCancellation { choreographer.removeFrameCallback(callback) }
    }

    /** 参数坐标转屏像素（dp→px，按执行时 density）；任一缺失/为负视为未填，调用方回退触点。 */
    private fun Action.Tap.pointPx(context: android.content.Context): Pair<Int, Int>? =
        pointPx(context, xDp, yDp)

    private fun Action.LongPress.pointPx(context: android.content.Context): Pair<Int, Int>? =
        pointPx(context, xDp, yDp)

    private fun Action.DoubleTap.pointPx(context: android.content.Context): Pair<Int, Int>? =
        pointPx(context, xDp, yDp)

    private fun pointPx(context: android.content.Context, xDp: Int?, yDp: Int?): Pair<Int, Int>? {
        if (xDp == null || yDp == null || xDp < 0 || yDp < 0) return null
        val density = context.resources.displayMetrics.density
        return (xDp * density).roundToInt() to (yDp * density).roundToInt()
    }
}

class IntentExecutors(private val intentController: IntentController) : ActionExecutor {

    override val supportedTypes: Set<String> = setOf(
        "intent.openUrl", "intent.shareText", "intent.shareFile", "intent.assist"
    )

    override suspend fun execute(action: Action, context: ExecutorContext): ActionResult {
        return when (action) {
            is Action.OpenUrl -> {
                val ok = context.openUrl(action.url, action.miniWindow, action.queryParameters)
                if (ok) ActionResult.Success() else ActionResult.Failed(ActionFailure.ExecutionFailed)
            }
            is Action.ShareText -> intentController.shareText(action.text, action.mimeType)
            is Action.ShareFile -> intentController.shareFile()
            is Action.Assist -> intentController.launchAssist()
            else -> ActionResult.Failed(ActionFailure.Unsupported)
        }
    }
}

class InternalExecutors : ActionExecutor {

    override val supportedTypes: Set<String> = setOf(
        Action.NONE_TYPE_ID, "internal.subGesture",
        "internal.buttonsVisibility", "internal.floatBallVisibility",
        "internal.quickAppLauncher", "internal.quickSearch", "internal.gridPanel",
        "internal.volumeScrub",
        "internal.pointer",
        "internal.previousApp", "internal.runRule"
    )

    override suspend fun execute(action: Action, context: ExecutorContext): ActionResult {
        return when (action) {
            is Action.None -> ActionResult.Success()
            is Action.SubGesture -> ActionResult.Success()
            is Action.ButtonsVisibility -> executeButtonsVisibility(action, context)
            is Action.FloatBallVisibility -> executeFloatBallVisibility(action, context)
            is Action.QuickAppLauncher -> {
                // 无触点路径（自动化/库测试）退屏幕中心；浮窗侧再夹取屏内。
                val anchor = context.touchPosition ?: run {
                    val dm = context.appContext.resources.displayMetrics
                    dm.widthPixels / 2 to dm.heightPixels / 2
                }
                context.toggleQuickAppLauncher(anchor.first, anchor.second)
                ActionResult.Success()
            }
            is Action.QuickSearch -> {
                context.showQuickSearch()
                ActionResult.Success()
            }
            is Action.GridPanel -> executeGridPanel(action, context)
            is Action.VolumeScrub -> {
                if (context.showVolumeScrub(action.stepThresholdDp)) {
                    ActionResult.Success()
                } else {
                    ActionResult.Failed(ActionFailure.ExecutionFailed)
                }
            }
            is Action.Pointer -> {
                // 已有会话时 begin 拒绝重复开启，按幂等成功收口。
                context.showPointer()
                ActionResult.Success()
            }
            is Action.PreviousApp -> {
                context.previousApp()
                ActionResult.Success()
            }
            is Action.RunRule -> {
                context.runRule?.invoke(action.ruleId, action.params, context)
                    ?: ActionResult.Failed(ActionFailure.Unsupported)
            }
            else -> ActionResult.Failed(ActionFailure.Unsupported)
        }
    }

    /**
     * 网格面板：面板被删时按「无动作」静默收口，不报失败让手势链路弹错误提示。
     * 空面板照常弹——面板本体就是编辑入口，拦掉空面板第一项永远加不上。
     * 浮窗入口走注入的 `context.showGridPanel`，不直接摸浮窗。
     */
    private fun executeGridPanel(action: Action.GridPanel, context: ExecutorContext): ActionResult {
        if (!context.gridPanelExists(action.panelId)) {
            return ActionResult.Success()
        }
        // 无触点路径（自动化/库测试）退屏幕中心；浮窗侧再夹取屏内。
        val anchor = context.touchPosition ?: run {
            val dm = context.appContext.resources.displayMetrics
            dm.widthPixels / 2 to dm.heightPixels / 2
        }
        context.showGridPanel(action.panelId, anchor.first, anchor.second)
        return ActionResult.Success()
    }

    /**
     * 悬浮球显隐：`hideForMs > 0` 走 [FloatBallTempHideEvent] 临时隐藏（FloatBallService
     * 内存到期恢复，只摘窗口、不碰 enabled 存量，进程被杀即失效）；否则改
     * [hunoia.luno.config.model.FloatBallSettings].enabled 存量。
     * visible=null 为切换；`FloatBallService` 的 settings flow 会自动摘/挂球窗。
     * 失败吞掉报 Failed，不抛。
     */
    private suspend fun executeFloatBallVisibility(action: Action.FloatBallVisibility, context: ExecutorContext): ActionResult {
        if (action.hideForMs > 0) {
            Events.post(FloatBallTempHideEvent(action.hideForMs))
            return ActionResult.Success()
        }
        val ok = runCatching {
            var enabled = false
            ConfigProvider.updateActionSettings { current ->
                enabled = action.visible ?: !current.floatBall.enabled
                current.copy(floatBall = current.floatBall.copy(enabled = enabled))
            }
            // 服务不在跑的场景（装更新时 restore 读到关闭态没拉球，随后规则把球启用）：
            // 只写盘上的值没有 settings flow 消费，必须把服务拉起来球才会回来。
            if (enabled) FloatBallService.start(context.appContext)
            true
        }.getOrDefault(false)
        return if (ok) ActionResult.Success() else ActionResult.Failed(ActionFailure.ExecutionFailed)
    }

    /**
     * 触钮显隐：`hideForMs > 0` 走 [ExecutorContext.hideGestureButtons] 临时隐藏
     *（ButtonHideRuntime 到期自动恢复，不碰 enabled 存量，按 target/buttonIds 选作用钮，
     * 原「隐藏触钮」动作语义）；否则直接改按钮 enabled 存量。DataStore 更新自带顺序，后写覆盖先写，
     * 即规则列表顺序=优先级。
     */
    private suspend fun executeButtonsVisibility(action: Action.ButtonsVisibility, context: ExecutorContext): ActionResult {
        if (action.hideForMs > 0) {
            context.hideGestureButtons(action.target, action.buttonIds, action.hideForMs)
            return ActionResult.Success()
        }
        val ok = runCatching {
            ConfigProvider.updateGestureButtons { list ->
                val ids = resolveButtonTargetIds(action.target, list.map { it.id }, action.buttonIds)
                list.map { button ->
                    if (button.id in ids) button.copy(enabled = action.visible) else button
                }
            }
            true
        }.getOrDefault(false)
        return if (ok) ActionResult.Success() else ActionResult.Failed(ActionFailure.ExecutionFailed)
    }
}

class ShellExecutor(private val shellController: ShellController) : ActionExecutor {

    override val supportedTypes: Set<String> = setOf("shell.custom")

    override suspend fun execute(action: Action, context: ExecutorContext): ActionResult {
        return when (action) {
            is Action.CustomCommand -> {
                val command = action.command.trim()
                val result = shellController.runCommand(command)
                if (action.copyOutput && result.output.isNotBlank()) {
                    copyText(
                        context.appContext,
                        context.appContext.getString(R.string.action_shell_clipboard_label),
                        result.output,
                    )
                }
                val message = if (action.showToast) {
                    if (result.success) {
                        result.output.ifBlank { "No output" }
                    } else {
                        result.error ?: result.output.ifBlank { "Unknown error" }
                    }.take(500)
                } else {
                    null
                }
                if (result.success) ActionResult.Success(message) else ActionResult.Failed(ActionFailure.ExecutionFailed, message)
            }
            else -> ActionResult.Failed(ActionFailure.Unsupported)
        }
    }
}
