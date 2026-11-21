package hunoia.luno.runtime.floatball

import hunoia.luno.config.model.WindowBounds

/**
 * 判定区判定：球心是否落进判定区（详见 `FloatBallSettings.dropAction`）。
 *
 * 判定区是归一化矩形（[hunoia.luno.config.model.FloatBallSettings.dropZone]，未自定义时
 * 为默认居中 1/3 × 1/4），按 [screenWidth]/[screenHeight] 换算成 px 后做边界包含；
 * 纯函数，JVM 可测。
 */
internal fun isBallInDropZone(
    centerX: Int,
    centerY: Int,
    screenWidth: Int,
    screenHeight: Int,
    zone: WindowBounds,
): Boolean {
    if (screenWidth <= 0 || screenHeight <= 0) return false
    val left = zone.x * screenWidth
    val top = zone.y * screenHeight
    val right = left + zone.width * screenWidth
    val bottom = top + zone.height * screenHeight
    return centerX >= left && centerX <= right && centerY >= top && centerY <= bottom
}
