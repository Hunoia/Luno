package hunoia.luno.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val TopBarPaddingExtra = 8.dp
val RootPadding = 12.dp
val ContentPaddingHorizontal = RootPadding
val ContentPaddingVerticalWithSection = ContentPaddingHorizontal
val ContentPaddingVertical = ContentPaddingVerticalWithSection / 2
val ItemPadding = 16.dp
val IconTextPadding = 8.dp
val SectionTitlePadding = 8.dp
val SectionPadding = RootPadding * 2
val SectionPaddingNoTitle = RootPadding
val ScrollBottomPadding = 24.dp
val HomeWideBreakpoint = 600.dp
val DividerHeight = 24.dp
val MainSecondaryTextPadding = 6.dp
val EdgeMenuPadding = RootPadding
val MarkColorSize = 20.dp
val MinItemHeight = 70.dp
val MinItemHeightNoSecondary = 50.dp
val MinInteractiveSize = 48.dp
val SubMinInteractiveSize = 36.dp
val MinIconSize = 24.dp
val DialogTitlePadding = RootPadding * 2
val DialogHexTextWidth = 120.dp
val LongPressHintStartPadding = 34.dp
val CloseIconSize = 18.dp

// Gesture / interaction thresholds
val MiniWindowWidth = 200.dp
// Spacing / gap
val Spacing1 = 1.dp
val Spacing2 = 2.dp
val Spacing4 = 4.dp
val Spacing5 = 5.dp
val Spacing6 = 6.dp
val Spacing8 = 8.dp
val Spacing10 = 10.dp
val Spacing12 = 12.dp
val Spacing14 = 14.dp
val Spacing16 = 16.dp
val Spacing20 = 20.dp
val Spacing24 = 24.dp
val Spacing32 = 32.dp
val Spacing40 = 40.dp
val Spacing48 = 48.dp
val Spacing56 = 56.dp
val Spacing64 = 64.dp

// Shape primitives
val ShapeExtraSmall = 4.dp
val ShapeSmall = 8.dp
val ShapeMedium = Spacing12
val ShapeLarge = Spacing16
val ShapeExtraLarge = Spacing20

// Semantic shape tokens — override these to update component shapes globally
val CardCorner = ShapeExtraLarge
val KeyboardCorner = ShapeMedium
val DialogCorner = ShapeMedium
val SheetCorner = ShapeExtraLarge
val ChipCorner = ShapeSmall
val ButtonCorner = ShapeSmall
val SearchBarCorner = ShapeExtraLarge
val ToastCorner = ShapeMedium
val SliderCorner = ShapeExtraSmall

val CardShape = RoundedCornerShape(CardCorner)
val DialogShape = RoundedCornerShape(DialogCorner)
val SheetTopShape = RoundedCornerShape(topStart = SheetCorner, topEnd = SheetCorner)
val IconBoxShape = RoundedCornerShape(ShapeSmall)

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
