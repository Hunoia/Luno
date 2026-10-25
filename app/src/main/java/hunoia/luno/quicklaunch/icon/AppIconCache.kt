package hunoia.luno.quicklaunch.icon

import android.graphics.drawable.Drawable
import androidx.collection.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用/动作图标的进程级 LruCache：取图标是跨进程 PackageManager 查询（几 ms~几十 ms），
 * 面板/列表首帧不该在主线程组合里串行付 N 次。与 [SearchIconCache]（搜索行的
 * ImageBitmap 缓存，首帧同步命中）互补，这里存 Drawable，供动作面板/网格/设置列表共用。
 */
object AppIconCache {
    // 上限 128（一屏面板/列表的量级）；LruCache 自带 LRU 淘汰与线程安全。
    private const val CAP = 128
    private val cache = LruCache<String, Drawable>(CAP)

    /** 取副本而非共享实例：调用方各自交给 Coil/自绘，互不干扰 bounds 等可变状态。 */
    fun get(key: String): Drawable? =
        cache.get(key)?.let { it.constantState?.newDrawable() ?: it }

    fun put(key: String, drawable: Drawable) {
        cache.put(key, drawable)
    }
}

/**
 * 组合期先吃缓存；未命中异步补载（IO）后触发一次重组 —— 主线程组合不再同步做 Binder。
 * 首帧未就绪时返回 null，调用方按各自占位回落（与原先「取不到图标返回 null」同构）。
 */
@Composable
fun rememberAppDrawable(key: String, load: () -> Drawable?): Drawable? {
    AppIconCache.get(key)?.let { return it }
    var loaded by remember(key) { mutableStateOf<Drawable?>(null) }
    LaunchedEffect(key) {
        val result = withContext(Dispatchers.IO) { runCatching(load).getOrNull() }
        if (result != null) {
            AppIconCache.put(key, result)
            loaded = result
        }
    }
    return loaded
}

/** 应用标签的进程级缓存，与 [AppIconCache] 同一套 Lru 策略（跨进程 PM 查询，量级更小）。 */
private object AppLabelCache {
    private const val CAP = 128
    private val cache = LruCache<String, String>(CAP)

    fun get(key: String): String? = cache.get(key)

    fun put(key: String, label: String) {
        cache.put(key, label)
    }
}

/**
 * [rememberAppDrawable] 的字符串版：网格面板最近应用磁贴的无障碍名用应用标签，
 * 取不到时回落 [fallback]（首帧先吃缓存，未命中异步补载）。
 */
@Composable
fun rememberAppLabel(key: String, fallback: String, load: () -> String?): String {
    AppLabelCache.get(key)?.let { return it }
    var label by remember(key) { mutableStateOf(fallback) }
    LaunchedEffect(key) {
        val result = withContext(Dispatchers.IO) { runCatching(load).getOrNull() }
        if (!result.isNullOrBlank()) {
            AppLabelCache.put(key, result)
            label = result
        }
    }
    return label
}
