package hunoia.luno.ui.theme

import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import hunoia.luno.ui.theme.CapsuleShape
import hunoia.luno.ui.theme.ContainerSmoothing
import hunoia.luno.ui.theme.LargeShape
import sv.lib.squircleshape.CornerSmoothing
import sv.lib.squircleshape.SquircleShape

// Component sizes
val MarkColorSize = 20.dp
val MinItemHeightNoSecondary = 50.dp
val MinInteractiveSize = 48.dp
val SubMinInteractiveSize = 36.dp
// 卡片内纯图标工具条的紧凑尺寸：仅在内容空间受挤压时使用（见 ui-guidelines §4.8）
val CompactToolbarHeight = 32.dp
val CompactControlIconSize = 20.dp
val RowIconSize = 24.dp
val BadgeIconSize = 16.dp
val TopBarPaddingExtra = 8.dp
val TopBarTitlePadding = 12.dp

// Layout spacing
val PageGutter = 16.dp
val ListSpacing = 16.dp
val ListItemVerticalPadding = 12.dp
val CardInnerSpacing = 8.dp
val SliderInnerPadding = 12.dp
val ContentBottom = 24.dp
val FloatingContentBottom = 120.dp
val SheetListMaxHeight = 420.dp

// Gesture / interaction thresholds
val MiniWindowWidth = 200.dp

// Shape primitives
val ShapeExtraSmall = 4.dp
val TileInnerPadding = ShapeExtraSmall
val ShapeSmall = 8.dp
val ShapeMedium = 12.dp
val ShapeLarge = 16.dp
val ShapeExtraLarge = 32.dp

// 连续曲率平滑度（ui-guidelines §2.1.3）：全仓只有两个值，页面不得覆盖。
// 容器 0.48（Medium）；胶囊 0.2（Small）——胶囊端部本来就是弧，档位一大就读作圆角矩形。
//
// 半径上限：容器必须满足 r ≤ h / (2(1+s))，s = 0.48 时 r ≤ h / 2.96。
// 50dp 行取 16dp（上限 16.9）、96dp 卡取 32dp（上限 32.4）；改档位必须重算（§2.1.2 第 3 条）。
val ContainerSmoothing = CornerSmoothing.Medium
val CapsuleSmoothing = CornerSmoothing.Small

// Shape instances（四角色见 §2.1.1）
// 小角也用同一几何家族，只换档位，不另开一套圆弧形状。
val SmallShape: Shape = SquircleShape(ShapeSmall, ContainerSmoothing)
val MediumShape: Shape = SquircleShape(ShapeMedium, ContainerSmoothing)
val LargeShape: Shape = SquircleShape(ShapeLarge, ContainerSmoothing)
val ExtraLargeShape: Shape = SquircleShape(ShapeExtraLarge, ContainerSmoothing)
val SheetTopShape: Shape = SquircleShape(
    topStart = ShapeExtraLarge,
    topEnd = ShapeExtraLarge,
    bottomStart = 0.dp,
    bottomEnd = 0.dp,
    smoothing = ContainerSmoothing,
)

// 胶囊：零直边百分比 50/(1+s)，随高度自适应，不写 dp（§2.1.2 第 2 条）。
// 底栏、指示 pill、chip、玻璃胶囊、固定高度单行输入都用它，不再按尺寸开 dp token。
val CapsuleCornerPercent = 50f / (1f + CapsuleSmoothing / 100f)
val CapsuleShape: Shape = PercentSquircleShape(CapsuleCornerPercent, CapsuleSmoothing)

// Bottom bar
val BottomBarPadding = 12.dp
val BottomBarHeight = 56.dp
val BottomBarIconSize = 30.dp
// 底栏与指示 pill 都用胶囊角色（§2.1.1），半径由 CapsuleShape 按各自高度算出。
// 主页底栏 Tab 格宽，指示 pill 同宽
val BottomBarTabWidth = 76.dp

// Segmented list tokens
val ContainerRadius = ShapeLarge
val ConnectionRadius = 5.dp
val SegmentedGap = 1.dp
// OCR 分词 chip 可见高度；命中区与之同高，行距即 chip 高，分词排版更紧凑。
val OcrChipMinHeight = 32.dp

// Row icon badge
val RowIconBadgeSize = 32.dp
val RowIconBadgeIconSize = 20.dp

// Quick launcher panel
// 面板底色只用 surfaceContainerHigh 一层，图标与按键都不自带底色方块，
// 所以这里的间距 token 同时是图标尺寸算式的输入，改动要连带看 QuickAppLauncherContent.kt 的 iconSizePx。
val PanelPaddingHorizontal = 12.dp
val PanelPaddingVertical = 10.dp

