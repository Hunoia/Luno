package hunoia.luno.quicklaunch.icon

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache

object IconResizeCache {

    /**
     * 字节上限而不是条目上限。
     *
     * 之前按条目数（200）淘汰，而这些 Drawable 来自 `PackageManager.getApplicationIcon`，
     * 是应用声明的原生尺寸：普通 192×192×4B ≈ 147KB，adaptive icon 更大。
     * 200 个常驻在 :service 进程里能到 ~30MB，而实际渲染只有面板格子大小（最大 80dp）。
     */
    private const val MAX_ICON_CACHE_BYTES = 12L * 1024 * 1024

    /** 无法从 Drawable 估出尺寸时（VectorDrawable 等）的兜底计费，防止 sizeOf 返回 0 导致条目数无界 */
    private const val FALLBACK_BYTES = 64 * 1024

    val iconCache: LruCache<String, Drawable> =
        object : LruCache<String, Drawable>(MAX_ICON_CACHE_BYTES.toInt()) {
            override fun sizeOf(key: String, value: Drawable): Int = estimateBytes(value)
        }

    private fun estimateBytes(drawable: Drawable): Int {
        (drawable as? BitmapDrawable)?.bitmap?.let { bmp ->
            val alloc = runCatching { bmp.allocationByteCount }.getOrDefault(bmp.byteCount)
            if (alloc > 0) return alloc
        }
        val w = drawable.intrinsicWidth
        val h = drawable.intrinsicHeight
        if (w > 0 && h > 0) return w * h * 4
        return FALLBACK_BYTES
    }
}
