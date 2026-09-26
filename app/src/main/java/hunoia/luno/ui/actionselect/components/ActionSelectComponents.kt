package hunoia.luno.ui.actionselect

import hunoia.luno.R
import hunoia.luno.action.definition.ActionConfigKind

val actionTitleResMap: Map<String, Int> = mapOf(
    "none" to R.string.action_none,
    "back" to R.string.action_back,
    "home" to R.string.action_home,
    "recent" to R.string.action_recent,
    "volume_up" to R.string.action_volume_up,
    "volume_down" to R.string.action_volume_down,
    "mute" to R.string.action_mute,
    "play_pause" to R.string.action_play_pause_song,
    "last_song" to R.string.action_last_song,
    "next_song" to R.string.action_next_song,
    "previous_app" to R.string.action_previous_app,
    "open_notification" to R.string.action_open_notification_panel,
    "open_quick_settings" to R.string.action_open_quick_panel,
    "lock_screen" to R.string.action_lock_screen,
    "flashlight" to R.string.action_flashlight,
    "assist_app" to R.string.action_assist_app,
    "screenshot" to R.string.action_screenshot,
    "power_button" to R.string.action_power_button,
    "keep_screen_on" to R.string.action_keep_screen_on,
    "popup_screen" to R.string.action_popup_screen,
    "back_to_top" to R.string.action_back_to_top,
    "pointer" to R.string.action_pointer,
    "volume_scrub" to R.string.action_volume_scrub,
    "shell_command" to R.string.action_shell_command,
    "open_activity" to R.string.action_open_activity,
    "open_url" to R.string.action_open_url,
    "quick_app_launcher" to R.string.action_quick_app_panel,
    "random_name" to R.string.action_random_name,
    "one_key_freeze" to R.string.action_one_key_freeze_apps,
    "generate_password_copy" to R.string.action_generate_password_copy,
    "open_password_generator" to R.string.action_open_password_generator,
    "sub_gesture" to R.string.action_sub_gesture,
)

val actionDescResMap: Map<String, Int> = mapOf(
    "back" to R.string.action_desc_back,
    "home" to R.string.action_desc_home,
    "recent" to R.string.action_desc_recent,
    "open_notification" to R.string.action_desc_notification,
    "lock_screen" to R.string.action_desc_lock_screen,
    "flashlight" to R.string.action_desc_flashlight,
    "screenshot" to R.string.action_desc_screenshot,
    "open_activity" to R.string.action_desc_open_activity,
    "open_url" to R.string.action_desc_open_url,
    "quick_app_launcher" to R.string.action_desc_quick_launcher,
    "pointer" to R.string.action_desc_pointer,
    "volume_scrub" to R.string.action_desc_volume_scrub,
    "shell_command" to R.string.action_desc_shell_command,
    "sub_gesture" to R.string.action_desc_sub_gesture,
)

val actionSettingHintResMap: Map<ActionConfigKind, Int> = mapOf(
    ActionConfigKind.OPEN_APP_OR_URL to R.string.action_setting_hint_open_activity,
    ActionConfigKind.SHELL_COMMAND to R.string.action_setting_hint_shell_command,
)

val actionPermissionHintResMap: Map<String, Int> = mapOf(
    "flashlight" to R.string.action_permission_hint_flashlight,
    "screenshot" to R.string.action_permission_hint_screenshot,
    "power_button" to R.string.action_permission_hint_power_button,
)
