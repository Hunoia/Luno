package hunoia.luno.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import hunoia.luno.ui.theme.ContainerSmoothing
import sv.lib.squircleshape.SquircleShape

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = if (darkTheme) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        // M3 Shapes 要求 CornerBasedShape，不能用 Shape 类型的 token；同一档位在此重建一次。
        shapes = Shapes(
            extraSmall = SquircleShape(ShapeExtraSmall, ContainerSmoothing),
            small = SquircleShape(ShapeSmall, ContainerSmoothing),
            medium = SquircleShape(ShapeMedium, ContainerSmoothing),
            large = SquircleShape(ShapeLarge, ContainerSmoothing),
            extraLarge = SquircleShape(ShapeExtraLarge, ContainerSmoothing),
        ),
        content = content
    )
}
