package hunoia.luno.pointer

import androidx.compose.ui.geometry.Offset
import hunoia.luno.bridge.DensityProvider
import hunoia.luno.config.model.GestureSettings
import kotlin.math.hypot

fun pointerInitialPosition(
    settings: GestureSettings.Pointer,
    previousPosition: Offset = Offset.Unspecified,
): Offset {
    if (settings.continuousMode && previousPosition.x.isFinite() && previousPosition.y.isFinite()) {
        return clampPointerPosition(previousPosition)
    }
    return Offset(
        x = DensityProvider.screenWidthPx * 0.5f,
        y = DensityProvider.screenHeightPx * settings.initialYRatio.coerceIn(0f, 1f),
    )
}

fun movePointerCursor(
    position: Offset,
    dragAmount: Offset,
    settings: GestureSettings.Pointer,
): Offset {
    val speed = hypot(dragAmount.x, dragAmount.y)
    if (speed < 0.1f) return position
    val precisionFactor = 0.38f + (speed / 36f).coerceIn(0f, 1f) * 0.62f
    val factor = 1f + settings.acceleration * (speed / 80f).coerceAtMost(2f)
    return clampPointerPosition(
        Offset(
            x = position.x + dragAmount.x * settings.sensitivityX * precisionFactor * factor,
            y = position.y + dragAmount.y * settings.sensitivityY * precisionFactor * factor,
        )
    )
}

fun isPointerStillMovement(
    dragAmount: Offset,
    settings: GestureSettings.Pointer,
): Boolean {
    val deadZone = DensityProvider.dp2px(settings.movementDeadZoneDp.toFloat()).toFloat()
    return hypot(dragAmount.x, dragAmount.y) <= deadZone
}

fun isPointerCancelGesture(
    touchPosition: Offset,
    settings: GestureSettings.Pointer,
): Boolean {
    if (!touchPosition.x.isFinite() || !touchPosition.y.isFinite()) return false
    val threshold = DensityProvider.dp2px(settings.edgeCancelThresholdDp.toFloat()).toFloat()
    val width = DensityProvider.screenWidthPx.toFloat()
    val height = DensityProvider.screenHeightPx.toFloat()
    val atBottom = touchPosition.y >= height - threshold
    return (touchPosition.x <= threshold && atBottom) ||
        (touchPosition.x >= width - threshold && atBottom)
}

fun clampPointerPosition(position: Offset): Offset {
    return Offset(
        x = position.x.coerceIn(0f, DensityProvider.screenWidthPx.toFloat()),
        y = position.y.coerceIn(0f, DensityProvider.screenHeightPx.toFloat()),
    )
}
