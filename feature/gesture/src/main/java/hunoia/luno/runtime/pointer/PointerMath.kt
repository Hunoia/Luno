package hunoia.luno.runtime.pointer

import androidx.compose.ui.geometry.Offset
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.clampPointerPoint
import hunoia.luno.config.model.followPoint
import hunoia.luno.config.model.initialPoint
import kotlin.math.hypot

/** 光标初始位置：不跟随 / 无触点时的归一化落点。 */
fun pointerInitialPosition(
    settings: GestureSettings.Pointer,
): Offset {
    val point = settings.initialPoint(DensityProvider.screenWidthPx, DensityProvider.screenHeightPx)
    return Offset(point.x, point.y)
}

/** 跟随触发点：叠加按四分区镜像后的偏移，再夹进屏内。 */
fun pointerFollowPosition(
    trigger: Offset,
    settings: GestureSettings.Pointer,
): Offset {
    val point = settings.followPoint(
        triggerX = trigger.x,
        triggerY = trigger.y,
        screenWidthPx = DensityProvider.screenWidthPx,
        screenHeightPx = DensityProvider.screenHeightPx,
    )
    return Offset(point.x, point.y)
}

/** 有触点且开启跟随时落 [pointerFollowPosition]，否则退回配置的初始位置。 */
fun pointerStartPosition(settings: GestureSettings.Pointer, trigger: Offset?): Offset =
    if (settings.followTrigger && trigger != null && trigger.x.isFinite() && trigger.y.isFinite()) {
        pointerFollowPosition(trigger, settings)
    } else {
        pointerInitialPosition(settings)
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
    val point = clampPointerPoint(
        x = position.x,
        y = position.y,
        screenWidthPx = DensityProvider.screenWidthPx,
        screenHeightPx = DensityProvider.screenHeightPx,
    )
    return Offset(point.x, point.y)
}
