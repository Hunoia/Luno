package hunoia.luno.ui.automation

import android.content.Context
import hunoia.luno.R
import hunoia.luno.action.model.AudioStream
import hunoia.luno.config.model.BooleanMode
import hunoia.luno.config.model.ConditionItem
import hunoia.luno.config.model.ConditionType
import hunoia.luno.config.model.NetworkType
import hunoia.luno.config.model.ScreenEventType
import hunoia.luno.config.model.ScreenType
import hunoia.luno.config.model.WhenGroup
import hunoia.luno.runtime.condition.formatTimeOfDay

fun conditionSummary(context: Context, group: WhenGroup): String {
    if (group.items.isEmpty()) return context.getString(R.string.automation_condition_empty)
    val sep = if (group.mode == BooleanMode.AND) context.getString(R.string.logic_all)
    else context.getString(R.string.logic_any)
    return group.items.joinToString(" $sep ") { itemSummary(context, it) }
}

fun itemSummary(context: Context, item: ConditionItem): String {
    val base = when (item.type) {
        ConditionType.FOREGROUND_APP,
        ConditionType.APP_CHANGED_TO -> {
            if (item.packageNames.isEmpty()) {
                context.getString(R.string.condition_apps_empty)
            } else {
                val shown = item.packageNames.take(3).joinToString(", ")
                val extra = item.packageNames.size - 3
                context.getString(
                    R.string.condition_app_in,
                    if (extra > 0) "$shown, +$extra" else shown,
                )
            }
        }

        ConditionType.SCREEN_STATE -> screenTypeLabel(context, item.screenType ?: ScreenType.LOCK_SCREEN)

        ConditionType.SCREEN_EVENT -> context.getString(
            if (item.screenEvent == ScreenEventType.SCREEN_OFF) R.string.screen_event_off
            else R.string.screen_event_on,
        )

        ConditionType.BATTERY_RANGE ->
            "${item.batteryLevelMin ?: 0}% - ${item.batteryLevelMax ?: 100}%"

        ConditionType.CHARGING -> context.getString(R.string.condition_charging)

        ConditionType.CHARGING_CHANGED -> context.getString(
            if (item.charging == false) R.string.charging_stopped else R.string.charging_started,
        )

        ConditionType.TIME_RANGE ->
            "${formatTimeOfDay(item.startMinute ?: 0)} - ${formatTimeOfDay(item.endMinute ?: 0)}"

        ConditionType.VOLUME_RANGE ->
            "${audioStreamLabel(context, item.audioStream ?: AudioStream.MUSIC)} ${item.volumeMin ?: 0}% - ${item.volumeMax ?: 100}%"

        ConditionType.VOLUME_CHANGED ->
            "${audioStreamLabel(context, item.audioStream ?: AudioStream.MUSIC)} " +
                context.getString(
                    when (item.volumeDirection) {
                        null -> R.string.condition_unlimited
                        hunoia.luno.action.model.VolumeDirection.UP -> R.string.volume_up
                        else -> R.string.volume_down
                    },
                )

        ConditionType.RINGER_SILENT -> context.getString(
            if (item.silent == true) R.string.condition_silent else R.string.condition_ringing,
        )

        ConditionType.NETWORK_TYPE -> networkTypeLabel(context, item.networkType ?: NetworkType.WIFI)

        ConditionType.HEADPHONES -> context.getString(
            if (item.plugged == true) R.string.headphones_plugged else R.string.headphones_unplugged,
        )

        ConditionType.BLUETOOTH -> bluetoothSummary(context, item)

        ConditionType.AIRPLANE_MODE -> context.getString(
            if (item.airplaneMode == true) R.string.state_on else R.string.state_off,
        )

        ConditionType.GROUP -> conditionSummary(context, item.group ?: WhenGroup())

        else -> conditionTypeLabel(context, item.type)
    }
    return if (item.negated) "${context.getString(R.string.logic_not)} $base" else base
}

private fun bluetoothSummary(context: Context, item: ConditionItem): String {
    val parts = buildList {
        if (item.expectedBluetoothAdapterOn != null) {
            add(context.getString(R.string.bluetooth_adapter) + ":" +
                context.getString(if (item.expectedBluetoothAdapterOn == true) R.string.state_on else R.string.state_off))
        }
        if (item.expectedBluetoothAudioConnected != null) {
            add(context.getString(R.string.bluetooth_audio) + ":" +
                context.getString(if (item.expectedBluetoothAudioConnected == true) R.string.state_on else R.string.state_off))
        }
    }
    return if (parts.isEmpty()) context.getString(R.string.condition_bluetooth)
    else parts.joinToString(" / ")
}

