package hunoia.luno.runtime.condition

import android.app.NotificationManager
import android.app.PendingIntent
import hunoia.luno.core.domain.AudioStream
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.BooleanMode
import hunoia.luno.config.model.ConditionItem
import hunoia.luno.config.model.ConditionType
import hunoia.luno.config.model.ConnectionDirection
import hunoia.luno.config.model.KeyboardEventType
import hunoia.luno.config.model.NetworkType
import hunoia.luno.config.model.NotificationMatchMode
import hunoia.luno.config.model.PlaybackDirection
import hunoia.luno.config.model.RingerMode
import hunoia.luno.config.model.ScreenEventType
import hunoia.luno.config.model.ScreenType
import hunoia.luno.config.model.VariableCompareOp
import hunoia.luno.config.model.WhenGroup
import hunoia.luno.runtime.GestureRuntimeState
import hunoia.luno.runtime.VolumeChange
import java.util.Calendar
import hunoia.luno.core.domain.formatTimeOfDay
import hunoia.luno.core.domain.nowDayOfWeek
import hunoia.luno.core.domain.nowMinuteOfDay

/** 间隔触发键：规则 id + 分钟数，允许同一规则里多个不同间隔各自到期。 */
fun intervalDueKey(ruleId: String, item: ConditionItem): String? =
    item.intervalMinutes?.let { "$ruleId:$it" }

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

fun GestureRuntimeState.toConditionContext(
    variables: Map<String, String> = emptyMap(),
): ConditionContext = ConditionContext(
    nowMs = nowMs,
    packageName = currentPackageName,
    isLockScreen = isNowInLockScreenPage,
    isLauncher = isInLauncher,
    isLandscape = isLandscape,
    isKeyboardInput = isKeyboardInputActive,
    isCharging = isCharging,
    batteryLevel = batteryLevel,
    minuteOfDay = minuteOfDay,
    dayOfWeek = dayOfWeek,
    networkType = networkType,
    wifiSsid = wifiSsid,
    headphonesConnected = headphonesConnected,
    bluetoothAdapterOn = bluetoothAdapterOn,
    bluetoothAudioConnected = bluetoothAudioConnected,
    bluetoothDeviceName = bluetoothDeviceName,
    bluetoothDeviceAddress = bluetoothDeviceAddress,
    airplaneMode = airplaneMode,
    ringerMode = ringerMode,
    volumePercent = volumePercent,
    isMusicPlaying = isMusicPlaying,
    isTorchOn = isTorchOn,
    isDndOn = isDndOn,
    appChangedTo = appChangedTo,
    screenEvent = screenEvent,
    chargingChangedTo = chargingChangedTo,
    volumeChanged = volumeChanged,
    notificationPosted = notificationPosted,
    notificationRemoved = notificationRemoved,
    activeNotifications = activeNotifications,
    packageChanged = packageChanged,
    shareReceived = shareReceived,
    clipboardChanged = clipboardChanged,
    intervalDueKeys = intervalDueKeys,
    bluetoothChanged = bluetoothChanged,
    headphonesChanged = headphonesChanged,
    scheduledTimeHit = scheduledTimeHit,
    gesturePerformedId = gesturePerformedId,
    gesturePerformedName = gesturePerformedName,
    keyboardEvent = keyboardEvent,
    musicChanged = musicChanged,
    torchChangedTo = torchChangedTo,
    dndChangedTo = dndChangedTo,
    variables = variables,
)

fun AutomationRule.matches(ctx: ConditionContext, ruleId: String? = null): Boolean =
    condition.matches(ctx, ruleId)

fun WhenGroup.matches(ctx: ConditionContext, ruleId: String? = null): Boolean {
    if (items.isEmpty()) return false
    return when (mode) {
        BooleanMode.AND -> items.all { it.matches(ctx, ruleId) }
        BooleanMode.OR -> items.any { it.matches(ctx, ruleId) }
    }
}

