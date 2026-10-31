package hunoia.luno.bridge.system

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * 位图落地 / 分享的唯一出口：识屏拾取与 OCR 结果页共用。
 *
 * 之前这套逻辑只长在 `ScreenPickOverlay` 里；结果页也要「复制/分享/保存图片」，
 * 第二份实现必然漂移（图库路径、FileProvider authority、clipData 授权），所以上收到 bridge。
 */
object ImageOutput {

    private const val Tag = "ImageOutput"

    /** 缓存目录保留的分享图数量。 */
    private const val CacheKeepFiles = 5

    /**
     * 写入系统图库（Pictures/Luno），返回 content URI。
     * 用 MediaStore 而非 FileProvider：接收方/粘贴方按普通图片（带 MIME、可被系统预览）处理。
     */
    fun saveToGallery(context: Context, bitmap: Bitmap, prefix: String = "luno"): Uri? =
        runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "${prefix}_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/Luno",
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                Log.w(Tag, "save image: MediaStore insert failed")
                return@runCatching null
            }
            val written = runCatching {
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } ?: false
            }.getOrDefault(false)
            if (!written) {
                Log.w(Tag, "save image: compress/write failed")
                runCatching { resolver.delete(uri, null, null) }
                return@runCatching null
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        }.onFailure {
            Log.e(Tag, "save image failed: ${it.message}")
        }.getOrNull()

    /** 写入缓存目录，经 FileProvider 供分享；顺手清理旧文件，不污染图库。 */
    fun saveToCache(
        context: Context,
        bitmap: Bitmap,
        prefix: String = "luno",
        dirName: String = "screen_pick",
    ): Uri? = runCatching {
        val dir = File(context.cacheDir, dirName).apply { mkdirs() }
        cleanCache(dir)
        val file = File(dir, "${prefix}_${System.currentTimeMillis()}.png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.onFailure {
        Log.e(Tag, "save cache image failed: ${it.message}")
    }.getOrNull()

    /**
     * 系统分享图片。
     *
     * 必须同时带 clipData：只给 EXTRA_STREAM 时部分应用拿不到读取授权，
     * 会把图片当普通文件/附件显示。
     */
    fun share(context: Context, uri: Uri, label: String = "luno"): Boolean {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, label, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(share, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching { context.startActivity(chooser) }
            .onFailure { Log.w(Tag, "share chooser failed: ${it.message}") }
            .isSuccess
    }

    /** 清理分享图缓存，只保留最近 [keep] 张。 */
    fun cleanCache(
        dir: File,
        keep: Int = CacheKeepFiles,
    ) {
        runCatching {
            if (!dir.isDirectory) return
            dir.listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?.drop(keep)
                ?.forEach { it.delete() }
        }
    }
}
