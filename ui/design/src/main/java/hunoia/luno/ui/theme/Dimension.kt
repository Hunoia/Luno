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
/**
 * 紧凑命中区下限：密排自绘命中区（月历格、时间表盘刻度、多列网格）与卡片内纯图标工具条共用。
 * 使用前提见 ui-guidelines §1.6（相邻命中区不重叠、边界可辨、存在替代入口）。
 */
val SubMinInteractiveSize = 36.dp
// 卡片内纯图标工具条的紧凑尺寸：仅在内容空间受挤压时使用（见 ui-guidelines §4.8，36–48dp 档）
val CompactToolbarHeight = SubMinInteractiveSize
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

// 主页双球停靠栏（导航球 + 快捷球）的几何 token 见 DockDimension.kt。

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
// 所以这里的间距 token 同时是图标尺寸算式的输入，改动要连带看 QuickLaunchBody.kt 的 iconSizePx。
val PanelPaddingHorizontal = 10.dp
val PanelPaddingVertical = 10.dp

// 候选项是决策区，列宽有意固定而不跟网格自适应：面板拉宽时保持大图标。
val CandidateColumnWidth = 64.dp
val CandidateRowSpacing = 2.dp

// 单行候选项的高度上下界。QuickLaunchBody 用下界兜底面板内容高度，
// 见 requiredContentHeight。
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

// 按压缩放反馈（全站统一，替代 ripple；见 ui-guidelines §5.3）
/** 大目标（行 / 卡 / 键）的按压缩放幅度。 */
val PressScaleLarge = 0.96f
/** 紧凑目标（图标按钮 / 圆点 / chip）的按压缩放幅度。 */
val PressScaleCompact = 0.90f
/** 紧凑目标的判据：最小边不超过它的可点区域按 compact 档缩。 */
val PressScaleCompactThresholdDp = MinInteractiveSize
/** 按压缩放防抖：这段时间内松手不缩（列表快速滑动不闪缩）。 */
const val PressFeedbackDebounceMillis = 80L

// 面板内上拖手势阈值（网格顶部展开键盘、页面底部返回应用页共用）
val DragThresholdPx = 32f

// 非档位类动效 / 编排延迟（不参与应用档位，见 Motion.kt）
/** 内容级反馈（行底色、选中态透明度等）的短过渡。 */
const val MotionSelection = 150
/** 页面内内容互换的进场。 */
const val MotionContentIn = 200
/** 页面内内容互换的退场。 */
const val MotionContentOut = 150
/**
 * 进场等键盘真正出现的兜底上限（ms）：只有等到它才撑掉窗口的 `SOFT_INPUT_STATE_ALWAYS_VISIBLE`，
 * 状态位不能无限留着（留着会在窗口再次获焦点时自己弹键盘）。
 */
const val ImeBootstrapTimeoutMs = 600L
/** 弹层遮罩不透明度（`scrim` role）：遮罩自己画 + 跟退场动画一起淡出，不用窗口 dim。 */
const val DialogScrimAlpha = 0.22f
const val MotionNavTransition = 400
/** 指针光标跟随触点的平滑（属于跟手，不参与档位）。 */
const val MotionPointerFollow = 45
/** 指针光标点击涟漪（点击反馈，不参与档位）。 */
const val MotionRipple = 300

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
const val MotionSubtle = 180
const val MotionStatusIn = 220
const val MotionStatusOut = 140
const val MotionCellIn = 120
const val MotionCellOut = 100
/** 面板出现 / 收口的位移距离（dp）：面板走位移而不是整层淡入淡出（ui-guidelines §3「面板」）。 */
val PanelAppearSlide = 16.dp
/**
 * 面板进退场的缩放起止档：进场 0.25→1，退场 1→0.25；缩放锚点落在触发点，缩放为主、位移为辅
 * （ui-guidelines §5.1）。网格面板 / 快捷启动 / 动作面板共用 `Modifier.panelAppear`，只此一份。
 */
const val PanelAppearScale = 0.25f
/**
 * 面板进退场 alpha 的收口尾段比例：进度其余部分保持不透明，alpha 只在尾段收掉。
 * 退场以缩放 / 位移为主体，alpha 仅用来避免移窗时的硬切（ui-guidelines §5.1）：
 * 0.3 = 前 70% 进度保持不透明地缩回（此时代码的缩放整形已缩到 ~63% 尺寸），
 * 末 30%（标准档 60ms）边缩边淡。取 0.15 时只剩 30ms（~2 帧），真机上看起来就是硬切。
 */
const val PanelFadeTailFraction = 0.3f
/** 双击确认窗口（ms）：面板内置习惯打卡 / 待办完成的倒计时时长，不随系统动画缩放改变。 */
const val MotionConfirmCountdown = 1200

// 编辑页底部保存 FAB 的边长（大字体下仍可点，命中区见 MinInteractiveSize）。
val FabSize = 64.dp

// 习惯日历点阵：编辑页近 30 天日历与网格面板部件共用语义（命中区 48dp 见 §1.6）。
val HabitDayCellMinHeight = 48.dp

// 待办提醒展开后是三列滚轮（日期 / 时 / 分），行高随字体推出、宽度按列权重分，
// 不再有固定高度的选择区 token：高度约束统一由弹窗卡片的 heightIn(max) 提供。
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
