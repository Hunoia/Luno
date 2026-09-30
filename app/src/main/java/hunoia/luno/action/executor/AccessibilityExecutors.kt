package hunoia.luno.action.executor

import hunoia.luno.action.controller.AccessibilityController
import hunoia.luno.action.execution.ActionExecutor
import hunoia.luno.action.execution.ExecutorContext
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionFailure
import hunoia.luno.action.model.ActionResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

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
                val (x, y) = context.touchPosition ?: return ActionResult.Failed(ActionFailure.InvalidParameter)
                context.hideGestureButton(250L)
                delay(80)
                executionResult(controller.click(x, y))
            }
            is Action.LongPress -> {
                val (x, y) = context.touchPosition ?: return ActionResult.Failed(ActionFailure.InvalidParameter)
                context.hideGestureButton(250L)
                delay(80)
                executionResult(controller.longPress(x, y))
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
}
