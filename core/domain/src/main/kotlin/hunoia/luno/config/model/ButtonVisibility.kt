package hunoia.luno.config.model

import androidx.annotation.Keep
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
    const val CLIPBOARD_BALL = "surface:clipboard_ball"
}

/**
 * 一条显隐场景。
 *
 * - [packageNames] 为空 = 任意应用；[screens] 为空 = 任意界面。
 * - 应用维度与界面维度是 AND；多条场景按列表顺序求值，最后命中的一条生效。
 * - [visible] 只相对用户基线隐藏 / 恢复，不会把总开关或用户手动关闭的对象重新打开。
 */
@Serializable
@Keep
data class VisibilityProfile(
    val id: String,
    val enabled: Boolean = true,
    val name: String = "",
    val packageNames: Set<String> = emptySet(),
    val screens: Set<VisibilityScreen> = emptySet(),
    val visible: Boolean = true,
    val mode: VisibilityTargetMode = VisibilityTargetMode.ALL,
    /** 触钮 id 或 [VisibilityTargets] 中的 Surface id。 */
    val targetIds: Set<String> = emptySet(),
)

@Serializable
@Keep
data class ButtonVisibilitySettings(
    val profiles: List<VisibilityProfile> = emptyList(),
)