// 候选项是决策区，列宽有意固定而不跟网格自适应：面板拉宽时保持大图标。
val CandidateColumnWidth = 64.dp
val CandidateRowSpacing = 2.dp

// 单行候选项的高度上下界。AdjustPanel 用它界定高度滑块行程，
// QuickAppLauncherContent.kt 用下界兜底 —— 两边必须同源，见 requiredContentHeight。
val CandidateMinHeight = 40.dp
val CandidateMaxHeight = 96.dp

// 网格 cell 与 AppItem 的内边距
val GridCellSpacing = 4.dp
val AppItemPadding = 4.dp

// 键盘：行间距与键间距共用一个值
val KeyboardGap = 6.dp
val KeyboardRowCount = 3

// 动作库图标选择器的图标尺寸
val PageMatchIconSize = 28.dp
val EmptyStateHeight = 88.dp

// 底部药丸：网格底部展开键盘，设置页 / 密码页底部返回应用页，共用一个组件
val PillWidth = 44.dp
val PillHeight = 5.dp
val PillPaddingVertical = 6.dp
val PillAlpha = 0.35f

// 按压缩放反馈
val PressScaleApp = 0.92f
val PressScaleKey = 0.93f

// 面板内上拖手势阈值（网格顶部展开键盘、页面底部返回应用页共用）
val DragThresholdPx = 32f

// Animation durations (ms)
const val AnimNormal = 150L
const val AnimMedium = 200L
const val AnimPanelResize = 250L
const val AnimOverlayFade = AnimMedium
const val AnimPostHideDelay = 250L
const val AnimNavTransition = 400

val MiniWindowDefaultHeight = 267.dp

val IconEmptyAlpha = 0.4f

// Feedback illustrations
val EmptyStateIconSize = 64.dp
val EmptyStatePaddingVertical = 32.dp

// Wide content gutter（空态 / 选择器 sheet 的横向留白）
val LargeGutter = 24.dp

// Elevation
val ElevationSmall = 3.dp

// Color picker canvas geometry
val CanvasThumbRadius = 10.dp
/** 画布拖拽把手的热区半径（大于可视半径，手指好命中；角度盘与取色器共用）。 */
val CanvasHitRadius = 26.dp
val CanvasStrokeWidth = 2.dp
/** 透明度/明度滑块的触达高度（轨道可视高度更小，整行是热区）。 */
val HueBarTouchHeight = 32.dp

// 颜色选择器：色相 + 饱和度圆盘直径
val ColorWheelSize = 260.dp

// Trigger-distance canvas geometry
val DistanceTrackHeight = 16.dp
val DistanceStrokeWidth = 4.dp

// Haze 背后模糊半径见 Backdrop.kt 的 HazeBlurRadius。

// 语义动效 token（ui-guidelines.md §2.4）：页面禁止再写裸 tween(数字)。
const val MotionPageEnter = 200
const val MotionPageExit = 150
const val MotionSwapIn = 160
const val MotionSwapOut = 120
const val MotionPress = 120
const val MotionSettle = 240
const val MotionReturn = 250
const val MotionFadeOut = 150
const val MotionSubtle = 180
const val MotionOverlayEnter = 300
const val MotionOverlayExit = 200
const val MotionStatusIn = 220
const val MotionStatusOut = 140
const val MotionCellIn = 120
const val MotionCellOut = 100
const val MotionPanelIn = 170
const val MotionPanelOut = 140
/** 双击确认窗口（ms）：面板内置习惯打卡 / 待办完成的倒计时时长，不随系统动画缩放改变。 */
const val MotionConfirmCountdown = 1200

// 编辑页底部保存 FAB 的边长（大字体下仍可点，命中区见 MinInteractiveSize）。
val FabSize = 64.dp

// 习惯日历点阵：编辑页近 30 天日历与网格面板部件共用语义（命中区 48dp 见 §1.6）。
val HabitDayCellMinHeight = 48.dp
val HabitDayCellPaddingVertical = 3.dp
val HabitDayDotSize = 14.dp
val HabitDayDotStroke = 1.5.dp
val HabitDayCheckIconSize = 9.dp
val HabitDayTextDotGap = 2.dp
val HabitCalendarCellGap = 2.dp
val HabitCalendarHeaderGap = 6.dp
val HabitCalendarPaddingVertical = 12.dp
val HabitDayTextAlpha = 0.6f
val HabitWeekdayAlpha = 0.7f
