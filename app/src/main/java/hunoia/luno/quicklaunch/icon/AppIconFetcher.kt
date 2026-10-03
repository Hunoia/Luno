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
 *
 * **只缩不放**：`PackageManager.getApplicationIcon` 返回的原生 Drawable 通常已是
 * 192×192（约 147KB），而全屏面板的网格格子在 density 2.75 上约 253px。若照
 * 格子尺寸盲目放大，单张会涨到 256KB —— 比现状更差。所以目标取
 * `min(渲染边长, 原生固有边长)`，原生够大时直接原样返回。
 *
 * 栅格化沿用 `ShortcutInfoExt` 里已有的 `setBounds` + `Canvas.draw` 做法
 * （`AdaptiveIconDrawable` 不是 `BitmapDrawable`，不能靠 `bitmap` 直接取）。
 *
 * 不做 in-flight 去重：Coil 的 `EngineInterceptor` 已按缓存键维护 in-flight 表，
 * 同键并发请求会自动合并到一次解码。
 */
internal class AppIconFetcher(
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

internal class AppIconFetcherFactory : Fetcher.Factory<AppIconModel> {
    override fun create(data: AppIconModel, options: Options, imageLoader: ImageLoader): Fetcher =
        AppIconFetcher(options.context, data.packageName, data.sizePx)
}