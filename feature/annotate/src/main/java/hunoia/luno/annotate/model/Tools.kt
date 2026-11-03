package hunoia.luno.annotate.model

/** 工具类型。序号即工具条顺序，持久化只存风格，不存笔迹。 */
enum class ToolKind {
    PEN,
    ERASER,
    RECT,
    ARROW,
    TEXT,
    MOSAIC,
    SELECT,
}

/**
 * 每个工具的颜色 / 线宽。颜色用 ARGB Long（Compose Color.value 也是同口径），
 * 避免模型层依赖 Compose，导出侧可直接转 `android.graphics.Color`。
 */
data class ToolStyle(
    val colorArgb: Long = 0xFFFF3B30,
    /** 笔迹宽度 / 线框宽度，图片像素。 */
    val strokeWidth: Float = 6f,
    /** 文字大小，图片像素。 */
    val textSize: Float = 48f,
    /** 马赛克单元边长，图片像素。 */
    val mosaicCell: Float = 24f,
)

/** 工具条位置：按屏幕比例存，跨分辨率恢复（同 Surface 位置规则）。 */
data class ToolbarAnchor(val xFraction: Float, val yFraction: Float) {
    fun sanitized(): ToolbarAnchor = ToolbarAnchor(
        xFraction = xFraction.coerceIn(0f, 1f),
        yFraction = yFraction.coerceIn(0f, 1f),
    )
}
