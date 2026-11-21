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
    // 局部模糊（真模糊，非马赛克）。
    BLUR,
    // 图片贴纸。
    STICKER,
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
    }
}
