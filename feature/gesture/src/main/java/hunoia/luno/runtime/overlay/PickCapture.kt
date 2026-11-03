package hunoia.luno.runtime.overlay

import android.graphics.Bitmap

/**
 * 预截图的独立快照：幽灵卡关闭后动作仍在后台裁剪，不能依赖 overlay 的可变字段
 *（close() 会重置原点并回收位图）。
 */
data class PickCapture(
    val bitmap: Bitmap,
    val originX: Int,
    val originY: Int,
    val screenW: Int,
    val screenH: Int,
)
