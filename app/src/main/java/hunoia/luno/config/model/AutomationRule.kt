package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.action.model.AudioStream
import hunoia.luno.action.model.StoredAction
import hunoia.luno.action.model.VolumeDirection
import kotlinx.serialization.Serializable

@Serializable
@Keep
enum class BooleanMode { AND, OR }

@Serializable
@Keep
enum class FireMode { ENTER, EXIT, BOTH }

@Serializable
@Keep
enum class ScreenType { LOCK_SCREEN, LAUNCHER, LANDSCAPE, PORTRAIT, KEYBOARD_INPUT }

@Serializable
@Keep
enum class ScreenEventType { SCREEN_ON, SCREEN_OFF, USER_UNLOCK }

@Serializable
@Keep
enum class KeyboardEventType { SHOWN, HIDDEN }

/** 音乐播放变化方向（MUSIC_CHANGED 脉冲）。 */
@Serializable
@Keep
enum class PlaybackDirection { STARTED, STOPPED }

/** 铃声模式三态（静音条件）。 */
@Serializable
@Keep
enum class RingerMode { SILENT, VIBRATE, NORMAL }

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
    const val GESTURE_PERFORMED = "gesturePerformed"

    /** 键盘弹出/收起（脉冲；键盘状态仍由 SCREEN_STATE=KEYBOARD_INPUT 承担）。 */
    const val KEYBOARD_EVENT = "keyboardEvent"
    /** 音乐播放（状态）与播放变化（脉冲）。 */
    const val MUSIC_PLAYING = "musicPlaying"
    const val MUSIC_CHANGED = "musicChanged"
    /** 手电筒状态（状态）与开关变化（脉冲）。 */
    const val TORCH_ON = "torchOn"
    const val TORCH_CHANGED = "torchChanged"
    /** 勿扰模式（状态=中断过滤器非 ALL）与开关变化（脉冲）。 */
    const val DND_ON = "dndOn"
    const val DND_CHANGED = "dndChanged"

    /**
     * 手动触发：恒为假，引擎不订阅（无事件域、非状态叶），只供手势/runRule 调用。
     * 取反无意义（恒真会变成永远触发），编辑器不给取反开关。
     */
    const val MANUAL = "manual"

    const val GROUP = "group"
}

/** 步骤类型：扁平 kind 字符串（不用 sealed 多态，免得动全局 Json 的 SerializersModule）。 */
object RuleStepKind {
    const val ACTION = "action"
    const val DELAY = "delay"
    const val IF = "if"
    /** 规则局部变量：只在本次运行内有效，供后续参数模板 ${var.name} 使用。 */
    const val SET_VAR = "setVar"
}

/**
 * 单步失败策略（仅 ACTION 生效；delay/if/setVar 的配置错误仍按 STOP 中断）。
 * STOP = 现状；SKIP = 记失败后继续后续步骤；RETRY = 可重试失败按次数/间隔重试，耗尽后按 STOP。
 */
object StepFailure {
    const val STOP = "stop"
    const val SKIP = "skip"
    const val RETRY = "retry"

    /** 编辑器与执行器共用上限：防止手改数据无限重试/长间隔。 */
    const val MAX_RETRY_COUNT = 5
    const val MAX_RETRY_INTERVAL_MS = 30_000L
}

/** 同规则重入策略：默认单飞（与旧行为一致）。 */
@Serializable
@Keep
enum class RuleReentry {
    BLOCK_NEW,
    STOP_CURRENT_AND_RUN_NEW,
    ALLOW_PARALLEL,
}

