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

    private companion object {
        const val MOSAIC_CACHE_LIMIT = 6
    }
}
