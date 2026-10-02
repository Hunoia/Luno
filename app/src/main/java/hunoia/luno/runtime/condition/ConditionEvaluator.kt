package hunoia.luno.runtime.condition

import hunoia.luno.action.model.AudioStream
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.BooleanMode
import hunoia.luno.config.model.ConditionItem
import hunoia.luno.config.model.ConditionType
import hunoia.luno.config.model.NetworkType
import hunoia.luno.config.model.ScreenEventType
import hunoia.luno.config.model.ScreenType
import hunoia.luno.config.model.WhenGroup
import hunoia.luno.runtime.GestureRuntimeState
import hunoia.luno.runtime.VolumeChange
import java.util.Calendar

data class ConditionContext(
    val nowMs: Long,
    val packageName: String,
    val isLockScreen: Boolean,
    val isLauncher: Boolean,
    val isLandscape: Boolean,
    val isKeyboardInput: Boolean,
    val isCharging: Boolean,
    val batteryLevel: Int,
    val minuteOfDay: Int,

    val networkType: NetworkType,
    val headphonesConnected: Boolean,
    val bluetoothAdapterOn: Boolean?,
    val bluetoothAudioConnected: Boolean?,
    val airplaneMode: Boolean,
    val isRingerSilent: Boolean,
    val volumePercent: Map<AudioStream, Int>,

    val appChangedTo: String?,
    val screenEvent: ScreenEventType?,
    val chargingChangedTo: Boolean?,
    val volumeChanged: VolumeChange?,
)

fun nowMinuteOfDay(): Int {
    val calendar = Calendar.getInstance()
    return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
}

