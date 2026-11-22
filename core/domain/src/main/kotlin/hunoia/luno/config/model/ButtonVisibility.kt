package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.core.domain.StoredAction
import kotlinx.serialization.Serializable

/** 显隐场景的界面维度。 */
@Serializable
@Keep
enum class VisibilityScreen {
    LOCK_SCREEN,
    LAUNCHER,
    LANDSCAPE,
    PORTRAIT,
    KEYBOARD_INPUT,
    /** 熄屏（亮屏不含）。状态来自既有的息屏/亮屏回调，不新增平台监听。 */
    SCREEN_OFF,
}

/** 场景作用对象的选取方式。 */
@Serializable
@Keep
enum class VisibilityTargetMode {
    ALL,
    ONLY,
    EXCEPT,
}

/**
 * 场景可作用的非触钮目标。触钮与识别区直接用各自的 `id`
 * （`GestureButton.id` / `GlobalGestureZone.id`）。
 */
object VisibilityTargets {
    const val FLOAT_BALL = "surface:float_ball"
    const val NOTIFICATION_CAPSULE = "surface:notification_capsule"
    /** 结果胶囊（分发规则的胶囊档结果显示面）。字符串保留旧值：它是对外可见的配置 id。 */
    const val RESULT_CAPSULE = "surface:clipboard_ball"
}

/**
 * 一条显隐场景。
 *
 * - [packageNames] 为空 = 任意应用；[screens] 为空 = 任意界面。
 * - 应用维度与界面维度是 AND；多条场景按列表顺序求值，最后命中的**带显隐效果**的一条生效。
 * - [visible] 三态：true/false = 进入场景把作用对象显示 / 隐藏（相对用户基线，不会把
 *   总开关或用户手动关闭的对象重新打开）；null = 本场景不改显隐（纯动作场景）。
 * - 触发：[enterActions] / [exitActions] 在场景进入 / 退出的边缘按序提交统一执行口，
 *   与显隐互不影响（边缘与冷却语义见 architecture §5.3）。
 */
@Serializable
@Keep
data class VisibilityProfile(
    val id: String,
    val enabled: Boolean = true,
    val name: String = "",
    val packageNames: Set<String> = emptySet(),
    val screens: Set<VisibilityScreen> = emptySet(),
    /** 三态显隐效果（见类型注释）；默认 true 保持既有数据语义（缺字段 → 显示）。 */
    val visible: Boolean? = true,
    val mode: VisibilityTargetMode = VisibilityTargetMode.ALL,
    /** 触钮 id 或 [VisibilityTargets] 中的 Surface id。 */
    val targetIds: Set<String> = emptySet(),
    /** 进入场景边缘触发的动作（按序提交统一执行口）。 */
    val enterActions: List<StoredAction> = emptyList(),
    /** 退出场景边缘触发的动作（按序提交统一执行口）。 */
    val exitActions: List<StoredAction> = emptyList(),
    /** 触发冷却秒数（0 = 不冷却）：进入/退出共用一个窗口，窗口内不重复触发。 */
    val cooldownSeconds: Int = 0,
)

@Serializable
@Keep
data class ButtonVisibilitySettings(
    val profiles: List<VisibilityProfile> = emptyList(),
)
