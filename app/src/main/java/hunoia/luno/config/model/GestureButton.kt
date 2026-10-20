package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.R
import hunoia.luno.action.model.StoredAction
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.core.AppContext
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.random.Random

private fun randomColor(): Int =
    (0xFF shl 24) or
        (Random.nextInt(256) shl 16) or
        (Random.nextInt(256) shl 8) or
        Random.nextInt(256)

@Serializable
@Keep
data class GestureButtonBounds(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0.06f,
    val height: Float = 1f,
)

object GestureButtonDefaults {
    const val ID_DEFAULT = "1"
    const val Enabled = true
    val Bounds = GestureButtonBounds(y = 0.50529f, height = 0.30311f)
    val SlideActions = DirectionActions(
        mapOf(
            GestureDirection.Right to StoredAction.of("accessibility.back"),
            GestureDirection.Up to StoredAction.of("accessibility.recents"),
            GestureDirection.Down to StoredAction.of("accessibility.notificationPanel"),
        )
    )
    val LongSlideActions = DirectionActions()
    val SlideHoldActions = DirectionActions(
        mapOf(GestureDirection.Right to StoredAction.of("internal.quickAppLauncher"))
    )
    val LongSlideHoldActions = DirectionActions(
        mapOf(GestureDirection.Right to StoredAction.of("internal.quickSearch"))
    )
    const val Color = 0
    const val MirrorHorizontal = true
    val SlideTriggerDistance = DensityProvider.dp2px(30f)
    val LongSlideTriggerDistance = DensityProvider.dp2px(100f)
    const val LongPressTriggerDelayMs = 250L
    const val DoubleTapTriggerDelayMs = 300L
    const val SlideHoldTriggerDelayMs = 120L
    const val LongSlideHoldTriggerDelayMs = 120L
    const val SlideVibrate = false
    const val LongSlideVibrate = false
    const val TapVibrate = false
    const val LongPressVibrate = false
    const val SlideHoldVibrate = false
    const val LongSlideHoldVibrate = false
    val Defaults = listOf(GestureButton(id = ID_DEFAULT))
}

@Serializable
@Keep
data class GestureButton(
    val id: String,
    val name: String = "",
    val bounds: GestureButtonBounds = GestureButtonDefaults.Bounds,
    val enabled: Boolean = GestureButtonDefaults.Enabled,
    val slideActions: DirectionActions = GestureButtonDefaults.SlideActions,
    val slideHoldActions: DirectionActions = GestureButtonDefaults.SlideHoldActions,
    val longSlideActions: DirectionActions = GestureButtonDefaults.LongSlideActions,
    val longSlideHoldActions: DirectionActions = GestureButtonDefaults.LongSlideHoldActions,
    val angle: GestureButtonAngle = GestureButtonAngle(),
    val tapActions: StoredAction? = null,
    val doubleTapActions: StoredAction? = null,
    val longPressActions: StoredAction? = null,
    val color: Int = GestureButtonDefaults.Color,
    val mirrorHorizontal: Boolean = GestureButtonDefaults.MirrorHorizontal,
    val slideVibrate: Boolean = GestureButtonDefaults.SlideVibrate,
    val longSlideVibrate: Boolean = GestureButtonDefaults.LongSlideVibrate,
    val tapVibrate: Boolean = GestureButtonDefaults.TapVibrate,
    val longPressVibrate: Boolean = GestureButtonDefaults.LongPressVibrate,
    val slideHoldVibrate: Boolean = GestureButtonDefaults.SlideHoldVibrate,
    val longSlideHoldVibrate: Boolean = GestureButtonDefaults.LongSlideHoldVibrate,
    val slideTriggerDistance: Int = GestureButtonDefaults.SlideTriggerDistance,
    val longSlideTriggerDistance: Int = GestureButtonDefaults.LongSlideTriggerDistance,
    val longPressTriggerDelayMs: Long = GestureButtonDefaults.LongPressTriggerDelayMs,
    val doubleTapTriggerDelayMs: Long = GestureButtonDefaults.DoubleTapTriggerDelayMs,
    val slideHoldTriggerDelayMs: Long = GestureButtonDefaults.SlideHoldTriggerDelayMs,
    val longSlideHoldTriggerDelayMs: Long = GestureButtonDefaults.LongSlideHoldTriggerDelayMs,
) : Comparable<GestureButton> {

    /**
     * 远档阈值不得小于近档：否则短滑会落进 LongSlide 分支，而 SlideHold 因
     * `canTriggerSlide && !canTriggerLong` 恒不成立而永久失联（数据层兜底，
     * 与 SubGesture.effectiveLongSlideTriggerDistance 一致；UI 侧另有 gap 校验）。
     */
    val effectiveLongSlideTriggerDistance: Int
        get() = longSlideTriggerDistance.coerceAtLeast(slideTriggerDistance)

    companion object {
        const val ID_DEFAULT = GestureButtonDefaults.ID_DEFAULT
        val Defaults: List<GestureButton> get() = GestureButtonDefaults.Defaults

        fun create(name: String = ""): GestureButton {
            val colorInt = randomColor()
            return GestureButton(
                // 时间戳前缀保证按 id 排序仍近似创建顺序（UI 会 sortedBy { it.id }），
                // UUID 后缀避免同一毫秒内创建两个按钮时撞 id。
                id = "${System.currentTimeMillis()}-${UUID.randomUUID()}",
                name = name,
                color = colorInt,
            )
        }

    }

    val isDefault: Boolean = id == ID_DEFAULT

    override fun compareTo(other: GestureButton): Int = id.compareTo(other.id)
}

/** 按钮显示名：有名字用名字，否则取主题字符串（与 UI 侧 buttonTextCompose 同约定）。 */
internal fun GestureButton.resolveDisplayName(): String {
    if (name.isNotEmpty()) return name
    return AppContext.get().getString(R.string.gesture_button)
}
