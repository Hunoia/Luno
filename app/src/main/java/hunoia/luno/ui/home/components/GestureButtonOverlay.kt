package hunoia.luno.ui.home

import hunoia.luno.ui.theme.*
import hunoia.luno.config.defaults.SettingsUiDefaults.GestureButtonColorAlpha
import hunoia.luno.gesture.GestureFacade

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.util.fastForEach

@Composable
fun GestureButtonOverlay(
    visible: Boolean,
    gestureButtons: List<hunoia.luno.config.model.GestureButton>,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioMediumBouncy)),
        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioMediumBouncy))
    ) {
        val colorScheme = MaterialTheme.colorScheme
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    gestureButtons.fastForEach { button ->
                        if (!button.enabled) {
                            return@fastForEach
                        }
                        val bounds = GestureFacade.bounds(button)
                        drawRoundRect(
                            color = when (button.color == android.graphics.Color.TRANSPARENT) {
                                true -> colorScheme.primary.copy(alpha = GestureButtonColorAlpha)
                                else -> Color(button.color).copy(alpha = GestureButtonColorAlpha)
                            },
                            topLeft = bounds.topLeft,
                            size = bounds.size,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(Spacing4.toPx(), Spacing4.toPx())
                        )
                        drawRoundRect(
                            color = colorScheme.outlineVariant,
                            topLeft = bounds.topLeft,
                            size = bounds.size,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(Spacing4.toPx(), Spacing4.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = Spacing1.toPx())
                        )
                    }
                }
        )
    }
}
