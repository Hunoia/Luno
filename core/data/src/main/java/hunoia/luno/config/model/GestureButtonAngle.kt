package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 触钮的方向角度布局：扇区数量 = [boundaries] 个数（1..8）。
 *
 * 改方向数时用 [withCount]（保留槽 0 起点、等分圆周）；逐边界拖动仍走
 * [copyDirectionAngleBoundary]，允许把某个扇区宽度拖成 0 来关闭这个方向。
 */
@Serializable
@Keep
data class GestureButtonAngle(
    override val boundaries: List<Float> = DirectionLayoutDefaults.DefaultBoundaries,
) : DirectionLayout {
    init {
        validateDirectionBoundaries(boundaries)
    }

    /**
     * 改方向数：以槽 0 的**中心角**为旋转锚点等分圆周，原有方向尽量留在原位；
     * 4→8 时扇区中心正好落在八向罗盘上（上/右上/…）。数量不变时返回自身。
     */
    fun withCount(count: Int): GestureButtonAngle {
        val resized = boundariesWithCount(count)
        return if (resized === boundaries) this else GestureButtonAngle(resized)
    }
}

fun GestureButtonAngle.copyNewNoGap(index: Int, newBoundary: Float): GestureButtonAngle =
    copy(boundaries = copyDirectionAngleBoundary(boundaries, index, newBoundary))
