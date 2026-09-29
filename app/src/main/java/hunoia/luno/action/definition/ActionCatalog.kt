package hunoia.luno.action.definition

import hunoia.luno.R
import hunoia.luno.action.api.ActionIds

object ActionCatalog {

    private val definitions: List<ActionDefinition> = listOf(
        ActionDefinition(ActionIds.NONE, ActionCategory.NONE, R.string.action_none, "internal.none"),
        ActionDefinition(ActionIds.BACK, ActionCategory.NAVIGATION, R.string.action_back, "accessibility.back"),
        ActionDefinition(ActionIds.HOME, ActionCategory.NAVIGATION, R.string.action_home, "accessibility.home"),
        ActionDefinition(ActionIds.RECENT, ActionCategory.NAVIGATION, R.string.action_recent, "accessibility.recents"),
        ActionDefinition(ActionIds.VOLUME_UP, ActionCategory.SYSTEM, R.string.action_volume_up, "system.volume"),
        ActionDefinition(ActionIds.VOLUME_DOWN, ActionCategory.SYSTEM, R.string.action_volume_down, "system.volume"),
        ActionDefinition(ActionIds.MUTE, ActionCategory.SYSTEM, R.string.action_mute, "system.media"),
        ActionDefinition(ActionIds.PLAY_PAUSE_SONG, ActionCategory.SYSTEM, R.string.action_play_pause_song, "system.media"),
        ActionDefinition(ActionIds.LAST_SONG, ActionCategory.SYSTEM, R.string.action_last_song, "system.media"),
        ActionDefinition(ActionIds.NEXT_SONG, ActionCategory.SYSTEM, R.string.action_next_song, "system.media"),
        ActionDefinition(ActionIds.PREVIOUS_APP, ActionCategory.NAVIGATION, R.string.action_previous_app, "internal.previousApp"),
        ActionDefinition(ActionIds.OPEN_NOTIFICATION_PANEL, ActionCategory.SYSTEM, R.string.action_open_notification_panel, "accessibility.notificationPanel"),
        ActionDefinition(ActionIds.OPEN_QUICK_PANEL, ActionCategory.SYSTEM, R.string.action_open_quick_panel, "accessibility.quickPanel"),
        ActionDefinition(ActionIds.LOCK_SCREEN, ActionCategory.SYSTEM, R.string.action_lock_screen, "accessibility.lockScreen"),
        ActionDefinition(ActionIds.FLASHLIGHT, ActionCategory.SYSTEM, R.string.action_flashlight, "system.flashlight"),
        ActionDefinition(ActionIds.ASSIST_APP, ActionCategory.SYSTEM, R.string.action_assist_app, "intent.assist"),
        ActionDefinition(ActionIds.SCREENSHOT, ActionCategory.SYSTEM, R.string.action_screenshot, "accessibility.screenshot"),
        ActionDefinition(ActionIds.SPLIT_SCREEN, ActionCategory.SYSTEM, R.string.action_split_screen, "accessibility.splitScreen"),
        ActionDefinition(ActionIds.POWER_BUTTON, ActionCategory.SYSTEM, R.string.action_power_button, "accessibility.powerButton"),
        ActionDefinition(ActionIds.POPUP_SCREEN, ActionCategory.NAVIGATION, R.string.action_popup_screen, "app.popup"),
        ActionDefinition(ActionIds.BACK_TO_TOP, ActionCategory.NAVIGATION, R.string.action_back_to_top, "accessibility.swipe"),
        ActionDefinition(ActionIds.CLICK_CURRENT_POSITION, ActionCategory.NAVIGATION, R.string.action_click_current_position, "accessibility.tap"),
        ActionDefinition(ActionIds.OPEN_APP_ACTIVITY, ActionCategory.TOOL, R.string.action_open_activity, "app.openActivity"),
        ActionDefinition(ActionIds.OPEN_URL, ActionCategory.TOOL, R.string.action_open_url, "intent.openUrl"),
        ActionDefinition(ActionIds.QUICK_APP_LAUNCHER, ActionCategory.TOOL, R.string.action_quick_app_panel, "internal.quickAppLauncher"),
        ActionDefinition(ActionIds.RANDOM_NAME, ActionCategory.TOOL, R.string.action_random_name, "system.clipboard"),
        ActionDefinition(ActionIds.GENERATE_PASSWORD_COPY, ActionCategory.TOOL, R.string.action_generate_password_copy, "system.clipboard"),
        ActionDefinition(ActionIds.HIDE_GESTURE_BUTTON, ActionCategory.SYSTEM, R.string.action_hide_gesture_button, "internal.hideGestureButton"),
        ActionDefinition(ActionIds.VOLUME_SCRUB, ActionCategory.SYSTEM, R.string.action_volume_scrub, "internal.volumeScrub"),
        ActionDefinition(ActionIds.EXECUTE_SHELL_COMMAND, ActionCategory.TOOL, R.string.action_shell_command, "shell.custom"),
        ActionDefinition(ActionIds.SUB_GESTURE, ActionCategory.SUB_GESTURE, R.string.action_sub_gesture, "internal.subGesture", isDisplayed = false),
    )

    private val byIdMap: Map<String, ActionDefinition> = definitions.associateBy { it.actionId }

    fun byId(actionId: String): ActionDefinition? = byIdMap[actionId]
}
