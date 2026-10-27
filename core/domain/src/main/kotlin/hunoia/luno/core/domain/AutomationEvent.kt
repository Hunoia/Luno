package hunoia.luno.core.domain

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/** 自动化跳过原因码（历史记录/UI 映射共用）。 */
enum class SkipReason(val code: String) {
    NO_STEPS("no_steps"),
    THROTTLED_COOLDOWN("throttled_cooldown"),
    THROTTLED_WINDOW("throttled_window"),
    OUTPRIORITIZED("outprioritized"),
    CONSTRAINT_UNMET("constraint_unmet"),
    RULE_BUSY("rule_busy"),
    PERMISSION_MISSING("permission_missing"),
}

/** 执行结果原因码（UI 映射到 strings.xml，Check 镜像断言用同一套）。 */
object ExecutionReason {
    const val OK = "ok"
    const val TIMEOUT = "timeout"
    const val INTERRUPTED_PREFIX = "interrupted:"
}

@Serializable
@Keep
data class StepRecord(
    val index: String,
    val kind: String,
    val ok: Boolean,
    val message: String = "",
    /** 失败但按 SKIP 策略继续执行：用于把「跳过」与「整条中断」区分开。 */
    val skipped: Boolean = false,
    /** 步骤级禁用：未执行，但不算失败。 */
    val disabled: Boolean = false,
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
    /** fired=false 时为跳过原因；true 时为 ok / timeout / interrupted:<首个未跳过失败步骤>。 */
    val reason: String = "",
    /** 通知原文不进历史：condition 侧匹配完即丢，这里只记命中包名+关键字（见 doc §6）。 */
    val steps: List<StepRecord> = emptyList(),
)
