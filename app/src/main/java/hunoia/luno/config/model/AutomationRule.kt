package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.action.model.AudioStream
import hunoia.luno.action.model.VolumeDirection
import kotlinx.serialization.Serializable

@Serializable
@Keep
enum class BooleanMode { AND, OR }

@Serializable
@Keep
enum class RuleEffectType { HIDE_BUTTONS, SHOW_BUTTONS, RUN_ACTION }

@Serializable
@Keep
enum class RuleScope { ALL, EXCEPT, ONLY }

@Serializable
@Keep
enum class ScreenType { LOCK_SCREEN, LAUNCHER, LANDSCAPE, PORTRAIT, KEYBOARD_INPUT }

@Serializable
@Keep
enum class ScreenEventType { SCREEN_ON, SCREEN_OFF }

@Serializable
@Keep
enum class NetworkType { WIFI, MOBILE, NONE, OTHER }

object ConditionType {
    const val FOREGROUND_APP = "foregroundApp"
    const val SCREEN_STATE = "screenState"
    const val BATTERY_RANGE = "batteryRange"
    const val CHARGING = "charging"
    const val TIME_RANGE = "timeRange"
    const val VOLUME_RANGE = "volumeRange"
    const val RINGER_SILENT = "ringerSilent"
    const val NETWORK_TYPE = "networkType"
    const val HEADPHONES = "headphones"
    const val BLUETOOTH = "bluetooth"
    const val AIRPLANE_MODE = "airplaneMode"

    const val APP_CHANGED_TO = "appChangedTo"
    const val SCREEN_EVENT = "screenEvent"
    const val CHARGING_CHANGED = "chargingChanged"
    const val VOLUME_CHANGED = "volumeChanged"

    const val GROUP = "group"
}

@Serializable
@Keep
data class ConditionItem(
    val type: String,
    val negated: Boolean = false,
    val packageNames: List<String> = emptyList(),
    val screenType: ScreenType? = null,
    val screenEvent: ScreenEventType? = null,
    val batteryLevelMin: Int? = null,
    val batteryLevelMax: Int? = null,
    val charging: Boolean? = null,
    val startMinute: Int? = null,
    val endMinute: Int? = null,
    val audioStream: AudioStream? = null,
    val volumeMin: Int? = null,
    val volumeMax: Int? = null,
    val volumeDirection: VolumeDirection? = null,
    val silent: Boolean? = null,
    val networkType: NetworkType? = null,
    val plugged: Boolean? = null,
    // 期望值：null 表示不限，与求值器里的实际值做等值比较
    val expectedBluetoothAdapterOn: Boolean? = null,
    val expectedBluetoothAudioConnected: Boolean? = null,
    val airplaneMode: Boolean? = null,
    val group: WhenGroup? = null,
)

@Serializable
@Keep
data class WhenGroup(
    val mode: BooleanMode = BooleanMode.AND,
    val items: List<ConditionItem> = emptyList(),
)

@Serializable
@Keep
data class RuleEffect(
    val type: RuleEffectType,
    val scope: RuleScope = RuleScope.ALL,
    val buttonIds: List<String> = emptyList(),
    val entryId: String = "",
) {
    fun covers(buttonId: String): Boolean = when (scope) {
        RuleScope.ALL -> true
        RuleScope.EXCEPT -> buttonId !in buttonIds
        RuleScope.ONLY -> buttonId in buttonIds
    }
}

@Serializable
@Keep
data class AutomationRule(
    val id: String,
    val enabled: Boolean = true,
    val name: String = "",
    val condition: WhenGroup,
    val effect: RuleEffect,
    val cooldownMs: Long = 0,
) {
    companion object {
        val Defaults: List<AutomationRule> = emptyList()
    }
}
