package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.action.model.ButtonTarget
import kotlinx.serialization.Serializable

/** 触钮可见性场景的界面维度。 */
@Serializable
@Keep
enum class VisibilityScreen {
    LOCK_SCREEN,
    LAUNCHER,
    LANDSCAPE,
    PORTRAIT,
    KEYBOARD_INPUT,
}

/**
 * 一条触钮可见性场景。
 *
 * - [packageNames] 为空 = 任意应用；[screens] 为空 = 任意界面。
 * - 应用维度与界面维度是 AND；多条场景按列表顺序求值，最后命中的一条生效。
 * - [visible] 只相对用户基线（`GestureButton.enabled`）隐藏 / 恢复，不会把用户手动关掉的钮重新打开。
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
    val target: ButtonTarget = ButtonTarget.ALL,
    val buttonIds: List<String> = emptyList(),
)

@Serializable
@Keep
data class ButtonVisibilitySettings(
    val profiles: List<VisibilityProfile> = emptyList(),
)
