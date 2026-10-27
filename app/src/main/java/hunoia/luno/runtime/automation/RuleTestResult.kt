package hunoia.luno.runtime.automation

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 「测试运行」结果：条件/约束按当前运行时快照求值，步骤真实执行一次。
 * 只走编辑页的测试入口，不占引擎仲裁/节流，也不写执行历史。
 */
@Serializable
@Keep
data class RuleTestResult(
    val conditionHit: Boolean,
    val constraintsHit: Boolean,
    /** 约束组为空（展示「不限」而不是「满足」）。 */
    val constraintsEmpty: Boolean = false,
    /** 是否真的执行了步骤（false = 仅检查条件）。 */
    val executed: Boolean = true,
    val steps: List<StepRecord> = emptyList(),
    /** ok / condition_only / interrupted:<步骤路径> / 其他异常文本。 */
    val reason: String = "",
) {
    companion object {
        const val CONDITION_ONLY = "condition_only"
    }
}
