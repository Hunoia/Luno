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
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.coroutines.delay
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
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported

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

/**
 * 采样重力向量的采样间隔。传感器本身仍以 SENSOR_DELAY_FASTEST 在跑(那是三方库注册的,
 * 改不了),但没必要把它的每次采样都传到 Compose 层。
 */
private const val GRAVITY_POLL_MS = 33L

/**
 * 两个单位向量点积 ≥ 该值即视为同一方向。cos(1.8°) ≈ 0.99939 —— 高光方向差 1.8° 肉眼
 * 不可辨,所以在这个范围内复用上一次的 [GravityDir],让 Compose 看不到变化。
 */
private const val GRAVITY_SAME_DIR_DOT = 0.9995f

/**
 * 归一化后的重力方向。用引用相等做 remember 的 key:只有方向真的变了才会产生新对象,
 * 从而让下游的 `innerShadow { }` 保持同一个 lambda 引用。
 */
private class GravityDir(val x: Float, val y: Float) {
    fun isSameDirectionAs(other: GravityDir): Boolean =
        x * other.x + y * other.y >= GRAVITY_SAME_DIR_DOT
}

/**
 * 带死区的重力方向,整个 bar 只订阅一次。
 *
 * miuix-blur 的 `rememberDeviceTilt()` 以 `SENSOR_DELAY_FASTEST`（约 1000Hz）注册加速度计,
 * listener 里对每个采样都 `$tilt.setValue(new DeviceTilt(...))` —— 那个 float 参数只是
 * EMA 低通滤波,平滑了数值但没有减少发送次数,也没有 `distinctUntilChanged`。
 *
 * 之前直接 `by rememberDeviceTilt()` 并把 tilt 当 remember 的 key,于是静止时每个采样都会:
 *   重组 → 新 Highlight → 新 BloomStroke → `innerShadow { }` 新的 lambda 引用
 *   → `InnerShadowElement.equals` 按引用比较返回 false → `invalidateDraw()`
 * 也就是启动器首页(bar 唯一的使用方)常驻时,这个带 blur 与离屏层的胶囊被每秒上千次重绘。
 * 而且当时为 indicator 和 pill 各调了一次 `rememberDeviceTilt()`,等于两条独立订阅。
 *
 * 现在:33ms 轮询一次、方向变化超过 1.8° 才写入 state。绝大多数采样 Compose 完全看不见,
 * 也就不会走到重绘。传感器硬件仍在跑,那部分省不掉。
 */
@Composable
private fun rememberStableGravityDirection(): State<GravityDir> {
    val tilt = rememberDeviceTilt()
    val dir = remember { mutableStateOf(GravityDir(0f, -1f)) }
    LaunchedEffect(tilt) {
        while (true) {
            val gx = tilt.value.gravityX
            val gy = tilt.value.gravityY
            val magSq = gx * gx + gy * gy
            val next = if (magSq > GRAVITY_DIR_THRESHOLD_SQ) {
                val invMag = 1f / sqrt(magSq)
                GravityDir(gx * invMag, gy * invMag)
            } else {
                GravityDir(0f, -1f)
            }
            if (!next.isSameDirectionAs(dir.value)) dir.value = next
            delay(GRAVITY_POLL_MS)
        }
    }
    return dir
}

/** Tracks gravity for a `dualPeak` highlight's primary light, with an extra UV-clockwise offset on top. */
@Composable
private fun rememberGravityRotatedHighlight(
    base: Highlight,
    gravity: GravityDir,
    extraDegrees: Float = 0f,
): Highlight {
    val baseStyle = base.style as BloomStroke
    val rotatedPrimary = remember(gravity, baseStyle.primaryLight, extraDegrees) {
        val basePrimary = baseStyle.primaryLight
        val rad = extraDegrees * PI / 180.0
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val lx = c * gravity.x - s * gravity.y
        val ly = s * gravity.x + c * gravity.y
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
    val renderEffectSupported = isRenderEffectSupported()
    val isLiquidGlassMode =
        renderEffectSupported && (mode == FloatingBottomBarMode.LiquidGlass || mode == FloatingBottomBarMode.LiquidGlassBlur)
    val isBlurMode = renderEffectSupported && mode == FloatingBottomBarMode.Blur
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

    val gravityDir by rememberStableGravityDirection()
    val baseHighlight = rememberGravityRotatedHighlight(iosIndicatorSpecular, gravityDir, extraDegrees = -45f)
    val pillHighlight = rememberGravityRotatedHighlight(iosIndicatorSpecular, gravityDir, extraDegrees = 90f)
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
