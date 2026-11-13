package hunoia.luno.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 主页三球停靠栏几何（ui-guidelines §4.2）。
 *
 * 单独成文件而不是并进 `Dimension.kt`：这一组是**纯尺寸**，被 [hunoia.luno.ui.component.HomeDockLayout]
 * 的纯函数直接引用；`Dimension.kt` 的类初始化会连带加载形状库，而形状库用 Java 21 编译、
 * 单测 JVM 是 17（同 ShapeRoleTest 的说明）。混在一起会让停靠栏几何无法在宿主 JVM 上测试。
 */
val DockBallSize = 60.dp
val DockCapsuleHeight = 60.dp

/** 胶囊内容高：外高 60 减上下各 `TileInnerPadding` 4。字面量保持本文件不依赖 `Dimension.kt`。 */
val DockTabHeight = 52.dp

/** 离屏幕左右边的距离（另加横向安全区 inset）。 */
val DockGutter = 16.dp

/** 三颗球 / 胶囊之间的间距。 */
val DockGap = 6.dp

/** 宽屏整组限宽：平板 / 横屏不把三颗球拉到屏幕两端。 */
val DockMaxWidth = 600.dp

/** 窄屏压缩 tab 宽度的下限；三球同排时 320dp 屏约 49dp，316dp 以下压到 48dp。 */
val DockMinTabWidth = 48.dp
