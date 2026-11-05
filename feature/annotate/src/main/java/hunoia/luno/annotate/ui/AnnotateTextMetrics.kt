package hunoia.luno.annotate.ui

import android.graphics.Paint
import android.graphics.Typeface
import hunoia.luno.annotate.model.ContainFit
import hunoia.luno.annotate.model.PxRect
import hunoia.luno.annotate.model.TextElement

/**
 * 文字元素的真实命中框（返回**图片像素**坐标）。
 *
 * 模型层 `AnnotationElement.bounds()` 对文字用 `textSize * text.length` 估算，
 * 全角/半角混排时宽度差一倍，点不准已有文字。这里复刻文字渲染的同一套 Paint 参数
 * （`Typeface.DEFAULT`、`textSize × fit.scale`、按 `\n` 分行、行高 = descent - ascent），
 * 量出「实际画出来的占位」，让命中区与所见一致。
 *
 * 依赖 `android.graphics.Paint`，只能在 UI 层调用（JVM 单测仍走模型层估算）。
 */
internal fun TextElement.realHitBounds(fit: ContainFit): PxRect {
    val size = (textSize * fit.scale).coerceAtLeast(1f)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = Typeface.DEFAULT
    }
    val lines = text.split('\n')
    val metrics = paint.fontMetrics
    val lineHeight = metrics.descent - metrics.ascent
    val width = lines.maxOfOrNull { paint.measureText(it) } ?: 0f
    val origin = fit.imageToView(position)
    // 与渲染一致：首行基线 = position.y - ascent，故框顶就是 position.y；
    // 末行底 = position.y + 行数 × 行高。
    return fit.viewRectToImage(
        PxRect(
            left = origin.x,
            top = origin.y,
            right = origin.x + width,
            bottom = origin.y + lineHeight * lines.size,
        ),
    )
}
