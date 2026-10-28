package hunoia.luno.runtime.pointer

import androidx.compose.ui.geometry.Offset
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.config.model.GestureSettings
import kotlin.math.hypot

/** 光标初始位置：水平居中，垂直取 [GestureSettings.Pointer.initialYRatio]。 */
fun pointerInitialPosition(
    settings: GestureSettings.Pointer,
): Offset {
    return Offset(
        x = DensityProvider.screenWidthPx * 0.5f,
        y = DensityProvider.screenHeightPx * settings.initialYRatio.coerceIn(0f, 1f),
    )
}

/**
 * 指针移动映射：低速精修、高速快移（precisionFactor），叠加灵敏度与加速曲线。
 * 手指挪动 1px，光标挪动 sensitivity × precision × (1 + acceleration×speed) px。
 */
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

fun clampPointerPosition(position: Offset): Offset {
    return Offset(
        x = position.x.coerceIn(0f, DensityProvider.screenWidthPx.toFloat()),
        y = position.y.coerceIn(0f, DensityProvider.screenHeightPx.toFloat()),
    )
}
