package hunoia.luno.quicklaunch.icon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.Collections

/**
 * 搜索行图标的同步缓存：composition 里同步读，首帧即有图。
 *
 * AsyncImage 配 crossfade(false) 仍有一帧异步空隙（内存命中也要跳一帧），
 * 结果列表每次挂载十几行 App 图标 = 整表闪一次。只有 App 行/快捷方式行走这条路，
 * 其它行全是同步矢量所以不闪。填充只许在后台线程（搜索 flow 发射结果前），
 * composition 只读。
 */
object SearchIconCache {
    // 键 = 包名@边长：density 变化（折叠屏）时同包不同尺寸是两条独立缓存，不串味。
    private val cache: MutableMap<String, ImageBitmap> =
        Collections.synchronizedMap(HashMap(512))

    fun get(packageName: String, sizePx: Int): ImageBitmap? = cache[keyOf(packageName, sizePx)]

    /**
     * 确保这些包的图标在缓存里（阻塞当前线程，只能后台调）。
     * 已缓存的直接跳过，所以每次搜索的开销≈0，只有新包才解码。
     */
    fun ensureAll(context: Context, packageNames: Collection<String>, sizePx: Int) {
        if (packageNames.isEmpty() || sizePx <= 0) return
        val appCtx = context.applicationContext ?: context
        for (pkg in packageNames) {
            val key = keyOf(pkg, sizePx)
            if (!cache.containsKey(key)) {
                loadSync(appCtx, pkg, sizePx)?.let { cache[key] = it }
            }
        }
    }

    private fun keyOf(packageName: String, sizePx: Int) = "$packageName@$sizePx"

    /** 只缩不放（同 AppIconFetcher 口径）：原生够小直接原样转。失败返回 null，走 AsyncImage 兜底。 */
    private fun loadSync(context: Context, packageName: String, sizePx: Int): ImageBitmap? {
        return runCatching {
            val source = context.packageManager.getApplicationIcon(packageName)
            val intrinsic = maxOf(source.intrinsicWidth, source.intrinsicHeight)
            if (intrinsic <= 0) return null
            val side = minOf(sizePx, intrinsic)
            if (source is BitmapDrawable && side >= intrinsic) {
                return source.bitmap?.asImageBitmap()
            }
            source.setBounds(0, 0, side, side)
            val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            source.draw(Canvas(bitmap))
            bitmap.asImageBitmap()
        }.getOrNull()
    }
}
