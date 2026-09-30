package hunoia.luno.action.definition

import hunoia.luno.R
import hunoia.luno.action.api.ActionIds

object ActionCatalog {

    private val definitions: List<ActionCatalogEntry> = listOf(
        ActionCatalogEntry(ActionIds.NONE, ActionCategory.NONE, R.string.action_none, "internal.none"),
        ActionCatalogEntry(ActionIds.BACK, ActionCategory.NAVIGATION, R.string.action_back, "accessibility.back"),
        ActionCatalogEntry(ActionIds.HOME, ActionCategory.NAVIGATION, R.string.action_home, "accessibility.home"),
        ActionCatalogEntry(ActionIds.RECENT, ActionCategory.NAVIGATION, R.string.action_recent, "accessibility.recents"),
        ActionCatalogEntry(ActionIds.VOLUME_UP, ActionCategory.SYSTEM, R.string.action_volume_up, "system.volume"),
        ActionCatalogEntry(ActionIds.VOLUME_DOWN, ActionCategory.SYSTEM, R.string.action_volume_down, "system.volume"),
        ActionCatalogEntry(ActionIds.MUTE, ActionCategory.SYSTEM, R.string.action_mute, "system.media"),
        ActionCatalogEntry(ActionIds.PLAY_PAUSE_SONG, ActionCategory.SYSTEM, R.string.action_play_pause_song, "system.media"),
        ActionCatalogEntry(ActionIds.LAST_SONG, ActionCategory.SYSTEM, R.string.action_last_song, "system.media"),
        ActionCatalogEntry(ActionIds.NEXT_SONG, ActionCategory.SYSTEM, R.string.action_next_song, "system.media"),
        ActionCatalogEntry(ActionIds.PREVIOUS_APP, ActionCategory.NAVIGATION, R.string.action_previous_app, "internal.previousApp"),
        ActionCatalogEntry(ActionIds.OPEN_NOTIFICATION_PANEL, ActionCategory.SYSTEM, R.string.action_open_notification_panel, "accessibility.notificationPanel"),
        ActionCatalogEntry(ActionIds.OPEN_QUICK_PANEL, ActionCategory.SYSTEM, R.string.action_open_quick_panel, "accessibility.quickPanel"),
        ActionCatalogEntry(ActionIds.LOCK_SCREEN, ActionCategory.SYSTEM, R.string.action_lock_screen, "accessibility.lockScreen"),
        ActionCatalogEntry(ActionIds.FLASHLIGHT, ActionCategory.SYSTEM, R.string.action_flashlight, "system.flashlight"),
        ActionCatalogEntry(ActionIds.ASSIST_APP, ActionCategory.SYSTEM, R.string.action_assist_app, "intent.assist"),
        ActionCatalogEntry(ActionIds.SCREENSHOT, ActionCategory.SYSTEM, R.string.action_screenshot, "accessibility.screenshot"),
        ActionCatalogEntry(ActionIds.SPLIT_SCREEN, ActionCategory.SYSTEM, R.string.action_split_screen, "accessibility.splitScreen"),
        ActionCatalogEntry(ActionIds.POWER_BUTTON, ActionCategory.SYSTEM, R.string.action_power_button, "accessibility.powerButton"),
        ActionCatalogEntry(ActionIds.POPUP_SCREEN, ActionCategory.NAVIGATION, R.string.action_popup_screen, "app.popup"),
        ActionCatalogEntry(ActionIds.BACK_TO_TOP, ActionCategory.NAVIGATION, R.string.action_back_to_top, "accessibility.swipe"),
        ActionCatalogEntry(ActionIds.CLICK_CURRENT_POSITION, ActionCategory.NAVIGATION, R.string.action_click_current_position, "accessibility.tap"),
        ActionCatalogEntry(ActionIds.OPEN_APP_ACTIVITY, ActionCategory.TOOL, R.string.action_open_activity, "app.openActivity"),
        ActionCatalogEntry(ActionIds.OPEN_URL, ActionCategory.TOOL, R.string.action_open_url, "intent.openUrl"),
        ActionCatalogEntry(ActionIds.QUICK_APP_LAUNCHER, ActionCategory.TOOL, R.string.action_quick_app_panel, "internal.quickAppLauncher"),
        ActionCatalogEntry(ActionIds.RANDOM_NAME, ActionCategory.TOOL, R.string.action_random_name, "system.clipboard"),
        ActionCatalogEntry(ActionIds.GENERATE_PASSWORD_COPY, ActionCategory.TOOL, R.string.action_generate_password_copy, "system.clipboard"),
        ActionCatalogEntry(ActionIds.HIDE_GESTURE_BUTTON, ActionCategory.SYSTEM, R.string.action_hide_gesture_button, "internal.hideGestureButton"),
        ActionCatalogEntry(ActionIds.VOLUME_SCRUB, ActionCategory.SYSTEM, R.string.action_volume_scrub, "internal.volumeScrub"),
        ActionCatalogEntry(ActionIds.EXECUTE_SHELL_COMMAND, ActionCategory.TOOL, R.string.action_shell_command, "shell.custom"),
        ActionCatalogEntry(ActionIds.SUB_GESTURE, ActionCategory.SUB_GESTURE, R.string.action_sub_gesture, "internal.subGesture", isDisplayed = false),
    )

    private val byIdMap: Map<String, ActionCatalogEntry> = definitions.associateBy { it.actionId }

    fun byId(actionId: String): ActionCatalogEntry? = byIdMap[actionId]
}
