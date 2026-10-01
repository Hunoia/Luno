package hunoia.luno.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// Component sizes
val HomeWideBreakpoint = 600.dp
val MarkColorSize = 20.dp
val MinItemHeightNoSecondary = 50.dp
val MinInteractiveSize = 48.dp
val SubMinInteractiveSize = 36.dp
val MinIconSize = 24.dp
val RowIconSize = MinIconSize
val BadgeIconSize = 16.dp
val DialogHexTextWidth = 120.dp
val LongPressHintStartPadding = 34.dp
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
val CardCorner = ShapeLarge
val KeyboardCorner = ShapeMedium
val DialogCorner = ShapeMedium
val SheetCorner = ShapeExtraLarge
val ChipCorner = ShapeMedium
val ButtonCorner = ShapeMedium
val SearchBarCorner = ShapeExtraLarge
val ToastCorner = ShapeMedium
val SliderCorner = ShapeExtraSmall

// Shape instances
val ExtraSmallShape = RoundedCornerShape(ShapeExtraSmall)
val SmallShape = RoundedCornerShape(ShapeSmall)
val MediumShape = RoundedCornerShape(ShapeMedium)
val LargeShape = RoundedCornerShape(ShapeLarge)
val ExtraLargeShape = RoundedCornerShape(ShapeExtraLarge)
val CardShape = RoundedCornerShape(CardCorner)
val DialogShape = RoundedCornerShape(DialogCorner)
val SheetTopShape = RoundedCornerShape(topStart = SheetCorner, topEnd = SheetCorner)

// Bottom bar
val BottomBarPadding = 12.dp
val BottomBarHeight = 56.dp
val BottomBarCapsuleRadius = SheetCorner
val BottomBarIconSize = 26.dp

// Segmented list tokens
val ContainerRadius = ShapeLarge
val ConnectionRadius = 5.dp
val SegmentedGap = 1.dp

// Row icon badge
val RowIconBadgeSize = 32.dp
val RowIconBadgeIconSize = 20.dp

// Animation durations (ms)
const val AnimRipple = 300L
const val AnimNormal = 150L
const val AnimMedium = 200L
const val AnimSlow = 250L
const val AnimPanelShift = 180L
const val AnimPanelResize = 250L
const val AnimOverlayFade = AnimMedium
const val AnimPostHideDelay = 250L
const val AnimFrameInterval = 16L
const val AnimTriggerDebounce = 500L
const val AnimNavTransition = 400

// Gesture / interaction
val HideButtonDelayRange: ClosedFloatingPointRange<Float> = 500f..5000f
val VolumeScrubSensitivityRange: ClosedFloatingPointRange<Float> = 8f..40f

// Component sizes
val MiniWindowDefaultHeight = 267.dp
val SliderTextMaxWidth = 999.dp
val SliderTrackHeight = 30.dp

val IconEmptyAlpha = 0.4f
