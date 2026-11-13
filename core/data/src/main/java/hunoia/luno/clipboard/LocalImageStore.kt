package hunoia.luno.clipboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID
import hunoia.luno.config.model.ClipboardImageMaxBytes
import hunoia.luno.config.model.ClipboardImageMaxPx
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.LunoLog as Log
import hunoia.luno.shizuku.ShizukuClipboard

/**
 * 剪贴板图片副本的唯一实现：`content://` URI 会随来源应用失效，历史要留图就必须落成本地文件。
 *
 * 后台捕获时 `ClipData` 由 shell uid 读出，URI 读授权落在 shell 侧 —— 直读失败时
 * 走 [ShizukuClipboard.readUriBytes] 取字节。拿不到字节就放弃这张图（不记半截条目）。
 */
internal object LocalImageStore {

    fun dir(context: Context, dirName: String): File =
        File(context.filesDir, dirName).apply { mkdirs() }

    /**
     * 从 URI 读一份副本并按长边上限重新压缩，返回文件名。
     *
     * [allowShizuku] 只在后台捕获剪贴板时为 true：那时 URI 授权在 shell 侧；
     * 分享入口直接读 contentResolver 即可。
     */
    suspend fun saveUri(
        context: Context,
        dirName: String,
        uri: Uri,
        allowShizuku: Boolean,
    ): String? {
        val bytes = readBytes(context, uri, allowShizuku) ?: return null
        val bitmap = decode(bytes) ?: return null
        return try {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            write(context, dirName, out.toByteArray())
        } catch (t: Throwable) {
            Log.w(LOG_TAG, "clipboard image save failed: ${t.message}")
            null
        } finally {
            bitmap.recycle()
        }
    }

    fun file(context: Context, dirName: String, name: String): File? =
        name.takeIf { it.isNotBlank() }
            ?.let { File(dir(context, dirName), it) }
            ?.takeIf { it.isFile }

    /** 读回一份副本并解码（写回剪贴板 / OCR 用）；文件缺失或解码失败返回 null。 */
    fun load(context: Context, dirName: String, name: String): Bitmap? =
        file(context, dirName, name)?.let { file ->
            runCatching { decode(file.readBytes()) }.getOrNull()
        }

    fun delete(context: Context, dirName: String, name: String) {
        runCatching { file(context, dirName, name)?.delete() }
    }

    /** 清掉历史里已不存在的副本（条目删除 / 裁剪 / 清空之后调用）。 */
    fun prune(context: Context, dirName: String, keep: Set<String>) {
        runCatching {
            dir(context, dirName).listFiles()?.forEach { file ->
                if (file.name !in keep) file.delete()
            }
        }
    }

    private fun write(context: Context, dirName: String, bytes: ByteArray): String {
        val name = "${UUID.randomUUID()}.jpg"
        File(dir(context, dirName), name).writeBytes(bytes)
        return name
    }

    private suspend fun readBytes(context: Context, uri: Uri, allowShizuku: Boolean): ByteArray? {
        runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
            ?.use { it.readLimited(ClipboardImageMaxBytes.toInt()) }
            ?.let { return it }
        if (!allowShizuku) return null
        // 后台捕获：URI 授权在 shell 侧，只能让 shell 把字节送回来。
        return runCatching {
            ShizukuClipboard.readUriBytes(uri, ClipboardImageMaxBytes.toInt())
        }.getOrNull()
    }

    private fun InputStream.readLimited(max: Int): ByteArray? = use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (out.size() + read > max) return null
            out.write(buffer, 0, read)
        }
        out.toByteArray().takeIf { it.isNotEmpty() }
    }

    /** 按长边上限采样解码：只需要预览与 OCR，不解满分辨率。 */
    private fun decode(bytes: ByteArray): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > ClipboardImageMaxPx) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }.getOrNull()
}
