package hunoia.luno.annotate.model

/** 工具类型。序号即工具条顺序，持久化只存风格，不存笔迹。 */
enum class ToolKind {
    PEN,
    HIGHLIGHT,
    ERASER,
    TEXT,
    MOSAIC,
    SELECT,
    // 形状已合并为单个工具（子类型见 [ShapeKind]），裁剪接管导出选区。
    SHAPE,
    CROP,
    NUMBER,
}

/**
 * 形状工具的子类型。选择 SHAPE 工具后在样式面板里切换，
 * 实际元素类型映射到 [elementKind]。
 */
enum class ShapeKind {
    RECT,
    ELLIPSE,
    ROUND_RECT,
    TRIANGLE,
    LINE,
    DASHED_LINE,
    ARROW,
    CHECK,
    CROSS,
    STAR,
    HEART,
    ;

    val elementKind: ElementKind get() = when (this) {
        RECT -> ElementKind.RECT
        ELLIPSE -> ElementKind.ELLIPSE
        ROUND_RECT -> ElementKind.ROUND_RECT
        TRIANGLE -> ElementKind.TRIANGLE
        LINE -> ElementKind.LINE
        DASHED_LINE -> ElementKind.DASHED_LINE
        ARROW -> ElementKind.ARROW
        CHECK -> ElementKind.CHECK
        CROSS -> ElementKind.CROSS
        STAR -> ElementKind.STAR
        HEART -> ElementKind.HEART
    }
}

/**
 * 每个工具的颜色 / 线宽。颜色用 ARGB Long（Compose Color.value 也是同口径），
 * 避免模型层依赖 Compose，导出侧可直接转 `android.graphics.Color`。
 */
data class ToolStyle(
    val colorArgb: Long = 0xFFFF3B30,
    /**
     * 笔迹 / 线框宽度，**屏幕 dp**（B9）：落笔瞬间按 `dp × density ÷ fit.scale`
     * 换算成图片像素存进元素，所以在不同分辨率底图上手感一致。
     */
    val strokeWidth: Float = 3f,
    /** 文字大小，图片像素。 */
    val textSize: Float = 48f,
    /** 马赛克单元边长，图片像素。 */
    val mosaicCell: Float = 24f,
    /** 形状是否半透明填充（SHAPE 工具）：新画的形状带填充 + 描边。 */
    val shapeFilled: Boolean = false,
)

/** 工具条位置：按屏幕比例存，跨分辨率恢复（同 Surface 位置规则）。 */
data class ToolbarAnchor(val xFraction: Float, val yFraction: Float) {
    fun sanitized(): ToolbarAnchor = ToolbarAnchor(
        xFraction = xFraction.coerceIn(0f, 1f),
        yFraction = yFraction.coerceIn(0f, 1f),
    )
}
