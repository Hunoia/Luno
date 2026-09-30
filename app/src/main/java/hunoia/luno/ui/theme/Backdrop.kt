package hunoia.luno.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported

val GlassBlurRadius: Dp = 25.dp

@Composable
fun liquidGlassBackdrop(): LayerBackdrop {
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

@Composable
fun Modifier.liquidGlassBlur(
    backdrop: LayerBackdrop? = null,
    shape: Shape = RectangleShape,
    blurRadius: Dp = GlassBlurRadius,
): Modifier {
    if (backdrop == null || !isRenderEffectSupported()) return this
    val radius = with(LocalDensity.current) { blurRadius.toPx() }
    return then(
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = { blur(radius, radius) },
            onDrawSurface = {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White, Color.White.copy(alpha = 0f)),
                        startY = 0f,
                        endY = size.height,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
        )
    )
}

@Composable
fun glassSurfaceColor(): Color =
    if (isRenderEffectSupported()) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer
