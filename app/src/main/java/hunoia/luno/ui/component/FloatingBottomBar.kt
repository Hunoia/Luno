// Adapted from InstallerX Revived (app/src/main/java/com/rosan/installer/ui/library/FloatingBottomBar.kt)
// and from compose-miuix-ui's IosLiquidGlassNavigationBar — Apache 2.0.
// Trimmed: InteractiveHighlight is dropped (the null-guarded branches are kept inert) and
// labelContent is removed because this bar is icon-only. LiquidGlass / Blur / None all retained.
package hunoia.luno.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor as M3LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import hunoia.luno.ui.animation.DampedDragAnimation
import hunoia.luno.ui.library.liquid.InnerShadow
import hunoia.luno.ui.library.liquid.innerShadow
import hunoia.luno.ui.library.liquid.lens
import hunoia.luno.ui.library.liquid.rememberCombinedBackdrop
import hunoia.luno.ui.library.liquid.vibrancy
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.sensor.rememberDeviceTilt

private val LocalFloatingBottomBarContentColor = staticCompositionLocalOf { Color.Unspecified }
private val LocalFloatingBottomBarTabScale = staticCompositionLocalOf { { 1f } }

enum class FloatingBottomBarMode {
    LiquidGlass,
    LiquidGlassBlur,
    Blur,
    None,
}

private val iosIndicatorSpecular: Highlight = Highlight(
    width = 1.dp,
    alpha = 1f,
    style = BloomStroke(
        color = Color.White.copy(alpha = 0.12f),
        innerBlurRadius = 2.0.dp,
        primaryLight = LightSource(
            position = LightPosition(0.5f, -0.3f, -0.05f),
            color = Color.White,
            intensity = 1f,
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.5f, 0.8f, -0.5f),
            color = Color.White,
            intensity = 0.4f,
        ),
        dualPeak = true,
    ),
)

// Mirrors miuix-blur HighlightStyle's LIGHT_REF — keep in sync.
private const val LIGHT_REF_X = 0.5f
private const val LIGHT_REF_Y = 0.7f
private const val GRAVITY_DIR_THRESHOLD_SQ = 0.01f

/** Tracks gravity for a `dualPeak` highlight's primary light, with an extra UV-clockwise offset on top. */
@Composable
private fun rememberGravityRotatedHighlight(base: Highlight, extraDegrees: Float = 0f): Highlight {
    val baseStyle = base.style as BloomStroke
    val tilt by rememberDeviceTilt()
    val rotatedPrimary = remember(tilt, baseStyle.primaryLight, extraDegrees) {
        val basePrimary = baseStyle.primaryLight
        val gx = tilt.gravityX
        val gy = tilt.gravityY
        val gMagSq = gx * gx + gy * gy
        val (lx0, ly0) = if (gMagSq > GRAVITY_DIR_THRESHOLD_SQ) {
            val invMag = 1f / sqrt(gMagSq)
            (gx * invMag) to (gy * invMag)
        } else {
            0f to -1f
        }
        val rad = extraDegrees * PI / 180.0
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val lx = c * lx0 - s * ly0
        val ly = s * lx0 + c * ly0
        basePrimary.copy(
            position = LightPosition(
                x = LIGHT_REF_X + lx,
                y = LIGHT_REF_Y + ly,
                z = basePrimary.position.z,
            ),
        )
    }
    return remember(base, rotatedPrimary) {
        base.copy(style = baseStyle.copy(primaryLight = rotatedPrimary))
    }
}

// State class holding all colors for the bottom bar
@Immutable
class FloatingBottomBarColors(
    val containerColor: Color,
    val indicatorColor: Color,
    val contentColor: Color,
    val activeContentColor: Color,
)

// Defaults object for creating the Colors instance
object FloatingBottomBarDefaults {
    @Composable
    fun colors(
        containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
        indicatorColor: Color = MaterialTheme.colorScheme.primary,
        contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        activeContentColor: Color = indicatorColor,
    ): FloatingBottomBarColors = FloatingBottomBarColors(
        containerColor = containerColor,
        indicatorColor = indicatorColor,
        contentColor = contentColor,
        activeContentColor = activeContentColor,
    )
}

