package hunoia.luno.shizuku

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import hunoia.luno.core.LunoLog as Log
import hunoia.luno.core.LOG_TAG
import hunoia.luno.bridge.ui.assertNotMainThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shizuku `screencap -p` 截屏：不受无障碍截屏约 1s 的限流影响。
 *
 * 失败（未就绪 / 命令不可用 / 解码失败）统一返回 null，由调用方回退到
 * [hunoia.luno.bridge.system.ScreenScreenshot]；这里不弹提示、不重试。
 */
object ShizukuScreenshot {

    suspend fun capture(): Bitmap? = withContext(Dispatchers.IO) {
        assertNotMainThread("shizuku.screenshot")
        if (!ShizukuManager.currentStatus().isReady) return@withContext null
        when (val opened = ShizukuShellStream.openArgv("screencap", "-p")) {
            is ShizukuShellStream.OpenResult.Failure -> {
                Log.w(LOG_TAG, "shizuku screenshot: open failed: ${opened.reason}")
                null
            }

            is ShizukuShellStream.OpenResult.Success -> opened.session.use { session ->
                runCatching {
                    val bytes = session.readBytes()
                    if (bytes.isEmpty()) {
                        Log.w(LOG_TAG, "shizuku screenshot: empty stdout")
                        return@use null
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: run {
                            Log.w(LOG_TAG, "shizuku screenshot: decode failed (${bytes.size} bytes)")
                            null
                        }
                }.onFailure {
                    Log.w(LOG_TAG, "shizuku screenshot failed: ${it.message}")
                }.getOrNull()
            }
        }
    }
}
