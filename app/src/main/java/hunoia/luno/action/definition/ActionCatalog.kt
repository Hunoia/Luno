package hunoia.luno.action.definition

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import hunoia.luno.action.definition.PlayPause
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Window
import hunoia.luno.R
import hunoia.luno.action.api.ActionIds
import hunoia.luno.config.model.Action

object ActionCatalog {

    val definitions: List<ActionDefinition> = listOf(
        ActionDefinition(ActionIds.NONE, ActionCategory.NONE, ActionConfigKind.NONE,
            R.string.action_none, Icons.Default.Android),
        ActionDefinition(ActionIds.BACK, ActionCategory.NAVIGATION, ActionConfigKind.NONE,
            R.string.action_back, Icons.AutoMirrored.Filled.ArrowBack),
        ActionDefinition(ActionIds.HOME, ActionCategory.NAVIGATION, ActionConfigKind.NONE,
            R.string.action_home, Icons.Default.Home),
        ActionDefinition(ActionIds.RECENT, ActionCategory.NAVIGATION, ActionConfigKind.NONE,
            R.string.action_recent, Icons.Default.ViewCarousel),
        ActionDefinition(ActionIds.VOLUME_UP, ActionCategory.SYSTEM, ActionConfigKind.NONE,
            R.string.action_volume_up, Icons.AutoMirrored.Filled.VolumeUp),
        ActionDefinition(ActionIds.VOLUME_DOWN, ActionCategory.SYSTEM, ActionConfigKind.NONE,
            R.string.action_volume_down, Icons.AutoMirrored.Filled.VolumeDown),
        ActionDefinition(ActionIds.MUTE, ActionCategory.SYSTEM, ActionConfigKind.NONE,
            R.string.action_mute, Icons.AutoMirrored.Filled.VolumeMute),
        ActionDefinition(ActionIds.PLAY_PAUSE_SONG, ActionCategory.SYSTEM, ActionConfigKind.NONE,
            R.string.action_play_pause_song, Icons.Default.PlayPause),
        ActionDefinition(ActionIds.LAST_SONG, ActionCategory.SYSTEM, ActionConfigKind.NONE,
            R.string.action_last_song, Icons.Default.SkipPrevious),
        ActionDefinition(ActionIds.NEXT_SONG, ActionCategory.SYSTEM, ActionConfigKind.NONE,
            R.string.action_next_song, Icons.Default.SkipNext),
        ActionDefinition(ActionIds.PREVIOUS_APP, ActionCategory.NAVIGATION, ActionConfigKind.NONE,
            R.string.action_previous_app, Icons.Default.SwapHoriz),
        ActionDefinition(ActionIds.OPEN_NOTIFICATION_PANEL, ActionCategory.SYSTEM, ActionConfigKind.NONE,
            R.string.action_open_notification_panel, Icons.Default.Notifications),
        ActionDefinition(ActionIds.OPEN_QUICK_PANEL, ActionCategory.SYSTEM, ActionConfigKind.NONE,
            R.string.action_open_quick_panel, Icons.Default.Dashboard),
        ActionDefinition(ActionIds.LOCK_SCREEN, ActionCategory.SYSTEM, ActionConfigKind.NONE,
            R.string.action_lock_screen, Icons.Default.ScreenLockPortrait),
        ActionDefinition(ActionIds.FLASHLIGHT, ActionCategory.SYSTEM, ActionConfigKind.NONE,
            R.string.action_flashlight, Icons.Default.FlashlightOn),
        ActionDefinition(ActionIds.ASSIST_APP, ActionCategory.SYSTEM, ActionConfigKind.NONE,
            R.string.action_assist_app, Icons.Default.Assistant),
        ActionDefinition(ActionIds.SCREENSHOT, ActionCategory.SYSTEM, ActionConfigKind.NONE,
            R.string.action_screenshot, Icons.Default.Screenshot),
        ActionDefinition(ActionIds.SPLIT_SCREEN, ActionCategory.SYSTEM, ActionConfigKind.NONE,
            R.string.action_split_screen, Icons.Default.Splitscreen),
        ActionDefinition(ActionIds.POWER_BUTTON, ActionCategory.SYSTEM, ActionConfigKind.NONE,
            R.string.action_power_button, Icons.Default.PowerSettingsNew),
        ActionDefinition(ActionIds.KEEP_SCREEN_ON, ActionCategory.SYSTEM, ActionConfigKind.NONE,
            R.string.action_keep_screen_on, Icons.Default.BrightnessHigh),
        ActionDefinition(ActionIds.POPUP_SCREEN, ActionCategory.NAVIGATION, ActionConfigKind.NONE,
            R.string.action_popup_screen, Icons.Default.Window),
        ActionDefinition(ActionIds.BACK_TO_TOP, ActionCategory.NAVIGATION, ActionConfigKind.NONE,
            R.string.action_back_to_top, Icons.Default.VerticalAlignTop),
        ActionDefinition(ActionIds.CLICK_CURRENT_POSITION, ActionCategory.NAVIGATION, ActionConfigKind.NONE,
            R.string.action_click_current_position, Icons.Default.TouchApp),
        ActionDefinition(ActionIds.POINTER, ActionCategory.TOOL, ActionConfigKind.NONE,
            R.string.action_pointer, Icons.Default.Mouse),
        ActionDefinition(ActionIds.OPEN_APP_ACTIVITY, ActionCategory.TOOL, ActionConfigKind.OPEN_APP_OR_URL,
            R.string.action_open_activity, Icons.Default.Settings),
        ActionDefinition(ActionIds.OPEN_URL, ActionCategory.TOOL, ActionConfigKind.OPEN_APP_OR_URL,
            R.string.action_open_url, Icons.AutoMirrored.Filled.OpenInNew),
        ActionDefinition(ActionIds.QUICK_APP_LAUNCHER, ActionCategory.TOOL, ActionConfigKind.NONE,
            R.string.action_quick_app_panel, Icons.Default.Apps),
        ActionDefinition(ActionIds.RANDOM_NAME, ActionCategory.TOOL, ActionConfigKind.NONE,
            R.string.action_random_name, Icons.Default.AutoAwesome),
        ActionDefinition(ActionIds.ONE_KEY_FREEZE_APPS, ActionCategory.TOOL, ActionConfigKind.NONE,
            R.string.action_one_key_freeze_apps, Icons.Default.AcUnit),
        ActionDefinition(ActionIds.GENERATE_PASSWORD_COPY, ActionCategory.TOOL, ActionConfigKind.NONE,
            R.string.action_generate_password_copy, Icons.Default.ContentCopy),
        ActionDefinition(ActionIds.HIDE_GESTURE_BUTTON, ActionCategory.SYSTEM, ActionConfigKind.NONE,
            R.string.action_hide_gesture_button, Icons.Default.Gesture),
        ActionDefinition(ActionIds.VOLUME_SCRUB, ActionCategory.SYSTEM, ActionConfigKind.NONE,
            R.string.action_volume_scrub, Icons.AutoMirrored.Filled.VolumeUp),
        ActionDefinition(ActionIds.EXECUTE_SHELL_COMMAND, ActionCategory.TOOL, ActionConfigKind.SHELL_COMMAND,
            R.string.action_shell_command, Icons.Default.Terminal),
        ActionDefinition(ActionIds.SUB_GESTURE, ActionCategory.SUB_GESTURE, ActionConfigKind.NONE,
            R.string.action_sub_gesture, Icons.Default.Gesture, isDisplayed = false),
    )

    private val byIdMap: Map<String, ActionDefinition> = definitions.associateBy { it.actionId }

    fun byId(actionId: String): ActionDefinition? = byIdMap[actionId]
    fun byAction(action: Action): ActionDefinition? = byIdMap[action.value]
    fun hasConfig(actionId: String): Boolean = byIdMap[actionId]?.configKind != ActionConfigKind.NONE
}