@Composable
fun <T> FloatingBottomBar(
    items: List<T>,
    selectedIndex: () -> Int,
    onSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    mode: FloatingBottomBarMode = FloatingBottomBarMode.LiquidGlass,
    colors: FloatingBottomBarColors = FloatingBottomBarDefaults.colors(),
    iconContent: @Composable (item: T, index: Int) -> Unit,
) {
    val isInDark = isSystemInDarkTheme()
    val pillShape = remember { CircleShape }
    val isLiquidGlassMode =
        mode == FloatingBottomBarMode.LiquidGlass || mode == FloatingBottomBarMode.LiquidGlassBlur
    val isBlurMode = mode == FloatingBottomBarMode.Blur
    val containerColor =
        if (isLiquidGlassMode) {
            colors.containerColor.copy(alpha = if (mode == FloatingBottomBarMode.LiquidGlassBlur) 0.5f else 0.4f)
        } else {
            colors.containerColor
        }

    val tabsBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    val tabsCount = items.size

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }

    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { 4.dp.toPx() }
    val panelOffset by remember(rubberBandPx) {
        derivedStateOf {
            if (totalWidthPx == 0f) {
                0f
            } else {
                val fraction = (offsetAnimation.value / totalWidthPx).fastCoerceIn(-1f, 1f)
                rubberBandPx * (if (fraction < 0f) -1f else 1f) * EaseOut.transform(abs(fraction))
            }
        }
    }

    var currentIndex by remember { mutableIntStateOf(selectedIndex()) }
    val selectedIndexUpdated by rememberUpdatedState(selectedIndex)
    val onSelectedUpdated by rememberUpdatedState(onSelected)

    fun indexAt(positionX: Float): Int {
        if (tabWidthPx == 0f) return currentIndex
        val horizontalPaddingPx = with(density) { 4.dp.toPx() }
        val logicalX = if (isLtr) positionX else totalWidthPx - positionX
        return ((logicalX - horizontalPaddingPx) / tabWidthPx)
            .toInt()
            .fastCoerceIn(0, tabsCount - 1)
    }

    val dampedDragAnimation = remember(animationScope, tabsCount, density, isLtr) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = currentIndex.toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1f,
            canDrag = { position ->
                position.x in 0f..totalWidthPx
            },
            onDragStarted = { position ->
                updateValue(indexAt(position.x).toFloat())
            },
            onDragStopped = {
                val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                if (currentIndex != targetIndex) {
                    currentIndex = targetIndex
                    onSelectedUpdated(targetIndex)
                }
                updateValue(targetIndex.toFloat())
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDragCancelled = {
                updateValue(currentIndex.toFloat())
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0f && dragAmount.x != 0f) {
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat()),
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            },
        )
    }

    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { selectedIndexUpdated() }.collectLatest { index ->
            if (currentIndex != index) {
                currentIndex = index
                dampedDragAnimation.animateToValue(index.toFloat())
            }
        }
    }

    val activateTab = remember(dampedDragAnimation) {
        { index: Int ->
            if (currentIndex != index) {
                currentIndex = index
                onSelectedUpdated(index)
            }
            dampedDragAnimation.animateToValue(index.toFloat())
        }
    }

    val baseHighlight = rememberGravityRotatedHighlight(iosIndicatorSpecular, extraDegrees = -45f)
    val pillHighlight = rememberGravityRotatedHighlight(iosIndicatorSpecular, extraDegrees = 90f)
    val combinedBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)

    val tabsContent: @Composable RowScope.() -> Unit = {
        val scale = LocalFloatingBottomBarTabScale.current
        val contentColor = LocalFloatingBottomBarContentColor.current
        items.forEachIndexed { index, item ->
            Column(
                modifier = Modifier
                    .defaultMinSize(minWidth = 76.dp)
                    .semantics(mergeDescendants = true) {
                        selected = index == currentIndex
                        role = Role.Tab
                        onClick {
                            activateTab(index)
                            true
                        }
                    }
                    .onKeyEvent { event ->
                        val isActivationKey = event.key == Key.Enter ||
                            event.key == Key.NumPadEnter ||
                            event.key == Key.Spacebar
                        if (isActivationKey) {
                            if (event.type == KeyEventType.KeyUp) activateTab(index)
                            true
                        } else {
                            false
                        }
                    }
                    .focusable()
                    .fillMaxHeight()
                    .graphicsLayer {
                        val itemScale = scale()
                        scaleX = itemScale
                        scaleY = itemScale
                    },
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CompositionLocalProvider(M3LocalContentColor provides contentColor) {
                    iconContent(item, index)
                }
            }
        }
    }

    Box(
        modifier = modifier.width(IntrinsicSize.Min),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Base layer (unselected state)
        CompositionLocalProvider(LocalFloatingBottomBarContentColor provides colors.contentColor) {
            Row(
                Modifier
                    .onGloballyPositioned { coords ->
                        totalWidthPx = coords.size.width.toFloat()
                        val contentWidthPx = totalWidthPx - with(density) { 8.dp.toPx() }
                        tabWidthPx = (contentWidthPx / tabsCount).coerceAtLeast(0f)
                    }
                    .graphicsLayer { translationX = panelOffset }
                    .dropShadow(
                        shape = pillShape,
                        shadow = Shadow(
                            radius = 10.dp,
                            color = Color.Black,
                            alpha = if (isInDark) 0.2f else 0.1f,
                        ),
                    )
                    .then(
                        if (isLiquidGlassMode) {
                            Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { pillShape },
                                effects = {
                                    vibrancy()
                                    val baseBlurRadius = if (mode == FloatingBottomBarMode.LiquidGlassBlur) 25.dp.toPx() else 4.dp.toPx()
                                    blur(baseBlurRadius, baseBlurRadius)
                                    lens(
                                        refractionHeight = 24.dp.toPx(),
                                        refractionAmount = 24.dp.toPx(),
                                    )
                                },
                                highlight = { baseHighlight.copy(alpha = 0.75f) },
                                layerBlock = {
                                    val layerWidth = size.width.coerceAtLeast(1f)
                                    val s = lerp(1f, 1f + 16.dp.toPx() / layerWidth, dampedDragAnimation.pressProgress)
                                    scaleX = s
                                    scaleY = s
                                },
                            )
                        } else if (isBlurMode) {
                            Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { pillShape },
                                effects = {
                                    blur(25.dp.toPx(), 25.dp.toPx())
                                },
                            )
                        } else {
                            Modifier.background(containerColor, pillShape)
                        },
                    )
                    .then(dampedDragAnimation.modifier)
                    .height(56.dp)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = tabsContent,
            )
        }

        // Active layer (selected state)
        if (isLiquidGlassMode) {
            CompositionLocalProvider(
                LocalFloatingBottomBarTabScale provides {
                    lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
                },
                LocalFloatingBottomBarContentColor provides colors.activeContentColor,
            ) {
                Row(
                    Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer { translationX = panelOffset }
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabsContent()
                }
            }
        }

        if (tabWidthPx > 0f) {
            val tabWidthDp = with(density) { tabWidthPx.toDp() }
            if (isLiquidGlassMode) {
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .graphicsLayer {
                            val progressOffset = dampedDragAnimation.value * tabWidthPx
                            translationX = if (isLtr) progressOffset + panelOffset else -progressOffset + panelOffset
                        }
                        .drawBackdrop(
                            backdrop = combinedBackdrop,
                            shape = { pillShape },
                            effects = {
                                val progress = dampedDragAnimation.pressProgress
                                lens(
                                    refractionHeight = 10.dp.toPx() * progress,
                                    refractionAmount = 14.dp.toPx() * progress,
                                    depthEffect = true,
                                    chromaticAberration = 0.5f,
                                )
                            },
                            highlight = { pillHighlight.copy(alpha = dampedDragAnimation.pressProgress) },
                            layerBlock = {
                                scaleX = dampedDragAnimation.scaleX
                                scaleY = dampedDragAnimation.scaleY
                                val velocity = dampedDragAnimation.velocity / 10f
                                scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                                scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                            },
                            onDrawSurface = {
                                val progress = dampedDragAnimation.pressProgress
                                drawRect(
                                    color = if (isInDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f),
                                    alpha = 1f - progress,
                                )
                                drawRect(Color.Black.copy(alpha = 0.03f * progress))
                            },
                        )
                        .innerShadow(shape = pillShape) {
                            InnerShadow(
                                radius = 8.dp * dampedDragAnimation.pressProgress,
                                color = Color.Black.copy(alpha = 0.15f),
                                alpha = dampedDragAnimation.pressProgress,
                            )
                        }
                        .height(48.dp)
                        .width(tabWidthDp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .graphicsLayer {
                            val progressOffset = dampedDragAnimation.value * tabWidthPx
                            translationX = if (isLtr) progressOffset + panelOffset else -progressOffset + panelOffset
                        }
                        .clip(pillShape)
                        .background(colors.indicatorColor.copy(alpha = 0.15f), pillShape)
                        .height(48.dp)
                        .width(tabWidthDp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    CompositionLocalProvider(
                        LocalFloatingBottomBarTabScale provides {
                            lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
                        },
                        LocalFloatingBottomBarContentColor provides colors.activeContentColor,
                    ) {
                        Row(
                            Modifier
                                .clearAndSetSemantics {}
                                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                                .requiredWidth(with(density) { (totalWidthPx - 8.dp.toPx()).toDp() })
                                .height(48.dp)
                                .graphicsLayer {
                                    val progressOffset = dampedDragAnimation.value * tabWidthPx
                                    translationX = if (isLtr) -progressOffset else progressOffset
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            content = tabsContent,
                        )
                    }
                }
            }
        }
    }
}
