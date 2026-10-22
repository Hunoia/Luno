package hunoia.luno.runtime.condition

import hunoia.luno.action.model.AudioStream
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.BooleanMode
import hunoia.luno.config.model.ConditionItem
import hunoia.luno.config.model.ConditionType
import hunoia.luno.config.model.ConnectionDirection
import hunoia.luno.config.model.KeyboardEventType
import hunoia.luno.config.model.NetworkType
import hunoia.luno.config.model.PlaybackDirection
import hunoia.luno.config.model.RingerMode
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
    val dayOfWeek: Int,

    val networkType: NetworkType,
    val headphonesConnected: Boolean,
    val bluetoothAdapterOn: Boolean?,
    val bluetoothAudioConnected: Boolean?,
    val airplaneMode: Boolean,
    val ringerMode: RingerMode,
    val volumePercent: Map<AudioStream, Int>,
    val isMusicPlaying: Boolean,
    val isTorchOn: Boolean,
    val isDndOn: Boolean,

    val appChangedTo: String?,
    val screenEvent: ScreenEventType?,
    val chargingChangedTo: Boolean?,
    val volumeChanged: VolumeChange?,

    // v2 新增脉冲（事件源在 P2 接入；null=无脉冲）
    val notificationPosted: NotificationPulse? = null,
    val bluetoothChanged: ConnectionDirection? = null,
    val headphonesChanged: ConnectionDirection? = null,
    val scheduledTimeHit: Boolean = false,
    /** 手势脉冲：本轮命中的按钮 id/名称，无则 null。 */
    val gesturePerformedId: String? = null,
    val gesturePerformedName: String? = null,
    /** 本轮执行的自动化规则名（引擎触发时写入，模板变量 trigger.rule.name 用）。 */
    val triggerRuleName: String? = null,
    /** v3 脉冲：键盘弹出/收起、音乐启停、手电/勿扰开关变化。 */
    val keyboardEvent: KeyboardEventType? = null,
    val musicChanged: PlaybackDirection? = null,
    val torchChangedTo: Boolean? = null,
    val dndChangedTo: Boolean? = null,
)

/** 通知脉冲：包名+标题+正文（匹配后原文即丢，历史默认不同原文）。 */
data class NotificationPulse(
    val packageName: String,
    val title: String,
    val text: String,
)

/**
 * 每条求值路径一个 Calendar,避免 Calendar.getInstance() 每次做时区与 locale 查找。
 * ThreadLocal 而不是单个共享实例:AutomationEngine.evaluate() 从 Main 和 IO 两条路径进来,
 * 共享的可变 Calendar 会竞争。
 */
private val minuteOfDayCalendar = ThreadLocal.withInitial { Calendar.getInstance() }

// @Volatile: 两个线程同时跨过整分时最多各算一次并写入同样的值,不需要加锁。
@Volatile private var cachedMinuteOfDay = -1
@Volatile private var cachedDayOfWeek = -1
@Volatile private var cachedAtSecond = -1L

/** 分钟与星期共用一个 60s 缓存：跨整分重读一次日历，时区改动最多 60s 自纠。 */
private fun refreshCalendarCacheIfNeeded() {
    val nowSecond = System.currentTimeMillis() / 1000L
    if (cachedMinuteOfDay >= 0 && nowSecond - cachedAtSecond < 60L) return
    val calendar = minuteOfDayCalendar.get()
    calendar.timeInMillis = System.currentTimeMillis()
    cachedMinuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    cachedDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
    cachedAtSecond = nowSecond
}

fun nowMinuteOfDay(): Int {
    refreshCalendarCacheIfNeeded()
    return cachedMinuteOfDay
}