fun ConditionItem.matches(ctx: ConditionContext, ruleId: String? = null): Boolean {
    // 手动触发：恒假且无视取反（取反恒真会变成永远触发），只供手势/runRule 调用。
    if (type == ConditionType.MANUAL) return false
    val raw = when {
        type == ConditionType.FOREGROUND_APP -> ctx.packageName in packageNames
        type == ConditionType.SCREEN_STATE -> screenStateMatches(ctx)
        type == ConditionType.BATTERY_RANGE -> rangeContains(ctx.batteryLevel, batteryLevelMin, batteryLevelMax)
        type == ConditionType.CHARGING -> (charging ?: false) == ctx.isCharging
        type == ConditionType.TIME_RANGE -> inTimeWindow(ctx.minuteOfDay, startMinute, endMinute) &&
            (timeWeekdays.isEmpty() || ctx.dayOfWeek in timeWeekdays)
        type == ConditionType.VOLUME_RANGE -> rangeContains(ctx.volumePercent[audioStream], volumeMin, volumeMax)
        type == ConditionType.RINGER_SILENT -> ringerMatches(ctx)
        type == ConditionType.NETWORK_TYPE -> networkType == ctx.networkType
        type == ConditionType.WIFI_SSID -> wifiSsidMatches(ctx)
        type == ConditionType.HEADPHONES -> (plugged ?: false) == ctx.headphonesConnected
        type == ConditionType.BLUETOOTH -> bluetoothMatches(ctx)
        type == ConditionType.AIRPLANE_MODE -> (airplaneMode ?: false) == ctx.airplaneMode
        type == ConditionType.MUSIC_PLAYING -> (musicPlaying ?: false) == ctx.isMusicPlaying
        type == ConditionType.TORCH_ON -> (torchOn ?: false) == ctx.isTorchOn
        type == ConditionType.DND_ON -> (dndOn ?: false) == ctx.isDndOn
        type == ConditionType.VAR_COMPARE -> variableMatches(ctx.variables)
        type == ConditionType.APP_CHANGED_TO -> ctx.appChangedTo != null && ctx.appChangedTo in packageNames
        type == ConditionType.SCREEN_EVENT -> ctx.screenEvent == screenEvent
        type == ConditionType.KEYBOARD_EVENT -> ctx.keyboardEvent != null && ctx.keyboardEvent == keyboardEvent
        type == ConditionType.CHARGING_CHANGED -> ctx.chargingChangedTo != null && ctx.chargingChangedTo == charging
        type == ConditionType.VOLUME_CHANGED -> volumeChangedMatches(ctx)
        type == ConditionType.MUSIC_CHANGED -> ctx.musicChanged != null &&
            (playbackDirection == null || playbackDirection == ctx.musicChanged)
        type == ConditionType.TORCH_CHANGED -> ctx.torchChangedTo != null && torchChangedTo == ctx.torchChangedTo
        type == ConditionType.DND_CHANGED -> ctx.dndChangedTo != null && dndChangedTo == ctx.dndChangedTo
        type == ConditionType.NOTIFICATION_POSTED -> notificationMatches(ctx.notificationPosted)
        type == ConditionType.NOTIFICATION_REMOVED -> notificationMatches(ctx.notificationRemoved)
        type == ConditionType.NOTIFICATION_EXISTS -> notificationExistsMatches(ctx)
        type == ConditionType.APP_PACKAGE_CHANGED -> packageChangedMatches(ctx)
        type == ConditionType.INTERVAL -> intervalMatches(ctx, ruleId)
        type == ConditionType.SHARE_RECEIVED -> ctx.shareReceived != null
        type == ConditionType.CLIPBOARD_CHANGED -> ctx.clipboardChanged?.let {
            clipboardKeyword.isBlank() || it.text.contains(clipboardKeyword, ignoreCase = true)
        } == true
        type == ConditionType.BLUETOOTH_CHANGED -> connectionMatches(ctx.bluetoothChanged)
        type == ConditionType.HEADPHONES_CHANGED -> connectionMatches(ctx.headphonesChanged)
        type == ConditionType.SCHEDULED_TIME -> ctx.scheduledTimeHit && scheduledTimeMatches(ctx)
        type == ConditionType.GESTURE_PERFORMED -> ctx.gesturePerformedId != null
        type == ConditionType.GROUP -> group?.matches(ctx, ruleId) == true
        else -> false
    }
    return if (negated) !raw else raw
}

private fun ConditionItem.variableMatches(variables: Map<String, String>): Boolean {
    val name = compareVarName.trim()
    if (name.isEmpty()) return false
    val key = if (name.startsWith("var.")) name else "var.$name"
    val left = variables[key] ?: return false
    return compareValues(left, compareOp, compareValue)
}

