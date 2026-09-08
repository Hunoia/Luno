package hunoia.luno.action.api

import hunoia.luno.config.model.Action
import hunoia.luno.action.definition.ActionCatalog
import hunoia.luno.action.definition.ActionDefinition

object ActionFacade {

    const val NONE = ActionIds.NONE
    const val BACK = ActionIds.BACK
    const val HOME = ActionIds.HOME
    const val RECENT = ActionIds.RECENT
    const val VOLUME_UP = ActionIds.VOLUME_UP
    const val VOLUME_DOWN = ActionIds.VOLUME_DOWN
    const val MUTE = ActionIds.MUTE
    const val PLAY_PAUSE_SONG = ActionIds.PLAY_PAUSE_SONG
    const val LAST_SONG = ActionIds.LAST_SONG
    const val NEXT_SONG = ActionIds.NEXT_SONG
    const val PREVIOUS_APP = ActionIds.PREVIOUS_APP
    const val OPEN_NOTIFICATION_PANEL = ActionIds.OPEN_NOTIFICATION_PANEL
    const val OPEN_QUICK_PANEL = ActionIds.OPEN_QUICK_PANEL
    const val LOCK_SCREEN = ActionIds.LOCK_SCREEN
    const val FLASHLIGHT = ActionIds.FLASHLIGHT
    const val SPLIT_SCREEN = ActionIds.SPLIT_SCREEN
    const val POPUP_SCREEN = ActionIds.POPUP_SCREEN
    const val ASSIST_APP = ActionIds.ASSIST_APP
    const val SCREENSHOT = ActionIds.SCREENSHOT
    const val POWER_BUTTON = ActionIds.POWER_BUTTON
    const val HIDE_GESTURE_BUTTON = ActionIds.HIDE_GESTURE_BUTTON
    const val KEEP_SCREEN_ON = ActionIds.KEEP_SCREEN_ON
    const val BACK_TO_TOP = ActionIds.BACK_TO_TOP
    const val OPEN_APP_ACTIVITY = ActionIds.OPEN_APP_ACTIVITY
    const val OPEN_URL = ActionIds.OPEN_URL
    const val QUICK_APP_LAUNCHER = ActionIds.QUICK_APP_LAUNCHER
    const val RANDOM_NAME = ActionIds.RANDOM_NAME
    const val ONE_KEY_FREEZE_APPS = ActionIds.ONE_KEY_FREEZE_APPS
    const val GENERATE_PASSWORD_COPY = ActionIds.GENERATE_PASSWORD_COPY
    const val CLICK_CURRENT_POSITION = ActionIds.CLICK_CURRENT_POSITION
    const val POINTER = ActionIds.POINTER
    const val VOLUME_SCRUB = ActionIds.VOLUME_SCRUB
    const val EXECUTE_SHELL_COMMAND = ActionIds.EXECUTE_SHELL_COMMAND
    const val SUB_GESTURE = ActionIds.SUB_GESTURE
    const val EXTRA_LAUNCH_APP = ActionIds.EXTRA_LAUNCH_APP
    const val EXTRA_LAUNCH_SHORTCUT = ActionIds.EXTRA_LAUNCH_SHORTCUT

    fun byAction(action: Action): ActionDefinition? = ActionCatalog.byAction(action)

    fun byId(actionId: String): ActionDefinition? = ActionCatalog.byId(actionId)

    fun hasConfig(actionId: String): Boolean = ActionCatalog.hasConfig(actionId)

    val definitions: List<ActionDefinition> get() = ActionCatalog.definitions
}
