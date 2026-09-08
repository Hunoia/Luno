package hunoia.luno.action.handlers

import android.view.KeyEvent
import hunoia.luno.action.api.ActionExecutionResult
import hunoia.luno.action.api.ActionHandler
import hunoia.luno.action.api.ActionHandlerContext
import hunoia.luno.action.api.ActionFacade
import hunoia.luno.config.model.Action
import hunoia.luno.bridge.dispatchMediaKeyEvent
import hunoia.luno.bridge.toggleMute
import hunoia.luno.bridge.volumeDown
import hunoia.luno.bridge.volumeUp

object MediaActionHandler : ActionHandler {

    override val supportedActions = setOf(
        ActionFacade.VOLUME_UP,
        ActionFacade.VOLUME_DOWN,
        ActionFacade.MUTE,
        ActionFacade.PLAY_PAUSE_SONG,
        ActionFacade.LAST_SONG,
        ActionFacade.NEXT_SONG,
    )

    override suspend fun handle(action: Action, context: ActionHandlerContext): ActionExecutionResult {
        when (action.value) {
            ActionFacade.VOLUME_UP -> context.appContext.volumeUp()
            ActionFacade.VOLUME_DOWN -> context.appContext.volumeDown()
            ActionFacade.MUTE -> context.appContext.toggleMute()
            ActionFacade.PLAY_PAUSE_SONG -> context.appContext.dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            ActionFacade.LAST_SONG -> context.appContext.dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            ActionFacade.NEXT_SONG -> context.appContext.dispatchMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT)
            else -> return ActionExecutionResult.Ignored
        }
        return ActionExecutionResult.Success
    }
}
