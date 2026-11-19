package hunoia.luno.ui.icon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import hunoia.luno.bridge.system.AppIcons

/**
 * 搜索行图标的同步缓存：composition 里同步读，首帧即有图。
 * AsyncImage 配 crossfade(false) 仍有一帧异步空隙（内存命中也要跳一帧），结果列表每次挂载
 * 十几行 App 图标 = 整表闪一次；只有 App 行/快捷方式行走这条路，其它行全是同步矢量。
 * 填充只许在后台线程（搜索 flow 发射结果前），composition 只读。
 */
object SearchIconCache {
    /** 缓存键：包名 + 用户 + 边长。分身与主应用包名相同，必须带 userId。 */
    data class Key(val packageName: String, val userId: Int = 0)

    /**
     * 上限按**字节**而不是条数：24dp 行图标约 20KB/张（8MB 够缓存约 400 张），
     * 但同一份缓存也被悬浮球 / 链接选择页以更大的边长写入，按条数上限会随边长线性膨胀
     * （256 条 × 48dp 图标可达 20MB+）。定死 8MB 后无论谁写都不会把进程堆顶穿。
     */
    private const val MAX_BYTES = 8 * 1024 * 1024

    /** accessOrder = true：迭代顺序即 LRU 序，队首最早使用。 */
    private val lock = Any()
    private val cache = LinkedHashMap<String, ImageBitmap>(64, 0.75f, true)
    private var bytes = 0

    fun get(packageName: String, sizePx: Int, userId: Int = 0): ImageBitmap? =
        synchronized(lock) { cache[keyOf(Key(packageName, userId), sizePx)] }

    private fun cachePut(key: String, bitmap: ImageBitmap) {
        synchronized(lock) {
            cache.put(key, bitmap)?.let { bytes -= byteSize(it) }
            bytes += byteSize(bitmap)
            val iterator = cache.entries.iterator()
            // 只淘汰比刚放入的这张更早使用的；单张超预算时保留它（否则等于缓存全空）。
            while (bytes > MAX_BYTES && iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.key == key) continue
                bytes -= byteSize(entry.value)
                iterator.remove()
            }
        }
    }

    /** ARGB_8888：宽 × 高 × 4 字节。 */
    private fun byteSize(bitmap: ImageBitmap): Int = bitmap.width * bitmap.height * 4

    /** 内存吃紧时丢弃图标缓存；下次搜索按需重新解码。 */
    fun clear() {
        synchronized(lock) {
            cache.clear()
            bytes = 0
        }
    }

    /**
     * 确保这些应用的图标在缓存里（阻塞当前线程，只能后台调）。
     * 已缓存的直接跳过，所以每次搜索的开销≈0，只有新包才解码。
     */
    fun ensureAll(context: Context, apps: Collection<Key>, sizePx: Int) {
        if (apps.isEmpty() || sizePx <= 0) return
        val appCtx = context.applicationContext ?: context
        for (app in apps) {
            val key = keyOf(app, sizePx)
            if (get(app.packageName, sizePx, app.userId) != null) continue
            loadSync(appCtx, app, sizePx)?.let { bmp -> cachePut(key, bmp) }
        }
    }

    private fun keyOf(app: Key, sizePx: Int) =
        if (app.userId == 0) "${app.packageName}@$sizePx" else "${app.packageName}@$sizePx@${app.userId}"

    /** 只缩不放（同 AppIconFetcher 口径）：原生够小直接原样转。失败返回 null，走 AsyncImage 兜底。 */
    private fun loadSync(context: Context, app: Key, sizePx: Int): ImageBitmap? {
        return runCatching {
            val source = AppIcons.drawable(context, app.packageName, userId = app.userId) ?: return null
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
