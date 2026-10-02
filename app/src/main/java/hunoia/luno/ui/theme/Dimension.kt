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
val CardShape = RoundedCornerShape(ShapeLarge)

// Bottom bar
val BottomBarPadding = 12.dp
val BottomBarHeight = 56.dp
val BottomBarCapsuleRadius = ShapeExtraLarge
val BottomBarIconSize = 26.dp

// Segmented list tokens
val ContainerRadius = ShapeLarge
val ConnectionRadius = 5.dp
val SegmentedGap = 1.dp

// Row icon badge
val RowIconBadgeSize = 32.dp
val RowIconBadgeIconSize = 20.dp

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
val SliderTextMaxWidth = 999.dp

val IconEmptyAlpha = 0.4f
