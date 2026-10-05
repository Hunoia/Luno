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
enum class FireMode { ENTER, EXIT, BOTH }

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

    const val NOTIFICATION_POSTED = "notificationPosted"
    const val BLUETOOTH_CHANGED = "bluetoothChanged"
    const val HEADPHONES_CHANGED = "headphonesChanged"
    const val SCHEDULED_TIME = "scheduledTime"

    const val GROUP = "group"
}

/** 步骤类型：扁平 kind 字符串（不用 sealed 多态，免得动全局 Json 的 SerializersModule）。 */
object RuleStepKind {
    const val ACTION = "action"
    const val DELAY = "delay"
    const val IF = "if"
}

/** 节流策略类型：同上扁平。 */
object ThrottleKind {
    const val OFF = "off"
    const val COOLDOWN = "cooldown"
    const val WINDOW = "window"
}

/** 通知方向/连接方向：null 表示不限。 */
@Serializable
@Keep
enum class ConnectionDirection { CONNECTED, DISCONNECTED }

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
    // v2 脉冲条件字段（null=不限）
    val notifyPackages: List<String> = emptyList(),
    val notifyKeyword: String = "",
    val connectionDirection: ConnectionDirection? = null,
    val scheduledMinutes: List<Int> = emptyList(),
    val scheduledWeekdays: Set<Int> = emptySet(),
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
    val action: Action? = null,
    /** v2 步骤：非空时优先于 action/entryId（老数据迁移见 effectiveSteps）。 */
    val steps: List<RuleStep> = emptyList(),
) {
    val hasAction: Boolean get() = effectiveSteps().any { it.isEffectiveAction }

    fun covers(buttonId: String): Boolean = when (scope) {
        RuleScope.ALL -> true
        RuleScope.EXCEPT -> buttonId !in buttonIds
        RuleScope.ONLY -> buttonId in buttonIds
    }

    /** 老数据：action/entryId 包成单 ActionStep；新数据直接用 steps。 */
    fun effectiveSteps(): List<RuleStep> {
        if (steps.isNotEmpty()) return steps
        if (action != null || entryId.isNotBlank()) {
            return listOf(RuleStep(kind = RuleStepKind.ACTION, action = action, entryId = entryId))
        }
        return emptyList()
    }

    /**
     * 步骤路径："0" / "2.then.0" / "2.else.1"，编辑器动作选择定位分支内步骤用。
     * 路径非法返回 null / 原样返回（不抛，UI 层容错）。
     */
    fun stepAt(path: String): RuleStep? {
        if (path.isBlank()) return null
        return stepAtTokens(path.split("."), steps)
    }

    fun withStepAt(path: String, transform: (RuleStep) -> RuleStep): RuleEffect {
        if (path.isBlank()) return this
        return copy(steps = withStepAtTokens(path.split("."), steps, transform))
    }
}

private fun stepAtTokens(tokens: List<String>, steps: List<RuleStep>): RuleStep? {
    val index = tokens.firstOrNull()?.toIntOrNull() ?: return null
    val step = steps.getOrNull(index) ?: return null
    if (tokens.size == 1) return step
    val branch = when (tokens.getOrNull(1)) {
        "then" -> step.thenSteps
        "else" -> step.elseSteps
        else -> return null
    }
    return stepAtTokens(tokens.drop(2), branch)
}
/**
 * 条件路径：顶层下标 / 逐级进组，如 [1]、[1, 0]。编辑器 sheet 定位用，非法返回 null/原样。
 */
fun WhenGroup.conditionAt(path: List<Int>): ConditionItem? {
    if (path.isEmpty()) return null
    val item = items.getOrNull(path[0]) ?: return null
    if (path.size == 1) return item
    if (item.type != ConditionType.GROUP) return null
    return item.group?.conditionAt(path.drop(1))
}

fun WhenGroup.withConditionAt(path: List<Int>, transform: (ConditionItem) -> ConditionItem): WhenGroup {
    if (path.isEmpty()) return this
    val index = path[0]
    if (index !in items.indices) return this
    val item = items[index]
    if (path.size == 1) {
        return copy(items = items.toMutableList().also { it[index] = transform(item) })
    }
    if (item.type != ConditionType.GROUP) return this
    val nested = item.group?.withConditionAt(path.drop(1), transform) ?: return this
    return copy(items = items.toMutableList().also { it[index] = item.copy(group = nested) })
}

private fun withStepAtTokens(    tokens: List<String>,
    steps: List<RuleStep>,
    transform: (RuleStep) -> RuleStep,
): List<RuleStep> {
    val index = tokens.firstOrNull()?.toIntOrNull() ?: return steps
    if (index !in steps.indices) return steps
    val step = steps[index]
    if (tokens.size == 1) {
        return steps.toMutableList().also { it[index] = transform(step) }
    }
    val branch = when (tokens.getOrNull(1)) {
        "then" -> step.thenSteps
        "else" -> step.elseSteps
        else -> return steps
    }
    val next = withStepAtTokens(tokens.drop(2), branch, transform)
    val updated = when (tokens[1]) {
        "then" -> step.copy(thenSteps = next)
        else -> step.copy(elseSteps = next)
    }
    return steps.toMutableList().also { it[index] = updated }
}

/**
 * 单个执行步骤。扁平结构（kind 区分），递归只发生在 IF 的 then/else_。
 * 守卫上限见 SequenceExecutor（steps 20 / IF 深 5 / 单 delay 60s / 总 delay 5min）。
 */
@Serializable
@Keep
data class RuleStep(
    val kind: String = RuleStepKind.ACTION,
    val action: Action? = null,
    val entryId: String = "",
    val delayMs: Long = 0L,
    val condition: WhenGroup = WhenGroup(),
    val thenSteps: List<RuleStep> = emptyList(),
    val elseSteps: List<RuleStep> = emptyList(),
) {
    val isEffectiveAction: Boolean
        get() = kind == RuleStepKind.ACTION && (action != null || entryId.isNotBlank())
}

/** 节流策略（替代裸 cooldownMs，老数据迁移见 AutomationRule.effectiveThrottle）。 */
@Serializable
@Keep
data class ThrottlePolicy(
    val kind: String = ThrottleKind.OFF,
    val cooldownMs: Long = 0L,
    val maxCount: Int = 1,
    val windowMs: Long = 60_000L,
)

@Serializable
@Keep
data class AutomationRule(
    val id: String,
    val enabled: Boolean = true,
    val name: String = "",
    val condition: WhenGroup,
    val effect: RuleEffect,
    /** 老字段：只做迁移源，新寫入走 throttle（见 effectiveThrottle）。 */
    val cooldownMs: Long = 0,
    val throttle: ThrottlePolicy = ThrottlePolicy(),
    val fireOn: FireMode = FireMode.ENTER,
) {
    companion object {
        val Defaults: List<AutomationRule> = emptyList()
    }

    /** 老数据 cooldownMs>0 → Cooldown；新数据以 throttle 为准。 */
    fun effectiveThrottle(): ThrottlePolicy {
        if (throttle.kind != ThrottleKind.OFF) return throttle
        if (cooldownMs > 0) return ThrottlePolicy(kind = ThrottleKind.COOLDOWN, cooldownMs = cooldownMs)
        return throttle
    }
}
