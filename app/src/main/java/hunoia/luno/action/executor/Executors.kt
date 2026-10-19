package hunoia.luno.action.executor

import hunoia.luno.action.controller.AccessibilityController
import hunoia.luno.action.controller.IntentController
import hunoia.luno.action.controller.PackageController
import hunoia.luno.action.controller.SettingsController
import hunoia.luno.action.controller.ShellController
import hunoia.luno.action.execution.ActionExecutor
import hunoia.luno.action.execution.ExecutorContext
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionFailure
import hunoia.luno.action.model.ActionResult
import hunoia.luno.action.model.ButtonTarget
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.OpenAppOrUrlData
import hunoia.luno.config.model.OpenUrlQueryParameter
import hunoia.luno.config.model.VolumeScrubConfig
import hunoia.luno.config.model.effectiveForAction
import hunoia.luno.config.model.miniWindowSettings
import hunoia.luno.quicklaunch.QuickLaunchFacade
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
        "accessibility.tap", "accessibility.longPress", "accessibility.swipe",
        "accessibility.inputText", "accessibility.screenshot", "accessibility.splitScreen",
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
            is Action.InputText -> executionResult(controller.inputText(action.text))
            is Action.Screenshot -> {
                context.scope.launch {
                    delay(200)
                    controller.takeScreenshot()
                }
                ActionResult.Success()
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
     * 点击/长按的落点。
     *
     * - 勾了「使用触发位置坐标」：只认手势触点，固定坐标整组忽略（隐藏触钮后按下的位置就是落点，
     *   点击直接穿透到触钮覆盖层下面的界面）。
     * - 否则参数坐标优先（库/规则配好的固定点），再退手势触点。
     * 两者都没有判参数无效（分发器前置校验已拦一次，这里兜底直接执行路径）。
     */
    private fun Action.Tap.targetPoint(context: ExecutorContext): Pair<Int, Int>? =
        if (useTriggerPoint) context.touchPosition
        else pointPx(context.appContext) ?: context.touchPosition

    private fun Action.LongPress.targetPoint(context: ExecutorContext): Pair<Int, Int>? =
        if (useTriggerPoint) context.touchPosition
        else pointPx(context.appContext) ?: context.touchPosition

    /**
     * 隐藏触钮，并等它**真的不可触**之后再注入触点。
     *
     * `hideGestureButton` 只给触钮的触摸捕获窗口加 `FLAG_NOT_TOUCHABLE` 并发一笔 WM 事务，
     * 不是同步生效；不等它落地就注入，点击会被还没失效的捕获窗口吃掉（点在触钮自己身上）。
     * WMS 从 App 侧观测不到，按帧等待是能做到的最近似：等两帧让事务落到输入分发。
     * 主 overlay 窗口本身就是 NOT_TOUCHABLE，不会挡住注入的触点。
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

    /**
     * 参数坐标转屏像素（dp→px，按执行时 density）。
     * 任一缺失/为负视为未填，调用方回退触点。负值不合法（编辑器 min=0 拦截，
     * 这里兜底外部构造的数据）。
     */
    private fun Action.Tap.pointPx(context: android.content.Context): Pair<Int, Int>? =
        pointPx(context, xDp, yDp)

    private fun Action.LongPress.pointPx(context: android.content.Context): Pair<Int, Int>? =
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

    override suspend fun execute(action: Action, context: ExecutorContext):ActionResult {
        return when (action) {
            is Action.OpenUrl -> {
                val adv = context.advancedSettings.effectiveForAction(action)
                val data = OpenAppOrUrlData(
                    type = OpenAppOrUrlData.TYPE_URL,
                    url = action.url,
                    queryParameters = action.queryParameters.map {
                        OpenUrlQueryParameter(name = it.name, value = it.value, enabled = it.enabled)
                    },
                )
                val ok = QuickLaunchFacade.launchUrlDirect(
                    context = context.appContext,
                    data = data,
                    miniWindowSettings = adv.miniWindowSettings(),
                )
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
        "internal.none", "internal.subGesture", "internal.hideGestureButton",
        "internal.buttonsVisibility",
        "internal.quickAppLauncher", "internal.quickSearch", "internal.gridPanel",
        "internal.volumeScrub",
        "internal.pointer",
        "internal.previousApp", "internal.runRule"
    )

    override suspend fun execute(action: Action, context: ExecutorContext): ActionResult {
        return when (action) {
            is Action.None -> ActionResult.Success()
            is Action.SubGesture -> ActionResult.Success()
            is Action.HideGestureButton -> {
                context.hideGestureButton(action.delayMs)
                ActionResult.Success()
            }
            is Action.ButtonsVisibility -> executeButtonsVisibility(action)
            is Action.QuickAppLauncher -> {
                context.toggleQuickAppLauncher()
                ActionResult.Success()
            }
            is Action.QuickSearch -> {
                context.showQuickSearch()
                ActionResult.Success()
            }
            is Action.GridPanel -> executeGridPanel(action, context)
            is Action.VolumeScrub -> {
                val config = VolumeScrubConfig(action.horizontalEnabled, action.stepThresholdDp)
                if (context.showVolumeScrub(config)) ActionResult.Success() else ActionResult.Failed(ActionFailure.ExecutionFailed)
            }
            is Action.Pointer -> {
                // 已有会话时 begin 拒绝重复开启，按幂等成功收口。
                context.showPointer()
                ActionResult.Success()
            }
            is Action.PreviousApp -> {
                context.previousApp(action.excludePackageNames)
                ActionResult.Success()
            }
            is Action.RunRule -> {
                context.runRule?.invoke(action.ruleId, context)
                    ?: ActionResult.Failed(ActionFailure.Unsupported)
            }
            else -> ActionResult.Failed(ActionFailure.Unsupported)
        }
    }

    /**
     * 网格面板：悬空引用（面板被删）时按「无动作」静默收口（弧形面板在 ActionMapper
     * 的兜底同例），不报失败让手势链路弹错误提示。
     *
     * **空面板照常弹**：面板本体就是编辑入口（长按空白进编辑态加项），
     * 拦掉空面板 = 第一项永远加不上。
     *
     * 浮窗入口走注入的 `context.showGridPanel`（与手势链同一 seam，不在本层直接摸
     * 浮窗）；球侧直发那条无容器的路径走 `GridPanelOverlay.showIfExists`，同一份收口语义。
     */
    private fun executeGridPanel(action: Action.GridPanel, context: ExecutorContext): ActionResult {
        if (context.actionSettings.actionGridPanels.panelById(action.panelId) == null) {
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
     * 触钮显隐：直接改按钮 enabled 存量（DataStore 更新自带顺序，后写覆盖先写，
     * 即规则列表顺序=优先级）。失败吞掉报 Failed，不抛。
     */
    private suspend fun executeButtonsVisibility(action: Action.ButtonsVisibility): ActionResult {
        val ok = runCatching {
            ConfigProvider.updateGestureButtons { list ->
                val ids = when (action.target) {
                    ButtonTarget.ALL -> list.map { it.id }.toSet()
                    ButtonTarget.EXCEPT -> list.map { it.id }
                        .filterNot { it in action.buttonIds }.toSet()
                    ButtonTarget.ONLY -> action.buttonIds.toSet()
                }
                list.map { button ->
                    if (button.id in ids) button.copy(enabled = action.visible) else button
                }
            }
            true
        }.getOrDefault(false)
        return if (ok) ActionResult.Success() else ActionResult.Failed(ActionFailure.ExecutionFailed)
    }
}

class PackageExecutors(private val controller: PackageController) : ActionExecutor {

    override val supportedTypes: Set<String> = setOf(
        "package.enable", "package.disable", "package.forceStop",
        "package.clearData", "package.uninstall"
    )

    override suspend fun execute(action: Action, context: ExecutorContext): ActionResult {
        return when (action) {
            is Action.PackageEnable -> controller.enable(action.packageName)
            is Action.PackageDisable -> controller.disable(action.packageName)
            is Action.PackageForceStop -> controller.forceStop(action.packageName)
            is Action.PackageClearData -> controller.clearData(action.packageName)
            is Action.PackageUninstall -> controller.uninstall(action.packageName)
            else -> ActionResult.Failed(ActionFailure.Unsupported)
        }
    }
}

class SettingsExecutors(private val controller: SettingsController) : ActionExecutor {

    override val supportedTypes: Set<String> = setOf("settings.get", "settings.put")

    override suspend fun execute(action: Action, context: ExecutorContext): ActionResult {
        return when (action) {
            is Action.SettingsGet -> controller.get(action.namespace, action.key)
            is Action.SettingsPut -> controller.put(action.namespace, action.key, action.value)
            else -> ActionResult.Failed(ActionFailure.Unsupported)
        }
    }
}

class ShellExecutor(private val shellController: ShellController) : ActionExecutor {

    override val supportedTypes: Set<String> = setOf("shell.custom")

    override suspend fun execute(action: Action, context: ExecutorContext): ActionResult {
        return when (action) {
            is Action.CustomCommand -> {
                val command = action.command.trim()
                val result = shellController.runCommand(command)
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

class SystemCmdExecutors(private val shellController: ShellController) : ActionExecutor {

    override val supportedTypes: Set<String> = setOf("systemCmd.reboot", "systemCmd.shutdown")

    override suspend fun execute(action: Action, context: ExecutorContext): ActionResult {
        return when (action) {
            is Action.Reboot -> {
                val result = shellController.runCommand("reboot")
                if (result.success) ActionResult.Success() else ActionResult.Failed(ActionFailure.ExecutionFailed)
            }
            is Action.Shutdown -> {
                val result = shellController.runCommand("shutdown -p")
                if (result.success) ActionResult.Success() else ActionResult.Failed(ActionFailure.ExecutionFailed)
            }
            else -> ActionResult.Failed(ActionFailure.Unsupported)
        }
    }
}
