package hunoia.luno.quickaccess.icon

import android.content.Context
import androidx.collection.LruCache
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * 应用图标的 Coil 请求工厂（主进程页面 / 面板共用）。
 *
 * 请求对象按 (包名, 边长, userId) 复用：`ImageRequest` 不重写 equals，每次 new 一个都会
 * 让 `AsyncImage` 的 model 参数恒变，Compose 跳过失效后再逐帧重组就会闪。
 */
object AppIconRequests {

    private const val CAP = 128
    private val cache = LruCache<String, ImageRequest>(CAP)

    fun request(context: Context, packageName: String, sizePx: Int, userId: Int = 0): ImageRequest {
        val key = if (userId == 0) "$packageName@$sizePx" else "$packageName@$sizePx@$userId"
        cache.get(key)?.let { return it }
        val request = ImageRequest.Builder(context)
            .data(AppIconModel(packageName, sizePx, userId))
            .size(sizePx, sizePx)
            .crossfade(false)
            .build()
        cache.put(key, request)
        return request
    }
}
