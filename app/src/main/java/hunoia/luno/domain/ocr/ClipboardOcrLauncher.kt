package hunoia.luno.domain.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import hunoia.luno.R
import hunoia.luno.capability.ocr.OcrModelStatus
import hunoia.luno.capability.ocr.OcrModelStore
import hunoia.luno.capability.ocr.OcrText
import hunoia.luno.config.model.OcrScript
import hunoia.luno.core.domain.ActionFailure
import hunoia.luno.core.domain.ActionResult
import hunoia.luno.ui.ocr.OcrResultActivity
import hunoia.luno.ui.ocr.OcrResultPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 剪贴板分词 / OCR 的 `:app` 侧实现：`GestureCoordinator.executeSurfaces` 与
 * `SurfaceActionRuntime.surfaces` 共用，避免两处复制模型检查 / 解码 / loader 逻辑。
 *
 * 与识屏路径不同：这里不持有 `ScreenPickOverlay` 的 a11y 取词，只处理剪贴板内容。
 * 结果页在 `:service` 进程，与本 helper 同进程，经 `OcrResultHandoff` 直接交接。
 */
object ClipboardOcrLauncher {

    /** 解码长边上限：够看清内容，又不把几十 MB 位图塞进结果页（与识屏裁剪同口径）。 */
    private const val MAX_DECODE_PX = 1600

    /**
     * 分词：把文本送进结果页分词视图。调用方已校验非空。
     * 返回是否成功弹起。
     */
    fun launchSegmentText(context: Context, text: String): Boolean =
        OcrResultActivity.start(context, OcrResultPayload(ocrText = text))

    /**
     * 剪贴板 OCR：检查模型 → 解码 → 懒加载 OCR → 拉起结果页。
     *
     * 模型未就绪返回带因失败（引导去识屏设置导入）；解码失败 / 拉起失败按执行失败收口。
     * 识别本身在结果页后台跑（[OcrResultPayload.ocrLoader]），不阻塞开页。
     * 模型检查与图片解码是文件 IO，切 `Dispatchers.IO` 不占主线程。
     */
    suspend fun launchImage(context: Context, uri: Uri, mimeType: String): ActionResult {
        val prepared = withContext(Dispatchers.IO) {
            val status = OcrModelStore.status(context)
            val bitmap = if (status is OcrModelStatus.Ready) decode(context, uri) else null
            status to bitmap
        }
        val (status, bitmap) = prepared
        if (status !is OcrModelStatus.Ready) {
            return ActionResult.Failed(
                reason = ActionFailure.ExecutionFailed,
                userMessage = context.getString(R.string.ocr_model_missing_hint),
            )
        }
        if (bitmap == null) {
            return ActionResult.Failed(
                reason = ActionFailure.ExecutionFailed,
                userMessage = context.getString(R.string.ocr_result_image_decode_failed),
            )
        }
        val engine = OcrEngineHolder.get(context)
        val loader: suspend (Bitmap) -> String? = { input ->
            val source = withContext(Dispatchers.IO) {
                runCatching {
                    if (input.isRecycled) null else input.copy(Bitmap.Config.ARGB_8888, false)
                }.getOrNull()
            }
            if (source == null) {
                null
            } else {
                try {
                    val result = engine.recognize(source, listOf(OcrScript.Chinese))
                    OcrText.join(result.blocks).ifBlank { null }
                } catch (_: Throwable) {
                    null
                } finally {
                    if (!source.isRecycled) source.recycle()
                }
            }
        }
        // 启动失败时 OcrResultActivity.start 已经 OcrResultHandoff.discard 回收图片，
        // 这里不再重复回收；返回失败由动作三态反馈提示。
        val opened = OcrResultActivity.start(
            context,
            OcrResultPayload(
                image = bitmap,
                ocrLoader = loader,
                preferOcr = true,
                preferImage = true,
            ),
        )
        return if (opened) {
            ActionResult.Success()
        } else {
            ActionResult.Failed(ActionFailure.ExecutionFailed)
        }
    }

    /** 按长边上限解码剪贴板图片；失败返回 null（URI 失效 / 解码异常）。 */
    private fun decode(context: Context, uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > MAX_DECODE_PX) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }.getOrNull()
}