fun formatTimeOfDay(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

fun inTimeWindow(minuteOfDay: Int, start: Int?, end: Int?): Boolean {
    val s = start ?: return false
    val e = end ?: return false
    return if (s <= e) minuteOfDay in s..e else minuteOfDay >= s || minuteOfDay < e
}

fun rangeContains(actual: Int?, min: Int?, max: Int?): Boolean {
    val value = actual ?: return false
    if (value < 0) return false
    return (min == null || value >= min) && (max == null || value <= max)
}

fun GestureRuntimeState.toConditionContext(): ConditionContext = ConditionContext(
    nowMs = nowMs,
    packageName = currentPackageName,
    isLockScreen = isNowInLockScreenPage,
    isLauncher = isInLauncher,
    isLandscape = isLandscape,
    isKeyboardInput = isKeyboardInputActive,
    isCharging = isCharging,
    batteryLevel = batteryLevel,
    minuteOfDay = minuteOfDay,
    networkType = networkType,
    headphonesConnected = headphonesConnected,
    bluetoothAdapterOn = bluetoothAdapterOn,
    bluetoothAudioConnected = bluetoothAudioConnected,
    airplaneMode = airplaneMode,
    isRingerSilent = isRingerSilent,
    volumePercent = volumePercent,
    appChangedTo = appChangedTo,
    screenEvent = screenEvent,
    chargingChangedTo = chargingChangedTo,
    volumeChanged = volumeChanged,
)

fun AutomationRule.matches(ctx: ConditionContext): Boolean = condition.matches(ctx)

fun WhenGroup.matches(ctx: ConditionContext): Boolean {
    if (items.isEmpty()) return false
    return if (mode == BooleanMode.AND) items.all { it.matches(ctx) }
    else items.any { it.matches(ctx) }
}

fun ConditionItem.matches(ctx: ConditionContext): Boolean {
    val raw = when {
        type == ConditionType.FOREGROUND_APP -> ctx.packageName in packageNames
        type == ConditionType.SCREEN_STATE -> screenStateMatches(ctx)
        type == ConditionType.BATTERY_RANGE -> rangeContains(ctx.batteryLevel, batteryLevelMin, batteryLevelMax)
        type == ConditionType.CHARGING -> (charging ?: false) == ctx.isCharging
        type == ConditionType.TIME_RANGE -> inTimeWindow(ctx.minuteOfDay, startMinute, endMinute)
        type == ConditionType.VOLUME_RANGE -> rangeContains(ctx.volumePercent[audioStream], volumeMin, volumeMax)
        type == ConditionType.RINGER_SILENT -> (silent ?: false) == ctx.isRingerSilent
        type == ConditionType.NETWORK_TYPE -> networkType == ctx.networkType
        type == ConditionType.HEADPHONES -> (plugged ?: false) == ctx.headphonesConnected
        type == ConditionType.BLUETOOTH -> bluetoothMatches(ctx)
        type == ConditionType.AIRPLANE_MODE -> (airplaneMode ?: false) == ctx.airplaneMode
        type == ConditionType.APP_CHANGED_TO -> ctx.appChangedTo != null && ctx.appChangedTo in packageNames
        type == ConditionType.SCREEN_EVENT -> ctx.screenEvent == screenEvent
        type == ConditionType.CHARGING_CHANGED -> ctx.chargingChangedTo != null && ctx.chargingChangedTo == charging
        type == ConditionType.VOLUME_CHANGED -> volumeChangedMatches(ctx)
        type == ConditionType.GROUP -> group?.matches(ctx) == true
        else -> false
    }
    return if (negated) !raw else raw
}

private fun ConditionItem.screenStateMatches(ctx: ConditionContext): Boolean = when (screenType) {
    ScreenType.LOCK_SCREEN -> ctx.isLockScreen
    ScreenType.LAUNCHER -> ctx.isLauncher
    ScreenType.LANDSCAPE -> ctx.isLandscape
    ScreenType.PORTRAIT -> !ctx.isLandscape
    ScreenType.KEYBOARD_INPUT -> ctx.isKeyboardInput
    null -> false
}

private fun ConditionItem.bluetoothMatches(ctx: ConditionContext): Boolean {
    if (expectedBluetoothAdapterOn == null && expectedBluetoothAudioConnected == null) return false
    val adapterOk = expectedBluetoothAdapterOn == null ||
        expectedBluetoothAdapterOn == ctx.bluetoothAdapterOn
    val audioOk = expectedBluetoothAudioConnected == null ||
        expectedBluetoothAudioConnected == ctx.bluetoothAudioConnected
    return adapterOk && audioOk
}

private fun ConditionItem.volumeChangedMatches(ctx: ConditionContext): Boolean {
    val pulse = ctx.volumeChanged ?: return false
    val streamOk = audioStream == null || pulse.stream == audioStream
    val dirOk = volumeDirection == null || pulse.direction == volumeDirection
    return streamOk && dirOk
}

data class TimeWindow(val startMinute: Int, val endMinute: Int) {
    fun matchesMinute(minuteOfDay: Int): Boolean = inTimeWindow(minuteOfDay, startMinute, endMinute)
}

fun WhenGroup.timeRanges(): List<TimeWindow> = items.flatMap { item ->
    when (item.type) {
        ConditionType.TIME_RANGE -> listOf(TimeWindow(item.startMinute ?: 0, item.endMinute ?: 0))
        ConditionType.GROUP -> item.group?.timeRanges() ?: emptyList()
        else -> emptyList()
    }
}

fun WhenGroup.hasTimeRange(): Boolean = timeRanges().isNotEmpty()

fun WhenGroup.hasVolumeCondition(): Boolean = items.any { item ->
    when (item.type) {
        ConditionType.VOLUME_RANGE,
        ConditionType.VOLUME_CHANGED,
        ConditionType.RINGER_SILENT -> true

        ConditionType.GROUP -> item.group?.hasVolumeCondition() == true
        else -> false
    }
}

fun WhenGroup.hasLeaf(): Boolean = items.any { item ->
    if (item.type == ConditionType.GROUP) item.group?.hasLeaf() == true else true
}
