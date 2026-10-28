package hunoia.luno.ui.automation

import android.content.Context
import hunoia.luno.R
import hunoia.luno.core.domain.AudioStream
import hunoia.luno.config.model.BooleanMode
import hunoia.luno.config.model.ConditionItem
import hunoia.luno.config.model.ConditionType
import hunoia.luno.config.model.KeyboardEventType
import hunoia.luno.config.model.NetworkType
import hunoia.luno.config.model.PlaybackDirection
import hunoia.luno.config.model.RingerMode
import hunoia.luno.config.model.ScreenEventType
import hunoia.luno.config.model.ScreenType
import hunoia.luno.config.model.WhenGroup
import hunoia.luno.domain.automation.formatTimeOfDay
import java.util.Calendar

fun conditionSummary(context: Context, group: WhenGroup): String {
    if (group.items.isEmpty()) return context.getString(R.string.automation_condition_empty)
    val sep = when (group.mode) {
        BooleanMode.AND -> context.getString(R.string.logic_all)
        BooleanMode.OR -> context.getString(R.string.logic_any)
    }
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
            when (item.screenEvent) {
                ScreenEventType.SCREEN_OFF -> R.string.screen_event_off
                ScreenEventType.USER_UNLOCK -> R.string.screen_event_unlock
                else -> R.string.screen_event_on
            },
        )

        ConditionType.KEYBOARD_EVENT -> context.getString(
            if (item.keyboardEvent == KeyboardEventType.HIDDEN) R.string.keyboard_hidden
            else R.string.keyboard_shown,
        )

        ConditionType.BATTERY_RANGE ->
            "${item.batteryLevelMin ?: 0}% - ${item.batteryLevelMax ?: 100}%"

        ConditionType.CHARGING -> context.getString(R.string.condition_charging)

        ConditionType.CHARGING_CHANGED -> context.getString(
            if (item.charging == false) R.string.charging_stopped else R.string.charging_started,
        )

        ConditionType.TIME_RANGE -> {
            val base = "${formatTimeOfDay(item.startMinute ?: 0)} - ${formatTimeOfDay(item.endMinute ?: 0)}"
            weekdaySummary(context, item.timeWeekdays)?.let { "$base · $it" } ?: base
        }

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
            when (item.ringerMode ?: RingerMode.NORMAL) {
                RingerMode.SILENT -> R.string.condition_silent
                RingerMode.VIBRATE -> R.string.condition_ringer_vibrate
                RingerMode.NORMAL -> R.string.condition_ringing
            },
        )

        ConditionType.MUSIC_PLAYING -> context.getString(
            if (item.musicPlaying == true) R.string.music_is_playing else R.string.music_not_playing,
        )

        ConditionType.MUSIC_CHANGED -> context.getString(
            when (item.playbackDirection) {
                null -> R.string.condition_unlimited
                PlaybackDirection.STARTED -> R.string.music_started
                PlaybackDirection.STOPPED -> R.string.music_stopped
            },
        )

        ConditionType.TORCH_ON -> context.getString(
            if (item.torchOn == true) R.string.torch_is_on else R.string.torch_is_off,
        )

        ConditionType.TORCH_CHANGED -> context.getString(
            when (item.torchChangedTo) {
                null -> R.string.condition_unlimited
                true -> R.string.state_on
                false -> R.string.state_off
            },
        )

        ConditionType.DND_ON -> context.getString(
            if (item.dndOn == true) R.string.dnd_is_on else R.string.dnd_is_off,
        )

        ConditionType.DND_CHANGED -> context.getString(
            when (item.dndChangedTo) {
                null -> R.string.condition_unlimited
                true -> R.string.state_on
                false -> R.string.state_off
            },
        )

        ConditionType.NETWORK_TYPE -> networkTypeLabel(context, item.networkType ?: NetworkType.WIFI)

        ConditionType.HEADPHONES -> context.getString(
            if (item.plugged == true) R.string.headphones_plugged else R.string.headphones_unplugged,
        )

        ConditionType.BLUETOOTH -> bluetoothSummary(context, item)

        ConditionType.NOTIFICATION_POSTED,
        ConditionType.NOTIFICATION_REMOVED,
        ConditionType.NOTIFICATION_EXISTS -> notificationSummary(context, item)

        ConditionType.APP_PACKAGE_CHANGED -> {
            val action = when (item.packageChangeAction) {
                hunoia.luno.core.domain.PackageChangeAction.ADDED ->
                    context.getString(R.string.condition_package_action_added)
                hunoia.luno.core.domain.PackageChangeAction.REMOVED ->
                    context.getString(R.string.condition_package_action_removed)
                hunoia.luno.core.domain.PackageChangeAction.REPLACED ->
                    context.getString(R.string.condition_package_action_replaced)
                hunoia.luno.core.domain.PackageChangeAction.CHANGED ->
                    context.getString(R.string.condition_package_action_changed)
                else -> context.getString(R.string.condition_package_action_any)
            }
            val apps = item.packageNames.take(2).joinToString(", ")
            if (apps.isBlank()) action else "$action · $apps"
        }

        ConditionType.INTERVAL -> context.getString(
            R.string.condition_interval_minutes,
        ) + " ${item.intervalMinutes ?: 5}"

        ConditionType.SHARE_RECEIVED -> context.getString(R.string.condition_share_received)
        ConditionType.CLIPBOARD_CHANGED -> context.getString(R.string.condition_clipboard_changed)
        ConditionType.WIFI_SSID -> if (item.wifiSsid.isBlank()) {
            context.getString(R.string.condition_wifi_ssid)
        } else {
            "${context.getString(R.string.condition_wifi_ssid)}: ${item.wifiSsid}"
        }

        ConditionType.GESTURE_PERFORMED -> context.getString(R.string.condition_gesture_any)

        ConditionType.BLUETOOTH_CHANGED,
        ConditionType.HEADPHONES_CHANGED -> context.getString(
            when (item.connectionDirection) {
                null -> R.string.condition_unlimited
                hunoia.luno.config.model.ConnectionDirection.CONNECTED -> R.string.condition_connected
                hunoia.luno.config.model.ConnectionDirection.DISCONNECTED -> R.string.condition_disconnected
            },
        )

        ConditionType.SCHEDULED_TIME -> {
            val times = item.scheduledMinutes.sorted()
                .take(3).joinToString(", ") { formatTimeOfDay(it) }
            if (item.scheduledWeekdays.isEmpty()) times
            else "$times · ${context.getString(R.string.condition_scheduled_weekdays)}"
        }

        ConditionType.AIRPLANE_MODE -> context.getString(
            if (item.airplaneMode == true) R.string.state_on else R.string.state_off,
        )

        ConditionType.MANUAL -> context.getString(R.string.condition_manual)

        ConditionType.VAR_COMPARE -> {
            val name = item.compareVarName.ifBlank { "var" }
            val op = variableCompareOpLabel(context, item.compareOp)
            if (item.compareOp == hunoia.luno.config.model.VariableCompareOp.IS_EMPTY ||
                item.compareOp == hunoia.luno.config.model.VariableCompareOp.IS_NOT_EMPTY
            ) {
                "$name $op"
            } else {
                "$name $op ${item.compareValue}"
            }
        }

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
        if (item.bluetoothDeviceName.isNotBlank()) {
            add(context.getString(R.string.condition_bluetooth_device_name) + ":" + item.bluetoothDeviceName)
        }
        if (item.bluetoothDeviceAddress.isNotBlank()) {
            add(context.getString(R.string.condition_bluetooth_device_address) + ":" + item.bluetoothDeviceAddress)
        }
    }
    return if (parts.isEmpty()) context.getString(R.string.condition_bluetooth)
    else parts.joinToString(" / ")
}

