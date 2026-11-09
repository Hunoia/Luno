package hunoia.luno.annotate.render

import android.content.Context
import hunoia.luno.annotate.R
import hunoia.luno.annotate.model.AnnotateState
import hunoia.luno.bridge.system.ImageOutput
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.clipboard.ClipboardCenter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 导出动作。两宿主共用这一份定义（此前浮层与图片编辑页各有一份同名枚举）。 */
enum class ExportKind { Save, Copy, Share }

/**
 * 导出全链路：按选区渲染 → 落图库 / 剪贴板 / 分享 → toast。
 *
 * 屏幕标注浮层（`AnnotateOverlay`）与图片编辑页（`ImageAnnotateActivity`）此前
 * 各有一份逐字重复的实现，必然漂移；收敛到这里，宿主只负责
 * 「提交未落盘文字 + busy 开关」。
 *
 * 内部自行切换调度器；返回是否成功（失败已 toast）。
 */
suspend fun Context.exportAnnotated(
    state: AnnotateState,
    base: BaseImage,
    kind: ExportKind,
): Boolean {
    val app = this
    val rendered = try {
        withContext(Dispatchers.Default) { AnnotateExporter.renderCropped(state, base) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: OutOfMemoryError) {
        // 大图导出可能在 createBitmap/saveLayer 处 OOM：按失败处理，不让进程崩掉。
        null
    } catch (e: Exception) {
        null
    }
    if (rendered == null) {
        showToast(R.string.annotate_export_failed)
        return false
    }
    val ok = withContext(Dispatchers.IO) {
        try {
            when (kind) {
                ExportKind.Save ->
                    ImageOutput.saveToGallery(app, rendered, "annotate") != null

                // 复制需要一个可被粘贴方读取的 URI：先落图库再写剪贴板。
                ExportKind.Copy ->
                    ImageOutput.saveToGallery(app, rendered, "annotate")
                        ?.let { uri -> ClipboardCenter.copyImage(uri, label = "annotate") } ?: false

                ExportKind.Share ->
                    ImageOutput.saveToCache(app, rendered, "annotate")
                        ?.let { uri -> ImageOutput.share(app, uri, "annotate") } ?: false
            }
        } finally {
            if (!rendered.isRecycled) rendered.recycle()
        }
    }
    when {
        !ok -> showToast(R.string.annotate_export_failed)
        kind == ExportKind.Save -> showToast(R.string.annotate_saved)
        kind == ExportKind.Copy -> showToast(R.string.annotate_copied)
    }
    return ok
}
