package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.core.domain.AudioStream
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.core.domain.VolumeDirection
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
    const val NOTIFICATION_REMOVED = "notificationRemoved"
    const val NOTIFICATION_EXISTS = "notificationExists"
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

    /** 全局/局部变量比较：`var.<name> <op> <value>`。 */
    const val VAR_COMPARE = "varCompare"

    /** 应用安装/卸载/更新事件。 */
    const val APP_PACKAGE_CHANGED = "appPackageChanged"
    /** 每隔 N 分钟触发一次（ticker 维持 lastFiredAt，Doze 下会漂移）。 */
    const val INTERVAL = "interval"
    /** 收到系统分享/链接。 */
    const val SHARE_RECEIVED = "shareReceived"
    /** 剪贴板球/面板读到新内容（不承诺后台实时监听）。 */
    const val CLIPBOARD_CHANGED = "clipboardChanged"
    /** Wi‑Fi SSID 状态（空=任意 Wi‑Fi）。 */
    const val WIFI_SSID = "wifiSsid"

    const val GROUP = "group"
}

/** 应用包变化动作；null/空表示不限。 */
object PackageChangeAction {
    const val ADDED = "added"
    const val REMOVED = "removed"
    const val REPLACED = "replaced"
    const val CHANGED = "changed"

    val all: List<String> = listOf(ADDED, REMOVED, REPLACED, CHANGED)
}

/** 通知文本匹配方式：contains（默认）、glob、regex。 */
object NotificationMatchMode {
    const val CONTAINS = "contains"
    const val GLOB = "glob"
    const val REGEX = "regex"

    val all: List<String> = listOf(CONTAINS, GLOB, REGEX)
}

/** 变量比较运算符；执行层与条件编辑器共用。 */
object VariableCompareOp {
    const val EQUALS = "eq"
    const val NOT_EQUALS = "neq"
    const val GREATER = "gt"
    const val GREATER_OR_EQUALS = "gte"
    const val LESS = "lt"
    const val LESS_OR_EQUALS = "lte"
    const val CONTAINS = "contains"
    const val STARTS_WITH = "startsWith"
    const val ENDS_WITH = "endsWith"
    const val IS_EMPTY = "isEmpty"
    const val IS_NOT_EMPTY = "isNotEmpty"

    val all: List<String> = listOf(
        EQUALS, NOT_EQUALS, GREATER, GREATER_OR_EQUALS, LESS, LESS_OR_EQUALS,
        CONTAINS, STARTS_WITH, ENDS_WITH, IS_EMPTY, IS_NOT_EMPTY,
    )
}

/** 步骤类型：扁平 kind 字符串（不用 sealed 多态，免得动全局 Json 的 SerializersModule）。 */
object RuleStepKind {
    const val ACTION = "action"
    const val DELAY = "delay"
    const val IF = "if"
    /** 规则局部变量：只在本次运行内有效，供后续参数模板 ${var.name} 使用。 */
    const val SET_VAR = "setVar"

    /** 有界循环：按 [RuleStep.repeatCount] 重复执行 thenSteps（≤20）。 */
    const val REPEAT = "repeat"

    /** 等待直到条件满足：超时 ≤60s，按 [RuleStep.waitIntervalMs] 轮询。 */
    const val WAIT_UNTIL = "waitUntil"

    /** 结束子规则并把 [RuleStep.varTemplate] 作为返回值（仅 runRule 调用时可见）。 */
    const val RETURN = "return"
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
    /** 旧版「标题或正文」关键字；新规则分别使用 notifyTitleKeyword / notifyTextKeyword。 */
    val notifyKeyword: String = "",
    val notifyTitleKeyword: String = "",
    val notifyTextKeyword: String = "",
    /** 标题或正文命中任一排除词即过滤掉。 */
    val notifyExcludeKeyword: String = "",
    val notifyExcludePackages: List<String> = emptyList(),
    /** 见 [NotificationMatchMode]；旧规则默认 contains。 */
    val notifyMatchMode: String = NotificationMatchMode.CONTAINS,
    /** 常驻通知过滤；null=不限。 */
    val notifyOngoing: Boolean? = null,
    /** 可清除过滤；null=不限。 */
    val notifyClearable: Boolean? = null,
    /** 重要性下限（NotificationManager.IMPORTANCE_*）；null=不限。 */
    val notifyImportanceAtLeast: Int? = null,
    /** NOTIFICATION_EXISTS：true=存在（默认），false=不存在。 */
    val notifyExists: Boolean? = null,
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
    /** 变量比较：变量名（不含 var. 前缀）。 */
    val compareVarName: String = "",
    /** 变量比较运算符：见 [VariableCompareOp]。 */
    val compareOp: String = VariableCompareOp.EQUALS,
    /** 变量比较右侧字面量（不作为模板展开，避免条件里再套模板）。 */
    val compareValue: String = "",
    /** APP_PACKAGE_CHANGED：见 [PackageChangeAction]；null=不限。 */
    val packageChangeAction: String? = null,
    /** INTERVAL：大致间隔（分钟，1..1440）；null=未配置。 */
    val intervalMinutes: Int? = null,
    /** WIFI_SSID：空=任意 Wi‑Fi；否则 SSID 精确匹配（忽略大小写）。 */
    val wifiSsid: String = "",
    /** BLUETOOTH：已连接设备名/地址过滤；空=不限。 */
    val bluetoothDeviceName: String = "",
    val bluetoothDeviceAddress: String = "",
    /** CLIPBOARD_CHANGED：可选文本包含过滤；空=不限。 */
    val clipboardKeyword: String = "",
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
    /** SET_VAR：true 时同时写入全局变量存储（跨运行/跨规则持久化）。 */
    val varGlobal: Boolean = false,
    /** ACTION 失败策略：见 [StepFailure]；旧数据缺键默认 STOP。 */
    val onFailure: String = StepFailure.STOP,
    /** RETRY：额外重试次数（0 = 不重试）。 */
    val retryCount: Int = 0,
    /** RETRY：两次尝试之间的等待（ms）。 */
    val retryIntervalMs: Long = 1_000L,
    /** 步骤级启用开关；false 时执行器跳过并写一条 disabled 记录。 */
    val enabled: Boolean = true,
    /** REPEAT：重复次数（执行器再按上限夹取）。 */
    val repeatCount: Int = 1,
    /** WAIT_UNTIL：总超时（ms；执行器上限 60s）。 */
    val waitTimeoutMs: Long = 10_000L,
    /** WAIT_UNTIL：轮询间隔（ms；执行器夹取 100..5000）。 */
    val waitIntervalMs: Long = 1_000L,
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

/** 规则参数定义：runRule 调用方按名传值，子规则用 `${param.name}` 读取。 */
@Serializable
@Keep
data class RuleParam(
    val name: String,
    /** 默认值模板；调用方未传时使用。 */
    val defaultValue: String = "",
)

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
    /** 子规则参数定义；空 = 无参。 */
    val params: List<RuleParam> = emptyList(),
    /** 配置 schema 版本；读取时由 AutomationRuleNormalizer 升级。 */
    val schemaVersion: Int = 1,
) {
    /** 是否含 RETURN 步骤（UI 提示 + runRule 结果变量展示用）。 */
    fun hasReturnStep(): Boolean = effect.steps.any { it.hasReturnStep() }

    companion object {
        val Defaults: List<AutomationRule> = emptyList()
    }
}

private fun RuleStep.hasReturnStep(): Boolean =
    kind == RuleStepKind.RETURN ||
        thenSteps.any { it.hasReturnStep() } ||
        elseSteps.any { it.hasReturnStep() }