/** 条件变量比较：数值运算优先按 double，文本运算保留原大小写。 */
fun compareValues(left: String, op: String, right: String): Boolean {
    val ln = left.trim().toDoubleOrNull()
    val rn = right.trim().toDoubleOrNull()
    val numeric = ln != null && rn != null
    return when (op) {
        VariableCompareOp.EQUALS -> if (numeric) ln == rn else left == right
        VariableCompareOp.NOT_EQUALS -> !(if (numeric) ln == rn else left == right)
        VariableCompareOp.GREATER -> ln != null && rn != null && ln > rn
        VariableCompareOp.GREATER_OR_EQUALS -> ln != null && rn != null && ln >= rn
        VariableCompareOp.LESS -> ln != null && rn != null && ln < rn
        VariableCompareOp.LESS_OR_EQUALS -> ln != null && rn != null && ln <= rn
        VariableCompareOp.CONTAINS -> left.contains(right)
        VariableCompareOp.STARTS_WITH -> left.startsWith(right)
        VariableCompareOp.ENDS_WITH -> left.endsWith(right)
        VariableCompareOp.IS_EMPTY -> left.isEmpty()
        VariableCompareOp.IS_NOT_EMPTY -> left.isNotEmpty()
        else -> false
    }
}

private fun ConditionItem.connectionMatches(pulse: ConnectionDirection?): Boolean {
    if (pulse == null) return false
    return connectionDirection == null || connectionDirection == pulse
}

private fun ConditionItem.notificationMatches(pulse: NotificationPulse?): Boolean {
    if (pulse == null) return false
    if (notifyPackages.isNotEmpty() && pulse.packageName !in notifyPackages) return false
    if (notifyExcludePackages.isNotEmpty() && pulse.packageName in notifyExcludePackages) return false
    if (notifyOngoing != null && pulse.ongoing != notifyOngoing) return false
    if (notifyClearable != null && pulse.clearable != notifyClearable) return false
    val importanceAtLeast = notifyImportanceAtLeast
    if (importanceAtLeast != null && pulse.importance < importanceAtLeast) return false

    val mode = notifyMatchMode.takeIf { it in NotificationMatchMode.all }
        ?: NotificationMatchMode.CONTAINS
    // 旧版单关键字继续兼容：标题或正文任意命中即可。
    if (notifyKeyword.isNotBlank() &&
        !textMatches(pulse.title, notifyKeyword, mode) &&
        !textMatches(pulse.text, notifyKeyword, mode)
    ) return false
    if (notifyTitleKeyword.isNotBlank() && !textMatches(pulse.title, notifyTitleKeyword, mode)) return false
    if (notifyTextKeyword.isNotBlank() && !textMatches(pulse.text, notifyTextKeyword, mode)) return false
    if (notifyExcludeKeyword.isNotBlank() &&
        (textMatches(pulse.title, notifyExcludeKeyword, mode) ||
            textMatches(pulse.text, notifyExcludeKeyword, mode))
    ) return false
    return true
}

/** 通知当前是否存在：对 activeNotifications 快照套用同一套过滤器。 */
private fun ConditionItem.notificationExistsMatches(ctx: ConditionContext): Boolean {
    val exists = ctx.activeNotifications.any { notificationMatches(it) }
    return exists == (notifyExists ?: true)
}

private fun textMatches(source: String, pattern: String, mode: String): Boolean = when (mode) {
    NotificationMatchMode.REGEX -> runCatching {
        Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(source)
    }.getOrDefault(false)

    NotificationMatchMode.GLOB -> {
        val regex = buildString {
            append(".*")
            for (ch in pattern) {
                when (ch) {
                    '*' -> append(".*")
                    '?' -> append('.')
                    else -> append(Regex.escape(ch.toString()))
                }
            }
            append(".*")
        }
        runCatching { Regex(regex, RegexOption.IGNORE_CASE).containsMatchIn(source) }
            .getOrDefault(false)
    }

    else -> source.contains(pattern, ignoreCase = true)
}

private fun ConditionItem.packageChangedMatches(ctx: ConditionContext): Boolean {
    val pulse = ctx.packageChanged ?: return false
    if (packageNames.isNotEmpty() && pulse.packageName !in packageNames) return false
    return packageChangeAction == null || packageChangeAction == pulse.action
}

private fun ConditionItem.intervalMatches(ctx: ConditionContext, ruleId: String?): Boolean {
    val key = ruleId?.let { intervalDueKey(it, this) } ?: return false
    return key in ctx.intervalDueKeys
}

private fun ConditionItem.wifiSsidMatches(ctx: ConditionContext): Boolean {
    if (ctx.networkType != NetworkType.WIFI) return false
    if (wifiSsid.isBlank()) return true
    val actual = ctx.wifiSsid ?: return false
    return actual.equals(wifiSsid.trim(), ignoreCase = true)
}

