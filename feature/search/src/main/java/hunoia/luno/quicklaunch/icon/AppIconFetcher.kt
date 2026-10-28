package hunoia.luno.quicklaunch.icon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options

/**
 * 按目标像素栅格化应用图标。
 * **只缩不放**：`PackageManager.getApplicationIcon` 的原生 Drawable 通常已是 192×192（约 147KB），
 * 而网格格子在 density 2.75 上约 253px；盲目放大会比现状更差，故目标取
 * `min(渲染边长, 原生固有边长)`，原生够大时直接原样返回。
 * 栅格化沿用 `setBounds` + `Canvas.draw`（`AdaptiveIconDrawable` 不是 `BitmapDrawable`，
 * 不能靠 `bitmap` 直接取）。不做 in-flight 去重：Coil 的 `EngineInterceptor` 已按缓存键维护 in-flight 表。
 */
class AppIconFetcher(
    private val context: Context,
    private val packageName: String,
    private val targetPx: Int,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val source = runCatching {
            context.packageManager.getApplicationIcon(packageName)
        }.getOrNull() ?: return null

        return runCatching {
            val intrinsic = maxOf(source.intrinsicWidth, source.intrinsicHeight)
            if (intrinsic <= 0) return null

            val side = if (targetPx > 0) minOf(targetPx, intrinsic) else intrinsic
            source.setBounds(0, 0, side, side)

            if (side >= intrinsic) {
                DrawableResult(source, isSampled = false, dataSource = DataSource.DISK)
            } else {
                val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
                source.draw(Canvas(bitmap))
                DrawableResult(
                    drawable = BitmapDrawable(context.resources, bitmap),
                    isSampled = true,
                    dataSource = DataSource.DISK,
                )
            }
        }.getOrNull()
    }
}

class AppIconFetcherFactory : Fetcher.Factory<AppIconModel> {
    override fun create(data: AppIconModel, options: Options, imageLoader: ImageLoader): Fetcher =
        AppIconFetcher(options.context, data.packageName, data.sizePx)
}