package hunoia.luno.runtime.automation

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/** 执行历史：全局环形 100 条，查询侧按规则过滤取后 20 条。未命中（hit=false）不记，只记边沿后的事。 */
object ExecutionLog {
    const val MAX_EVENTS = 100
    const val MAX_PER_RULE_QUERY = 20
}

/** 跳过原因枚举（线值保持与旧字符串一致，历史兼容）。 */
enum class SkipReason(val code: String) {
    NO_STEPS("no_steps"),
    THROTTLED_COOLDOWN("throttled_cooldown"),
    THROTTLED_WINDOW("throttled_window"),
    OUTPRIORITIZED("outprioritized"),
    RULE_BUSY("rule_busy"),
}

/** 执行结果原因码（UI 映射到 strings.xml，Check 镜像断言用同一套）。 */
object ExecutionReason {
    const val OK = "ok"
    const val INTERRUPTED_PREFIX = "interrupted:"
}

@Serializable
@Keep
data class StepRecord(
    val index: String,
    val kind: String,
    val ok: Boolean,
    val message: String = "",
)

@Serializable
@Keep
data class AutomationEvent(
    val ruleId: String,
    val ruleName: String = "",
    val timeMs: Long = 0L,
    /** 墙钟落盘时间（展示用）。timeMs 是开机时长，只给节流/边沿用；老事件缺该键按 0 解码，展示层丢弃。 */
    val wallTimeMs: Long = 0L,
    val fired: Boolean,
    /** fired=false 时为跳过原因；true 时为 ok / interrupted:<首个失败步骤>。 */
    val reason: String = "",
    /** 通知原文不进历史：condition 侧匹配完即丢，这里只记命中包名+关键字（见 doc §6）。 */
    val steps: List<StepRecord> = emptyList(),
)