private fun notificationSummary(context: Context, item: ConditionItem): String {
    val apps = when {
        item.notifyPackages.isEmpty() -> context.getString(R.string.condition_apps_empty)
        else -> item.notifyPackages.take(2).joinToString(", ")
    }
    val keyword = listOf(item.notifyTitleKeyword, item.notifyTextKeyword, item.notifyKeyword)
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
    val base = when (item.type) {
        ConditionType.NOTIFICATION_REMOVED -> context.getString(R.string.condition_notification_removed)
        ConditionType.NOTIFICATION_EXISTS -> context.getString(
            if (item.notifyExists == false) R.string.condition_notify_exists_no
            else R.string.condition_notify_exists_yes,
        )
        else -> context.getString(R.string.condition_notification_posted)
    }
    return buildString {
        append(base)
        if (item.notifyPackages.isNotEmpty()) append(" · ").append(apps)
        if (keyword.isNotBlank()) append(" · ").append(keyword)
        if (item.notifyExcludeKeyword.isNotBlank()) append(" · !").append(item.notifyExcludeKeyword)
    }
}

/** 时间范围的星期摘要：工作日/周末走快捷文案，自定义组合按周一~周日拼接单字。 */
private fun weekdaySummary(context: Context, weekdays: Set<Int>): String? {
    if (weekdays.isEmpty()) return null
    val workdays = setOf(
        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY,
    )
    val weekend = setOf(Calendar.SATURDAY, Calendar.SUNDAY)
    return when {
        weekdays == workdays -> context.getString(R.string.condition_weekdays_workday)
        weekdays == weekend -> context.getString(R.string.condition_weekdays_weekend)
        else -> WEEKDAY_ORDER.filter { it.first in weekdays }.joinToString("") { context.getString(it.second) }
    }
}