private fun ConditionItem.scheduledTimeMatches(ctx: ConditionContext): Boolean {
    // 星期由 P2 ticker 保证（只在星期命中的那天发脉冲），此处只校验分钟。
    if (scheduledMinutes.isEmpty()) return false
    return ctx.minuteOfDay in scheduledMinutes
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
    val hasDeviceFilter = bluetoothDeviceName.isNotBlank() || bluetoothDeviceAddress.isNotBlank()
    if (expectedBluetoothAdapterOn == null &&
        expectedBluetoothAudioConnected == null &&
        !hasDeviceFilter
    ) return false
    val adapterOk = expectedBluetoothAdapterOn == null ||
        expectedBluetoothAdapterOn == ctx.bluetoothAdapterOn
    val audioOk = expectedBluetoothAudioConnected == null ||
        expectedBluetoothAudioConnected == ctx.bluetoothAudioConnected
    if (!adapterOk) return false
    // 指定设备意味着必须已连接；读取失败（null）按不匹配处理，避免误触。
    if (hasDeviceFilter) {
        if (ctx.bluetoothAudioConnected != true) return false
        if (bluetoothDeviceName.isNotBlank() &&
            !ctx.bluetoothDeviceName.equals(bluetoothDeviceName.trim(), ignoreCase = true)
        ) return false
        if (bluetoothDeviceAddress.isNotBlank() &&
            !ctx.bluetoothDeviceAddress.equals(bluetoothDeviceAddress.trim(), ignoreCase = true)
        ) return false
    }
    return audioOk
}

private fun ConditionItem.volumeChangedMatches(ctx: ConditionContext): Boolean {
    val pulse = ctx.volumeChanged ?: return false
    val streamOk = audioStream == null || pulse.stream == audioStream
    val dirOk = volumeDirection == null || pulse.direction == volumeDirection
    return streamOk && dirOk
}

/** 铃声三态。 */
private fun ConditionItem.ringerMatches(ctx: ConditionContext): Boolean {
    val expected = ringerMode ?: RingerMode.NORMAL
    return expected == ctx.ringerMode
}

data class TimeWindow(
    val startMinute: Int,
    val endMinute: Int,
    /** Calendar 常量；空=每天。 */
    val weekdays: Set<Int> = emptySet(),
) {
    fun matches(minuteOfDay: Int, dayOfWeek: Int): Boolean =
        inTimeWindow(minuteOfDay, startMinute, endMinute) &&
            (weekdays.isEmpty() || dayOfWeek in weekdays)
}

fun WhenGroup.timeRanges(): List<TimeWindow> = items.flatMap { item ->
    when (item.type) {
        ConditionType.TIME_RANGE -> listOf(TimeWindow(item.startMinute ?: 0, item.endMinute ?: 0, item.timeWeekdays))
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

/** 音乐条件：决定播放回调的注册/注销（事件驱动，不注册零开销）。 */
fun WhenGroup.hasMusicCondition(): Boolean = items.any { item ->
    when (item.type) {
        ConditionType.MUSIC_PLAYING,
        ConditionType.MUSIC_CHANGED -> true

        ConditionType.GROUP -> item.group?.hasMusicCondition() == true
        else -> false
    }
}

/** 手电筒条件：决定 torch 回调的注册/注销。 */
fun WhenGroup.hasTorchCondition(): Boolean = items.any { item ->
    when (item.type) {
        ConditionType.TORCH_ON,
        ConditionType.TORCH_CHANGED -> true

        ConditionType.GROUP -> item.group?.hasTorchCondition() == true
        else -> false
    }
}

/** 定时触发项（递归进 GROUP；星期由 ticker 保证，此处只收分钟表）。 */
fun WhenGroup.scheduledItems(): List<ConditionItem> = items.flatMap { item ->
    when (item.type) {
        ConditionType.SCHEDULED_TIME -> listOf(item)
        ConditionType.GROUP -> item.group?.scheduledItems() ?: emptyList()
        else -> emptyList()
    }
}

fun WhenGroup.hasScheduledTime(): Boolean = scheduledItems().isNotEmpty()

/** 间隔触发项（递归进 GROUP）：ticker 用 rule.id + 分钟数维护 lastFiredAt。 */
fun WhenGroup.intervalItems(): List<ConditionItem> = items.flatMap { item ->
    when (item.type) {
        ConditionType.INTERVAL -> listOfNotNull(item.takeIf { it.intervalMinutes != null })
        ConditionType.GROUP -> item.group?.intervalItems() ?: emptyList()
        else -> emptyList()
    }
}

fun WhenGroup.hasIntervalCondition(): Boolean = intervalItems().isNotEmpty()

fun WhenGroup.hasLeaf(): Boolean = items.any { item ->
    if (item.type == ConditionType.GROUP) item.group?.hasLeaf() == true else true
}
