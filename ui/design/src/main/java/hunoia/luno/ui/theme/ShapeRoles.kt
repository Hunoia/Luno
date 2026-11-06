package hunoia.luno.ui.theme

import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import sv.lib.squircleshape.SquircleShape

/**
 * 四角可不同档位的容器形状（ui-guidelines §2.1.4）。
 *
 * 分段列表的首/中/尾、可展开卡的头/体都用它：档位可以不同（`ContainerRadius` /
 * `ConnectionRadius` / 0），但**角色只有一个**——都是容器角、同一个平滑度。
 * 需要一半容器角一半胶囊角时，拆成两个形状 / 两层裁切，不要在这里混。
 *
 * 形状库的类型不越过 `ui:design`：调用方拿到的只是 [Shape]。
 */
/** 四角同档位的容器形状（等价于 `containerShape(r, r, r, r)`），供 `RoundedCornerShape(dp)` 的迁移使用。 */
fun containerShape(radius: Dp): Shape = containerShape(radius, radius, radius, radius)

fun containerShape(
    topStart: Dp = 0.dp,
    topEnd: Dp = topStart,
    bottomStart: Dp = 0.dp,
    bottomEnd: Dp = bottomStart,
): Shape = SquircleShape(
    topStart = topStart,
    topEnd = topEnd,
    bottomStart = bottomStart,
    bottomEnd = bottomEnd,
    smoothing = ContainerSmoothing,
)
