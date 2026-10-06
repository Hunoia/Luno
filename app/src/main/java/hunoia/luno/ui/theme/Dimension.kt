package hunoia.luno.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// Component sizes
val MarkColorSize = 20.dp
val MinItemHeightNoSecondary = 50.dp
val MinInteractiveSize = 48.dp
val SubMinInteractiveSize = 36.dp
val RowIconSize = 24.dp
val BadgeIconSize = 16.dp
val CloseIconSize = 18.dp
val TopBarPaddingExtra = 8.dp
val TopBarTitlePadding = 12.dp
val TrailingPadding = 8.dp

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
val ShapeExtraLarge = 28.dp

// Semantic shape tokens

// Shape instances
val ExtraSmallShape = RoundedCornerShape(ShapeExtraSmall)
val SmallShape = RoundedCornerShape(ShapeSmall)
val MediumShape = RoundedCornerShape(ShapeMedium)
val LargeShape = RoundedCornerShape(ShapeLarge)
val ExtraLargeShape = RoundedCornerShape(ShapeExtraLarge)
val SheetTopShape = RoundedCornerShape(topStart = ShapeExtraLarge, topEnd = ShapeExtraLarge)

// Bottom bar
val BottomBarPadding = 12.dp
val BottomBarHeight = 56.dp
val BottomBarCapsuleRadius = ShapeExtraLarge
val BottomBarIconSize = 30.dp
// 主页底栏 Tab 格宽（旧自研底栏 tab 最小宽原数，指示 pill 与其同宽）
val BottomBarTabWidth = 76.dp

// Segmented list tokens
val ContainerRadius = ShapeLarge
val ConnectionRadius = 5.dp
val SegmentedGap = 1.dp

// Row icon badge
val RowIconBadgeSize = 32.dp
val RowIconBadgeIconSize = 20.dp

// Quick launcher panel
// 面板底色只用 colorScheme.surfaceContainerHigh 一层，图标与按键都不再自带底色方块，
// 所以这里的间距 token 同时是图标尺寸算式的输入，改动必须连带看 Content.kt 的 iconSizePx。
val PanelPaddingHorizontal = 12.dp
val PanelPaddingVertical = 10.dp

// 候选项是决策区，列宽有意固定而不跟网格一起自适应：面板拉宽时它保持大图标。
val CandidateColumnWidth = 64.dp
val CandidateRowSpacing = 2.dp

// 单行候选项的高度上下界。AdjustPanel 用它们界定面板高度滑块的行程，
// Content.kt 用下界兜底 —— 两边必须同源，见 requiredContentHeight。
val CandidateMinHeight = 40.dp
val CandidateMaxHeight = 96.dp

// 网格 cell 与 AppItem/PageMatchIcon 的内边距
val GridCellSpacing = 4.dp
val AppItemPadding = 4.dp

// 键盘：行间距与键间距共用一个值
val KeyboardGap = 6.dp
val KeyboardRowCount = 3

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

// Gesture / interaction

// Component sizes
val MiniWindowDefaultHeight = 267.dp

val IconEmptyAlpha = 0.4f

// Feedback illustrations
val EmptyStateIconSize = 64.dp
val EmptyStatePaddingVertical = 32.dp
val FeedbackPaddingLarge = 48.dp
val ToastMaxWidth = 300.dp

// Wide content gutter（toast / 空态 / 选择器 sheet 的横向留白）
val LargeGutter = 24.dp

// Settings lists
val SettingsListMaxHeight = 184.dp

// Elevation
val ElevationSmall = 3.dp

// Hairline divider（1.dp 在此处是"线宽"语义，不是间距别名）
val DividerThickness = 1.dp

// Search field inset
val SearchBarPadding = 12.dp

// Color picker canvas geometry
val CanvasThumbRadius = 10.dp
val CanvasStrokeWidth = 2.dp
val HueBarHeight = 24.dp
val HueBarTouchHeight = 32.dp

// Trigger-distance canvas geometry
val DistanceTrackHeight = 8.dp
val DistanceStrokeWidth = 4.dp

// Haze 背后模糊半径见 Backdrop.kt 的 HazeBlurRadius（旧 GlassBlurRadius 同名兼容保留）。

// Timeline（条件树 / 步骤流共用的时间线语言）
val TimelineRailX = 13.dp
val TimelineRailWidth = 2.dp
val TimelineBadgeSize = 26.dp
