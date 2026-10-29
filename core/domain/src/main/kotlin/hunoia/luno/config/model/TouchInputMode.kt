package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 触钮原始触摸的来源策略。
 *
 * 触摸来源是 Trigger 侧的选择，不影响动作执行：三条路径最终都收敛为同一份
 * `GestureResolvedActions` → `ActionRequest` → `Dispatcher`。
 */
enum class TouchInputMode {
    /** 只用无障碍按钮窗。行为与接入 Shizuku 之前完全一致。 */
    ONLY_ACCESSIBILITY,

    /** 只用内核触摸流 + 拦截窗（无障碍可关闭）。拦截区之外的触摸照常穿透到下层。 */
    ONLY_SHIZUKU,

    /**
     * 无障碍在线走按钮窗；无障碍不在线且 Shizuku 就绪时自动切到内核触摸流。
     * 默认值：没装 Shizuku / 没授权时退化成 [ONLY_ACCESSIBILITY]，与旧行为一致。
     */
    AUTO,
}

/**
 * 单个触钮的触摸来源配置。
 *
 * 这是**手势识别的实现方式**，不是独立功能：两种来源产出同一份触摸帧契约，
 * 共用同一份识别状态与同一套动作分发，差别只在“这一块像素的触摸从哪来”。
 */
@Serializable
@Keep
data class TouchInputConfig(
    val mode: TouchInputMode = TouchInputMode.AUTO,
    /**
     * 拦截区宽度（dp）。`0` 表示整块触发区域（默认）：Shizuku 场景下这一块本来就归它，
     * 不需要为了穿透把条带做窄。调小则只吃贴边一条，其余触摸照常穿透到下层。
     */
    val interceptWidthDp: Int = 0,
)