/** Calendar.DAY_OF_WEEK 常量（SUNDAY=1..SATURDAY=7）。 */
fun nowDayOfWeek(): Int {
    refreshCalendarCacheIfNeeded()
    return cachedDayOfWeek
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
    dayOfWeek = dayOfWeek,
    networkType = networkType,
    headphonesConnected = headphonesConnected,
    bluetoothAdapterOn = bluetoothAdapterOn,
    bluetoothAudioConnected = bluetoothAudioConnected,
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
    bluetoothChanged = bluetoothChanged,
    headphonesChanged = headphonesChanged,
    scheduledTimeHit = scheduledTimeHit,
    gesturePerformedId = gesturePerformedId,
    gesturePerformedName = gesturePerformedName,
    keyboardEvent = keyboardEvent,
    musicChanged = musicChanged,
    torchChangedTo = torchChangedTo,
    dndChangedTo = dndChangedTo,
)

fun AutomationRule.matches(ctx: ConditionContext): Boolean = condition.matches(ctx)

fun WhenGroup.matches(ctx: ConditionContext): Boolean {
    if (items.isEmpty()) return false
    return when (mode) {
        BooleanMode.AND -> items.all { it.matches(ctx) }
        BooleanMode.OR -> items.any { it.matches(ctx) }
    }
}

fun ConditionItem.matches(ctx: ConditionContext): Boolean {
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
        type == ConditionType.HEADPHONES -> (plugged ?: false) == ctx.headphonesConnected
        type == ConditionType.BLUETOOTH -> bluetoothMatches(ctx)
        type == ConditionType.AIRPLANE_MODE -> (airplaneMode ?: false) == ctx.airplaneMode
        type == ConditionType.MUSIC_PLAYING -> (musicPlaying ?: false) == ctx.isMusicPlaying
        type == ConditionType.TORCH_ON -> (torchOn ?: false) == ctx.isTorchOn
        type == ConditionType.DND_ON -> (dndOn ?: false) == ctx.isDndOn
        type == ConditionType.APP_CHANGED_TO -> ctx.appChangedTo != null && ctx.appChangedTo in packageNames
        type == ConditionType.SCREEN_EVENT -> ctx.screenEvent == screenEvent
        type == ConditionType.KEYBOARD_EVENT -> ctx.keyboardEvent != null && ctx.keyboardEvent == keyboardEvent
        type == ConditionType.CHARGING_CHANGED -> ctx.chargingChangedTo != null && ctx.chargingChangedTo == charging
        type == ConditionType.VOLUME_CHANGED -> volumeChangedMatches(ctx)
        type == ConditionType.MUSIC_CHANGED -> ctx.musicChanged != null &&
            (playbackDirection == null || playbackDirection == ctx.musicChanged)
        type == ConditionType.TORCH_CHANGED -> ctx.torchChangedTo != null && torchChangedTo == ctx.torchChangedTo
        type == ConditionType.DND_CHANGED -> ctx.dndChangedTo != null && dndChangedTo == ctx.dndChangedTo
        type == ConditionType.NOTIFICATION_POSTED -> notificationMatches(ctx)
        type == ConditionType.BLUETOOTH_CHANGED -> connectionMatches(ctx.bluetoothChanged)
        type == ConditionType.HEADPHONES_CHANGED -> connectionMatches(ctx.headphonesChanged)
        type == ConditionType.SCHEDULED_TIME -> ctx.scheduledTimeHit && scheduledTimeMatches(ctx)
        type == ConditionType.GESTURE_PERFORMED -> ctx.gesturePerformedId != null
        type == ConditionType.GROUP -> group?.matches(ctx) == true
        else -> false
    }
    return if (negated) !raw else raw
}

private fun ConditionItem.connectionMatches(pulse: ConnectionDirection?): Boolean {
    if (pulse == null) return false
    return connectionDirection == null || connectionDirection == pulse
}

private fun ConditionItem.notificationMatches(ctx: ConditionContext): Boolean {
    val pulse = ctx.notificationPosted ?: return false
    if (notifyPackages.isNotEmpty() && pulse.packageName !in notifyPackages) return false
    if (notifyKeyword.isNotBlank() &&
        !pulse.title.contains(notifyKeyword, ignoreCase = true) &&
        !pulse.text.contains(notifyKeyword, ignoreCase = true)
    ) return false
    return true
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

fun WhenGroup.hasLeaf(): Boolean = items.any { item ->
    if (item.type == ConditionType.GROUP) item.group?.hasLeaf() == true else true
}
