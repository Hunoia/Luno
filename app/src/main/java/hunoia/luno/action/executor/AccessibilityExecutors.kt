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
            is Action.Back -> {
                controller.back()
                ActionResult.Success
            }
            is Action.Home -> {
                controller.home()
                ActionResult.Success
            }
            is Action.Recents -> {
                controller.recents()
                ActionResult.Success
            }
            is Action.Tap -> {
                context.hideGestureButton(250L)
                delay(80)
                ActionResult.Success
            }
            is Action.LongPress -> {
                context.hideGestureButton(250L)
                delay(80)
                ActionResult.Success
            }
            is Action.Swipe -> {
                when (action.direction) {
                    hunoia.luno.action.model.SwipeDirection.UP -> controller.swipeUp()
                    hunoia.luno.action.model.SwipeDirection.DOWN -> controller.swipeDown()
                    hunoia.luno.action.model.SwipeDirection.LEFT -> controller.swipeLeft()
                    hunoia.luno.action.model.SwipeDirection.RIGHT -> controller.swipeRight()
                    hunoia.luno.action.model.SwipeDirection.TO_TOP -> controller.scrollToTop()
                }
                ActionResult.Success
            }
            is Action.InputText -> {
                controller.inputText(action.text)
                ActionResult.Success
            }
            is Action.Screenshot -> {
                context.scope.launch {
                    delay(200)
                    controller.takeScreenshot()
                }
                ActionResult.Success
            }
            is Action.SplitScreen -> {
                controller.toggleSplitScreen()
                ActionResult.Success
            }
            is Action.PowerButton -> {
                controller.powerDialog()
                ActionResult.Success
            }
            is Action.NotificationPanel -> {
                controller.notificationPanel()
                ActionResult.Success
            }
            is Action.QuickPanel -> {
                controller.quickPanel()
                ActionResult.Success
            }
            is Action.LockScreen -> {
                controller.lockScreen()
                ActionResult.Success
            }
            else -> ActionResult.Failed(ActionFailure.Unsupported)
        }
    }
}
