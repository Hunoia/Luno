package hunoia.luno.runtime.condition

import hunoia.luno.config.model.Condition
import hunoia.luno.runtime.GestureRuntimeState
import java.util.Calendar

data class ConditionContext(
    val packageName: String,
    val isLockScreen: Boolean,
    val isLauncher: Boolean,
    val isLandscape: Boolean,
    val isKeyboardInput: Boolean,
    val isCharging: Boolean,
    val batteryLevel: Int,
    val minuteOfDay: Int,
)

fun nowMinuteOfDay(): Int {
    val calendar = Calendar.getInstance()
    return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
}

fun formatTimeOfDay(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

fun GestureRuntimeState.toConditionContext(): ConditionContext = ConditionContext(
    packageName = currentPackageName,
    isLockScreen = isNowInLockScreenPage,
    isLauncher = isInLauncher,
    isLandscape = isLandscape,
    isKeyboardInput = isKeyboardInputActive,
    isCharging = isCharging,
    batteryLevel = batteryLevel,
    minuteOfDay = minuteOfDay,
)

fun Condition.matches(ctx: ConditionContext): Boolean = when (this) {
    is Condition.All -> items.all { it.matches(ctx) }
    is Condition.Any -> items.any { it.matches(ctx) }
    is Condition.Not -> !inner.matches(ctx)
    is Condition.ForegroundApp -> ctx.packageName in packageNames
    is Condition.Screen -> when (screenType) {
        hunoia.luno.config.model.ScreenType.LOCK_SCREEN -> ctx.isLockScreen
        hunoia.luno.config.model.ScreenType.LAUNCHER -> ctx.isLauncher
        hunoia.luno.config.model.ScreenType.LANDSCAPE -> ctx.isLandscape
        hunoia.luno.config.model.ScreenType.PORTRAIT -> !ctx.isLandscape
        hunoia.luno.config.model.ScreenType.KEYBOARD_INPUT -> ctx.isKeyboardInput
    }
    is Condition.Battery -> {
        val chargingOk = charging == null || charging == ctx.isCharging
        val levelOk =
            (levelMin == null || (ctx.batteryLevel >= 0 && ctx.batteryLevel >= levelMin)) &&
                (levelMax == null || (ctx.batteryLevel >= 0 && ctx.batteryLevel <= levelMax))
        chargingOk && levelOk
    }
    is Condition.TimeRange -> matchesMinute(ctx.minuteOfDay)
}

fun Condition.timeRanges(): List<Condition.TimeRange> = when (this) {
    is Condition.All -> items.flatMap { it.timeRanges() }
    is Condition.Any -> items.flatMap { it.timeRanges() }
    is Condition.Not -> inner.timeRanges()
    is Condition.TimeRange -> listOf(this)
    is Condition.ForegroundApp -> emptyList()
    is Condition.Screen -> emptyList()
    is Condition.Battery -> emptyList()
}

fun Condition.hasTimeRange(): Boolean = timeRanges().isNotEmpty()

fun Condition.hasLeaf(): Boolean = when (this) {
    is Condition.All -> items.any { it.hasLeaf() }
    is Condition.Any -> items.any { it.hasLeaf() }
    is Condition.Not -> inner.hasLeaf()
    else -> true
}
