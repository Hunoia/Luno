package hunoia.luno.action.template

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.ScreenLockRotation
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Wifi
import hunoia.luno.R
import hunoia.luno.config.model.ParamType

object SystemFunctionTemplates {

    data class Template(
        val id: String,
        @StringRes val nameResId: Int,
        val icon: androidx.compose.ui.graphics.vector.ImageVector,
        val category: String,
        val command: String,
        val params: List<TemplateParam> = emptyList()
    )

    val templates = listOf(
        Template(
            id = "volume_up",
            nameResId = R.string.tpl_volume_up,
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            category = "audio",
            command = "cmd media_session volume --show --adj raise"
        ),
        Template(
            id = "volume_down",
            nameResId = R.string.tpl_volume_down,
            icon = Icons.AutoMirrored.Filled.VolumeDown,
            category = "audio",
            command = "cmd media_session volume --show --adj lower"
        ),
        Template(
            id = "volume_mute",
            nameResId = R.string.tpl_volume_mute,
            icon = Icons.Default.VolumeMute,
            category = "audio",
            command = "cmd media_session dispatch mute"
        ),
        Template(
            id = "volume_set",
            nameResId = R.string.tpl_volume_set,
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            category = "audio",
            command = "cmd media_session volume --show --stream {stream} --set {level}",
            params = listOf(
                TemplateParam("stream", "Stream", ParamType.SELECT, "3", listOf("0:Voice Call", "1:Ring", "2:Music", "3:Alarm", "4:Notification", "5:System")),
                TemplateParam("level", "Level (0-15)", ParamType.INT, "10")
            )
        ),
        Template(
            id = "play_pause",
            nameResId = R.string.tpl_play_pause,
            icon = Icons.Default.VolumeUp,
            category = "media",
            command = "cmd media_session dispatch play-pause"
        ),
        Template(
            id = "next_track",
            nameResId = R.string.tpl_next_track,
            icon = Icons.Default.SkipNext,
            category = "media",
            command = "cmd media_session dispatch next"
        ),
        Template(
            id = "prev_track",
            nameResId = R.string.tpl_prev_track,
            icon = Icons.Default.SkipPrevious,
            category = "media",
            command = "cmd media_session dispatch previous"
        ),
        Template(
            id = "brightness_up",
            nameResId = R.string.tpl_brightness_up,
            icon = Icons.Default.BrightnessHigh,
            category = "display",
            command = "input keyevent 221"
        ),
        Template(
            id = "brightness_down",
            nameResId = R.string.tpl_brightness_down,
            icon = Icons.Default.BrightnessLow,
            category = "display",
            command = "input keyevent 220"
        ),
        Template(
            id = "brightness_set",
            nameResId = R.string.tpl_brightness_set,
            icon = Icons.Default.SettingsBrightness,
            category = "display",
            command = "settings put system screen_brightness {level}",
            params = listOf(
                TemplateParam("level", "Brightness (0-255)", ParamType.INT, "128")
            )
        ),
        Template(
            id = "auto_rotate_on",
            nameResId = R.string.tpl_auto_rotate_on,
            icon = Icons.Default.ScreenRotation,
            category = "display",
            command = "settings put system accelerometer_rotation 1"
        ),
        Template(
            id = "auto_rotate_off",
            nameResId = R.string.tpl_auto_rotate_off,
            icon = Icons.Default.ScreenLockRotation,
            category = "display",
            command = "settings put system accelerometer_rotation 0"
        ),
        Template(
            id = "lock_rotation",
            nameResId = R.string.tpl_lock_rotation,
            icon = Icons.Default.ScreenLockPortrait,
            category = "display",
            command = "settings put system user_rotation {rotation}",
            params = listOf(
                TemplateParam("rotation", "Rotation", ParamType.SELECT, "0", listOf("0:0°", "1:90°", "2:180°", "3:270°"))
            )
        ),
        Template(
            id = "wifi_on",
            nameResId = R.string.tpl_wifi_on,
            icon = Icons.Default.Wifi,
            category = "network",
            command = "cmd wifi set-wifi-enabled enabled"
        ),
        Template(
            id = "wifi_off",
            nameResId = R.string.tpl_wifi_off,
            icon = Icons.Default.Wifi,
            category = "network",
            command = "cmd wifi set-wifi-enabled disabled"
        ),
        Template(
            id = "bluetooth_on",
            nameResId = R.string.tpl_bluetooth_on,
            icon = Icons.Default.Bluetooth,
            category = "network",
            command = "cmd bluetooth_manager enable"
        ),
        Template(
            id = "bluetooth_off",
            nameResId = R.string.tpl_bluetooth_off,
            icon = Icons.Default.Bluetooth,
            category = "network",
            command = "cmd bluetooth_manager disable"
        ),
        Template(
            id = "dnd_on",
            nameResId = R.string.tpl_dnd_on,
            icon = Icons.Default.DoNotDisturb,
            category = "notification",
            command = "cmd notification set_dnd on"
        ),
        Template(
            id = "dnd_off",
            nameResId = R.string.tpl_dnd_off,
            icon = Icons.Default.Notifications,
            category = "notification",
            command = "cmd notification set_dnd off"
        ),
        Template(
            id = "home",
            nameResId = R.string.tpl_home,
            icon = Icons.Default.Home,
            category = "navigation",
            command = "input keyevent 3"
        ),
        Template(
            id = "back",
            nameResId = R.string.tpl_back,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            category = "navigation",
            command = "input keyevent 4"
        ),
        Template(
            id = "recent",
            nameResId = R.string.tpl_recent,
            icon = Icons.Default.ViewCarousel,
            category = "navigation",
            command = "input keyevent 187"
        ),
        Template(
            id = "power_menu",
            nameResId = R.string.tpl_power_menu,
            icon = Icons.Default.PowerSettingsNew,
            category = "power",
            command = "input keyevent --longpress 26"
        ),
        Template(
            id = "sleep",
            nameResId = R.string.tpl_sleep,
            icon = Icons.Default.PowerSettingsNew,
            category = "power",
            command = "input keyevent 223"
        ),
        Template(
            id = "wake_up",
            nameResId = R.string.tpl_wake_up,
            icon = Icons.Default.PowerSettingsNew,
            category = "power",
            command = "input keyevent 224"
        )
    )

    fun getById(id: String): Template? = templates.find { it.id == id }

    fun getByCategory(category: String): List<Template> = templates.filter { it.category == category }

    fun getCategories(): List<String> = templates.map { it.category }.distinct()

    fun generateCommand(template: Template, params: Map<String, String>): String {
        var command = template.command
        template.params.forEach { param ->
            val value = params[param.key] ?: param.defaultValue
            command = command.replace("{${param.key}}", value)
        }
        return command
    }
}