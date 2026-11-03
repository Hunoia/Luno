package hunoia.luno.annotate.render

import android.graphics.Bitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import hunoia.luno.annotate.model.AnnotateOps
import hunoia.luno.annotate.model.AnnotateState
import hunoia.luno.annotate.model.ContainFit
import androidx.compose.ui.geometry.Size
import kotlin.math.ceil

/**
 * 导出渲染（计划 D4 / §3.4）。
 *
 * 不截屏：底图分辨率就是导出分辨率，标注按图片像素重放，最后按选区裁剪。
 * 与预览共用 [drawAnnotate]，不存在第二套绘制代码。
 */
object AnnotateExporter {

    /**
     * 渲染并裁剪。调用方负责在合适线程执行（大图分配 + 逐格马赛克）。
     * [base] 与 [state] 的图片尺寸必须一致；不一致时以 state 为准裁切底图。
     */
    fun renderCropped(state: AnnotateState, base: BaseImage): Bitmap {
        val crop = AnnotateOps.exportCropRect(state)
        val outWidth = ceil(crop.width).toInt().coerceAtLeast(1)
        val outHeight = ceil(crop.height).toInt().coerceAtLeast(1)

        val out = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        // 先平移，再按整图坐标重放；等价于把 crop 区域平移到原点。
        canvas.translate(-crop.left, -crop.top)

        val composeCanvas = androidx.compose.ui.graphics.Canvas(canvas)
        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = composeCanvas,
            size = Size(state.imageWidth.toFloat(), state.imageHeight.toFloat()),
        ) {
            drawAnnotate(
                state = state,
                base = base,
                fit = ContainFit.identity(state.imageWidth, state.imageHeight),
                showSelection = false,
            )
        }
        return out
    }
}
