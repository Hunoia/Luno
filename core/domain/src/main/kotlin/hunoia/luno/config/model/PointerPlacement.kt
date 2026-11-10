package hunoia.luno.config.model

import hunoia.luno.config.defaults.GestureSettingsDefaults

/** 指针落点（屏幕绝对坐标，光标中心）。 */
data class PointerPoint(val x: Float, val y: Float)

/**
 * 指针起始点：
 * - [initialPoint]：不跟随时的归一化初始位置。
 * - [followPoint]：以触发点为基础叠加有符号偏移，再夹进屏内。
 *
 * 偏移只存一份，以「左下分区」为基准：触发点落在右半翻 X、落在上半翻 Y，
 * 这样四个分区共用同一份配置，且天然朝屏幕内侧避让手指。
 * 纯几何，运行时与调整页预览共用同一份口径。
 */
fun GestureSettings.Pointer.initialPoint(
    screenWidthPx: Int,
    screenHeightPx: Int,
): PointerPoint = clampPointerPoint(
    x = screenWidthPx * initialXRatio.coerceIn(0f, 1f),
    y = screenHeightPx * initialYRatio.coerceIn(0f, 1f),
    screenWidthPx = screenWidthPx,
    screenHeightPx = screenHeightPx,
)

/**
 * 触发点所在半屏的镜像符号：右半 −X、上半 −Y（基准为左下分区）。
 * 调整页反算偏移时复用同一份符号，避免两处各写一套。
 */
fun pointerMirrorSign(
    triggerX: Float,
    triggerY: Float,
    screenWidthPx: Int,
    screenHeightPx: Int,
): PointerPoint = PointerPoint(
    x = if (triggerX * 2f >= screenWidthPx) -1f else 1f,
    y = if (triggerY * 2f < screenHeightPx) -1f else 1f,
)

/** 跟随触点的落点：`触发点 + 镜像后的偏移`，偏移按屏幕宽高百分比换算后夹取。 */
fun GestureSettings.Pointer.followPoint(
    triggerX: Float,
    triggerY: Float,
    screenWidthPx: Int,
    screenHeightPx: Int,
): PointerPoint {
    val sign = pointerMirrorSign(triggerX, triggerY, screenWidthPx, screenHeightPx)
    val offsetX = followOffsetXRatio.coerceIn(
        GestureSettingsDefaults.PointerFollowOffsetMinRatio,
        GestureSettingsDefaults.PointerFollowOffsetMaxRatio,
    ) * screenWidthPx
    val offsetY = followOffsetYRatio.coerceIn(
        GestureSettingsDefaults.PointerFollowOffsetMinRatio,
        GestureSettingsDefaults.PointerFollowOffsetMaxRatio,
    ) * screenHeightPx
    return clampPointerPoint(
        x = triggerX + sign.x * offsetX,
        y = triggerY + sign.y * offsetY,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
    )
}

/** 光标中心夹进屏幕，不做半径夹取（允许贴边时半颗光标在屏外）。 */
fun clampPointerPoint(x: Float, y: Float, screenWidthPx: Int, screenHeightPx: Int): PointerPoint =
    PointerPoint(
        x = x.coerceIn(0f, screenWidthPx.toFloat()),
        y = y.coerceIn(0f, screenHeightPx.toFloat()),
    )

/**
 * 调整页反算：把屏幕上的显示位置还原成「以左下分区为基准」的偏移比例。
 * 返回值已夹到 ±100%，与 [followPoint] 的输入口径一致。
 */
fun pointerCanonicalOffset(
    trigger: PointerPoint,
    display: PointerPoint,
    screenWidthPx: Int,
    screenHeightPx: Int,
): PointerPoint {
    if (screenWidthPx <= 0 || screenHeightPx <= 0) return PointerPoint(0f, 0f)
    val sign = pointerMirrorSign(trigger.x, trigger.y, screenWidthPx, screenHeightPx)
    return PointerPoint(
        x = ((display.x - trigger.x) / screenWidthPx * sign.x).coerceIn(
            GestureSettingsDefaults.PointerFollowOffsetMinRatio,
            GestureSettingsDefaults.PointerFollowOffsetMaxRatio,
        ),
        y = ((display.y - trigger.y) / screenHeightPx * sign.y).coerceIn(
            GestureSettingsDefaults.PointerFollowOffsetMinRatio,
            GestureSettingsDefaults.PointerFollowOffsetMaxRatio,
        ),
    )
}
