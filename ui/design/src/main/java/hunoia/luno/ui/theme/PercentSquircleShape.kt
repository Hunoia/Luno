package hunoia.luno.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import sv.lib.squircleshape.SquircleShape

/**
 * 百分比半径的连续曲率形状：几何半径 = `minDimension × percent / 100`。
 *
 * 形状库的工厂只接受 dp 半径，而胶囊的高度随字体 / inset / 内容变化，
 * 所以换算只能放在这一层（ui-guidelines §2.1.4）。公开暴露的类型是 [Shape]。
 *
 * `equals` / `hashCode` 必须实现：否则每次组合都是新实例，裁切层会被重建。
 */
internal class PercentSquircleShape(
    private val percent: Float,
    private val smoothing: Int,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val radius = with(density) { (size.minDimension * percent / 100f).toDp() }
        return SquircleShape(radius, smoothing).createOutline(size, layoutDirection, density)
    }

    override fun equals(other: Any?): Boolean =
        other is PercentSquircleShape && other.percent == percent && other.smoothing == smoothing

    override fun hashCode(): Int = 31 * percent.hashCode() + smoothing

    override fun toString(): String =
        "PercentSquircleShape(percent=$percent, smoothing=$smoothing)"
}
