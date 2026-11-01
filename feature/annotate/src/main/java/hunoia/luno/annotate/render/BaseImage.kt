package hunoia.luno.annotate.render

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * 标注底图。宿主（浮层截图 / 图片选择器）都把它包一层再进渲染器，
 * 这样预览与导出拿到的是同一份像素事实源（马赛克采样依赖它）。
 *
 * HARDWARE 位图不能 `getPixel`，构造时降级为软件位图；只发生在初始化一次。
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

    /** 马赛克单元采样：取单元中心像素，越界自动 clamp。 */
    fun sampleColor(x: Int, y: Int): Int {
        val px = x.coerceIn(0, width - 1)
        val py = y.coerceIn(0, height - 1)
        return bitmap.getPixel(px, py)
    }
}
