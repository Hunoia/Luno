package hunoia.luno.runtime.overlay

import hunoia.luno.config.model.GestureButton

/**
 * 一个手势按钮窗口的标识。
 *
 * [mirrored] 区分同一按钮的主窗口与镜像窗口 —— 镜像窗口的 [windowButton] 与主窗口
 * 字段完全相同（`mirroredButton()` 会把 `mirrorHorizontal` 置回 false），只能靠这个显式标记区分。
 * 窗口身份由 (sourceButton.id, mirrored) 决定，bounds 属于可更新内容而非身份的一部分。
 */
internal data class GestureButtonWindowTarget(
    val sourceButton: GestureButton,
    val windowButton: GestureButton,
    val mirrored: Boolean = false,
)