/** 节流策略类型：同上扁平。BOTH=冷却与窗口同时生效（通用编辑器派生）。 */
object ThrottleKind {
    const val OFF = "off"
    const val COOLDOWN = "cooldown"
    const val WINDOW = "window"
    const val BOTH = "both"
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
    val networkType: NetworkType? = null,
    val plugged: Boolean? = null,
    // 期望值：null 表示不限，与求值器里的实际值做等值比较
    val expectedBluetoothAdapterOn: Boolean? = null,
    val expectedBluetoothAudioConnected: Boolean? = null,
    val airplaneMode: Boolean? = null,
    val group: WhenGroup? = null,
    // 脉冲条件字段（null=不限）
    val notifyPackages: List<String> = emptyList(),
    val notifyKeyword: String = "",
    val connectionDirection: ConnectionDirection? = null,
    val scheduledMinutes: List<Int> = emptyList(),
    val scheduledWeekdays: Set<Int> = emptySet(),
    // 条件字段（null/空=未配置）
    /** 键盘弹出/收起事件（脉冲）。 */
    val keyboardEvent: KeyboardEventType? = null,
    /** 时间范围限定星期（Calendar 常量；空=每天），与 scheduledWeekdays 同语义。 */
    val timeWeekdays: Set<Int> = emptySet(),
    /** 音乐播放状态期望值（null 视为未播放，与充电条件同构）。 */
    val musicPlaying: Boolean? = null,
    /** 音乐播放变化方向（null=不限）。 */
    val playbackDirection: PlaybackDirection? = null,
    /** 手电筒状态期望值（null 视为关闭）。 */
    val torchOn: Boolean? = null,
    /** 手电筒开关变化（null=不限）。 */
    val torchChangedTo: Boolean? = null,
    /** 勿扰状态期望值（null 视为未开启）。 */
    val dndOn: Boolean? = null,
    /** 勿扰开关变化（null=不限）。 */
    val dndChangedTo: Boolean? = null,
    /** 铃声模式（null=不限）。 */
    val ringerMode: RingerMode? = null,
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
    val steps: List<RuleStep> = emptyList(),
) {
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
 * 条件路径：顶层下标 / 逐级进组，如 [1]、[1, 0]；编辑器 sheet 定位用，非法返回 null/原样。
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
 * 单个执行步骤。扁平结构（kind 区分），递归只发生在 IF 的 then/else。
 * 守卫上限见 SequenceExecutor（steps 20 / IF 深 5 / 单 delay 60s / 总 delay 5min）。
 */
@Serializable
@Keep
data class RuleStep(
    val kind: String = RuleStepKind.ACTION,
    val action: StoredAction? = null,
    val delayMs: Long = 0L,
    val condition: WhenGroup = WhenGroup(),
    val thenSteps: List<RuleStep> = emptyList(),
    val elseSteps: List<RuleStep> = emptyList(),
    /** SET_VAR：变量名（不含 var. 前缀）。 */
    val varName: String = "",
    /** SET_VAR：值模板，支持 ${...} 与表达式。 */
    val varTemplate: String = "",
    /** ACTION 失败策略：见 [StepFailure]；旧数据缺键默认 STOP。 */
    val onFailure: String = StepFailure.STOP,
    /** RETRY：额外重试次数（0 = 不重试）。 */
    val retryCount: Int = 0,
    /** RETRY：两次尝试之间的等待（ms）。 */
    val retryIntervalMs: Long = 1_000L,
)

/**
 * 节流策略。空即不限：maxCount/windowMs 默认 0（未填）；kind==OFF 是总闸，
 * 引擎只认它关闭，其余一律值驱动（冷却 cooldownMs>0，窗口 count>=1 且 window>=1s）。
 */
@Serializable
@Keep
data class ThrottlePolicy(
    val kind: String = ThrottleKind.OFF,
    val cooldownMs: Long = 0L,
    val maxCount: Int = 0,
    val windowMs: Long = 0L,
)

/** 三档优先级的存储数值（HIGH 100 / MEDIUM 50 / LOW 0）。 */
object RulePriority {
    const val HIGH = 100
    const val MEDIUM = 50
    const val LOW = 0
}

@Serializable
@Keep
data class AutomationRule(
    val id: String,
    val enabled: Boolean = true,
    val name: String = "",
    /** 规则图标（IconPalette key，可为自定义文字；null=默认图标，老数据缺键解码无碍）。 */
    val iconKey: String? = null,
    val condition: WhenGroup,
    /** 约束条件：触发边沿命中后必须同时满足才进入仲裁；空 = 不限制。 */
    val constraints: WhenGroup = WhenGroup(),
    val effect: RuleEffect,
    val throttle: ThrottlePolicy = ThrottlePolicy(),
    val fireOn: FireMode = FireMode.ENTER,
    /** 优先级三档（HIGH/MEDIUM/LOW）；同档按用户顺序，创建时间只做兜底。 */
    val priority: Int = RulePriority.MEDIUM,
    /** 创建时间戳（同优先级 tie-break；存量 0 保持原列表顺序）。 */
    val createdAtMs: Long = 0L,
    /** 单次运行超时（ms；0 = 不限）。仅约束本条规则的一次触发实例。 */
    val maxRunMs: Long = 0L,
    /** 同规则重入策略；旧数据缺键保持单飞。 */
    val reentry: RuleReentry = RuleReentry.BLOCK_NEW,
) {
    companion object {
        val Defaults: List<AutomationRule> = emptyList()
    }
}
