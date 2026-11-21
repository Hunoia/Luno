package hunoia.luno.annotate.render

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.roundToInt

/**
 * 标注底图。宿主（浮层截图 / 图片选择器）都把它包一层再进渲染器，
 * 这样预览与导出拿到的是同一份像素事实源（马赛克取样依赖它）。
 *
 * HARDWARE 位图不能 `getPixel`/预栅格化，构造时降级为软件位图；只发生在初始化一次。
 */
class BaseImage(source: Bitmap) {

    val bitmap: Bitmap = if (source.config == Bitmap.Config.HARDWARE) {
        source.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        source
    }

    val width: Int get() = bitmap.width
    val height: Int get() = bitmap.height

    val image: ImageBitmap by lazy { bitmap.asImageBitmap() }

    /**
     * 马赛克预栅格化：按单元边长把整图缩成小块，绘制时用最近邻放大成块状。
     *
     * 每张只有 `(w/cell)×(h/cell)` 像素（2048 长边 / 24px ≈ 22KB），按取整后的
     * cell 缓存最近 3 份；cell 滑杆连续拖动不会每帧重做。
     */
    private val mosaicCache = LinkedHashMap<Int, Bitmap>()

    /** 局部模糊预栅格化缓存（键 = 模糊半径整数值）。 */
    private val blurCache = LinkedHashMap<Int, Bitmap>()

    fun mosaicImage(cellSize: Float): Bitmap {
        // 量化到 4px 步进：滑杆连续拖动时缓存键稳定，避免反复重建缩略图。
        val cell = ((cellSize / 4f).roundToInt() * 4).coerceIn(4, 96)
        mosaicCache.remove(cell)?.let { cached ->
            // 命中后重新插入，维持 LinkedHashMap 的访问顺序（真 LRU）。
            mosaicCache[cell] = cached
            return cached
        }
        if (mosaicCache.size >= MOSAIC_CACHE_LIMIT) {
            // 不主动 recycle：上一帧可能仍在 RenderThread 上取样，交给 GC。
            mosaicCache.remove(mosaicCache.keys.first())
        }
        val smallW = (width / cell).coerceAtLeast(1)
        val smallH = (height / cell).coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, smallW, smallH, true)
        mosaicCache[cell] = scaled
        return scaled
    }

    /**
     * 局部模糊预栅格化（P2d）：按模糊半径把整图「金字塔缩放」糊一遍再放大回原尺寸，
     * 绘制时经轨迹 mask 取样（与马赛克同一管线）。
     *
     * 半径 → 下采样倍数：radius 2 → 2×，radius 96 → 32×；两级下采样 + 回升带来
     * 视觉等效的高斯感，成本只有几次 bilinear 缩放。按半径整数值缓存最近几份
     * （滑杆连续拖动不逐帧重做）。
     */
    fun blurImage(radiusPx: Float): Bitmap {
        val radius = radiusPx.roundToInt().coerceIn(2, 96)
        blurCache.remove(radius)?.let { cached ->
            blurCache[radius] = cached
            return cached
        }
        if (blurCache.size >= BLUR_CACHE_LIMIT) {
            blurCache.remove(blurCache.keys.first())
        }
        val downScale = (radius / 3f).coerceIn(2f, 32f)
        val smallW = (width / downScale).toInt().coerceAtLeast(2)
        val smallH = (height / downScale).toInt().coerceAtLeast(2)
        var current = Bitmap.createScaledBitmap(bitmap, smallW, smallH, true)
        // 再缩半回升一级：补一层低频模糊，避免强 radius 下残留块状边缘。
        val halfW = (smallW / 2).coerceAtLeast(1)
        val halfH = (smallH / 2).coerceAtLeast(1)
        current = Bitmap.createScaledBitmap(current, halfW, halfH, true)
        current = Bitmap.createScaledBitmap(current, smallW, smallH, true)
        val up = Bitmap.createScaledBitmap(current, width, height, true)
        blurCache[radius] = up
        return up
    }

    private companion object {
        const val MOSAIC_CACHE_LIMIT = 6
        const val BLUR_CACHE_LIMIT = 4
    }
}
