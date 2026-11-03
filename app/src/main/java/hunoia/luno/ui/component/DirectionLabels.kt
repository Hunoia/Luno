package hunoia.luno.ui.component

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.config.model.sectorCenterDegrees
import kotlin.math.roundToInt

/**
 * 方向展示：扇区只提供序号与中心角，名称/图标/顺序都在这里派生。
 *
 * 角度约定与识别一致：0° = 正右、逆时针为正；图标旋转顺时针为正（View rotationZ）。
 */
object DirectionLabels {

    const val CompassStepDegrees = 45f

    /** 就近的八向罗盘名（上/右上/右/右下/下/左下/左/左上）；单方向时是「任意方向」。 */
    @StringRes
    fun nameRes(centerDegrees: Float, count: Int = 0): Int {
        if (count == 1) return R.string.direction_any
        val normalized = ((centerDegrees % 360f) + 360f) % 360f
        return when ((normalized / CompassStepDegrees).roundToInt() % 8) {
            0 -> R.string.right
            1 -> R.string.top_right
            2 -> R.string.top
            3 -> R.string.top_left
            4 -> R.string.left
            5 -> R.string.bottom_left
            6 -> R.string.bottom
            else -> R.string.bottom_right
        }
    }

    /** 图标旋转角（顺时针，0 = 箭头朝右）。 */
    fun iconRotation(centerDegrees: Float, count: Int = 0): Float =
        if (count == 1) 0f else -centerDegrees

    /** 角度文案：取整到度，360 归一成 0。 */
    fun angleText(centerDegrees: Float): String {
        val degrees = ((centerDegrees.roundToInt() % 360) + 360) % 360
        return "$degrees°"
    }

    /** 槽的展示顺序：从正上方开始顺时针。 */
    fun clockwiseSlots(boundaries: List<Float>): List<Int> =
        boundaries.indices.sortedBy { clockwiseRank(sectorCenterDegrees(boundaries, it)) }

    private fun clockwiseRank(centerDegrees: Float): Float =
        ((90f - centerDegrees) % 360f + 360f) % 360f
}

@Composable
fun directionLabel(
    centerDegrees: Float,
    count: Int = 0,
    withAngle: Boolean = true,
): String {
    if (count == 1) return stringResource(R.string.direction_any)
    val name = stringResource(DirectionLabels.nameRes(centerDegrees))
    return if (withAngle) "$name · ${DirectionLabels.angleText(centerDegrees)}" else name
}
