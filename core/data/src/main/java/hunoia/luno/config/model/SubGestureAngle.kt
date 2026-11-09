package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/** 子手势的方向角度布局；语义与 [GestureButtonAngle] 相同（扇区数 1..8）。 */
@Serializable
@Keep
data class SubGestureAngle(
    override val boundaries: List<Float> = DirectionLayoutDefaults.DefaultBoundaries,
) : DirectionLayout {
    init {
        validateDirectionBoundaries(boundaries)
    }

    /**
     * 改方向数：以槽 0 的**中心角**为旋转锚点等分圆周，原有方向尽量留在原位；
     * 4→8 时扇区中心正好落在八向罗盘上（上/右上/…）。数量不变时返回自身。
     */
    fun withCount(count: Int): SubGestureAngle {
        val resized = boundariesWithCount(count)
        return if (resized === boundaries) this else SubGestureAngle(resized)
    }
}

fun SubGestureAngle.copyNewNoGap(index: Int, newP: Float): SubGestureAngle =
    copy(boundaries = copyDirectionAngleBoundary(boundaries, index, newP))
