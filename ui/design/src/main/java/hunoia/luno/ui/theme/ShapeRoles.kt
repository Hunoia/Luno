package hunoia.luno.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import sv.lib.squircleshape.SquircleShape

/**
 * 四角可不同档位的容器形状（ui-guidelines §2.1.4）。
 *
 * 分段列表的首/中/尾、可展开卡的头/体都用它：档位可以不同（`ContainerRadius` /
 * `ConnectionRadius` / 0），但**角色只有一个**——都是容器角、同一个平滑度。
 * 需要一半容器角一半胶囊角时，拆成两个形状 / 两层裁切，不要在这里混。
 *
 * 除了画路径，这个类型还是「条目在组里的位置」的载体（[index]）：大卡分割线风格
 * 靠它决定条目顶部要不要画线，而 Shape 等值比较在选中段切角、动画半径、页面自组合
 * 形状上都会失效（ui-guidelines §4.1）。公开类型仍是 [Shape]，形状库不越过主题层。
 *
 * `equals` / `hashCode` 必须实现：否则每帧组合都得到新实例，裁切层会被重建。
 * 两者只比几何，不比 [index]——位置是元数据，不改变路径。
 */
internal class ContainerShape(
    val topStart: Dp = 0.dp,
    val topEnd: Dp = topStart,
    val bottomStart: Dp = 0.dp,
    val bottomEnd: Dp = bottomStart,
    /** 组内下标；null = 调用方没有分组位置（页面自组合形状），只能按顶角推断。 */
    val index: Int? = null,
) : Shape {

    private val delegate = SquircleShape(
        topStart = topStart,
        topEnd = topEnd,
        bottomStart = bottomStart,
        bottomEnd = bottomEnd,
        smoothing = ContainerSmoothing,
    )

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = delegate.createOutline(size, layoutDirection, density)

    override fun equals(other: Any?): Boolean =
        other is ContainerShape &&
            other.topStart == topStart &&
            other.topEnd == topEnd &&
            other.bottomStart == bottomStart &&
            other.bottomEnd == bottomEnd

    override fun hashCode(): Int {
        var result = topStart.hashCode()
        result = 31 * result + topEnd.hashCode()
        result = 31 * result + bottomStart.hashCode()
        result = 31 * result + bottomEnd.hashCode()
        return result
    }

    override fun toString(): String =
        "ContainerShape(topStart=$topStart, topEnd=$topEnd, " +
            "bottomStart=$bottomStart, bottomEnd=$bottomEnd)"
}

/** 四角同档位的容器形状（等价于 `containerShape(r, r, r, r)`），供 `RoundedCornerShape(dp)` 的迁移使用。 */
fun containerShape(radius: Dp): Shape = containerShape(radius, radius, radius, radius)

fun containerShape(
    topStart: Dp = 0.dp,
    topEnd: Dp = topStart,
    bottomStart: Dp = 0.dp,
    bottomEnd: Dp = bottomStart,
): Shape = ContainerShape(topStart, topEnd, bottomStart, bottomEnd)

/**
 * 组内第 [index] 项的形状：几何与 [containerShape] 相同，额外带上位置，
 * 供 `LunoRowBase` / `LunoGroupItem` 在大卡分割线风格下决定分割线。
 */
internal fun groupItemShape(
    index: Int,
    topStart: Dp,
    topEnd: Dp,
    bottomStart: Dp,
    bottomEnd: Dp,
): Shape = ContainerShape(topStart, topEnd, bottomStart, bottomEnd, index)
