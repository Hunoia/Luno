package hunoia.luno.action.executor

import hunoia.luno.actions.R
import hunoia.luno.action.execution.ActionExecutor
import hunoia.luno.action.execution.ExecutorContext
import hunoia.luno.action.model.Action
import hunoia.luno.bridge.system.ClipboardContent
import hunoia.luno.clipboard.ClipboardCenter
import hunoia.luno.core.domain.ActionFailure
import hunoia.luno.core.domain.ActionResult

/**
 * 图片标注 / 剪贴板分词 / 剪贴板 OCR：选择器、编辑 Activity 与 OCR 结果页都在 `:app`，
 * 这里只转发入口。剪贴板读取走 [ClipboardCenter]（Shizuku 优先，`:service` 进程无窗口
 * 焦点、应用内直读受 Android 10+ 限制，Shizuku 是后台可靠路径），页面拉起经
 * `ExecutorContext` 端口。
 */
class MiscActionExecutors : ActionExecutor {

    override val supportedTypes: Set<String> = setOf(
        "image.edit",
        "image.recognize",
        "image.recognizeGallery",
        "internal.clipboardRecognize",
    )

    override suspend fun execute(action: Action, context: ExecutorContext): ActionResult = when (action) {
        is Action.ImageEdit -> {
            val opened = context.surfaces.openImageAnnotate()
            if (opened) ActionResult.Success() else ActionResult.Failed(ActionFailure.ExecutionFailed)
        }
        is Action.PhotoRecognize -> {
            val opened = context.surfaces.openPhotoRecognize()
            if (opened) ActionResult.Success() else ActionResult.Failed(ActionFailure.ExecutionFailed)
        }
        is Action.ImageRecognizeGallery -> {
            val opened = context.surfaces.openGalleryRecognize()
            if (opened) ActionResult.Success() else ActionResult.Failed(ActionFailure.ExecutionFailed)
        }
        is Action.ClipboardRecognize -> executeClipboardRecognize(context)
        else -> ActionResult.Failed(ActionFailure.Unsupported)
    }

    /**
     * 识别剪贴板：文本送分词视图、图片送 OCR 结果页；空 / 其他类型一律失败。
     * 文本交结果页分词视图；图片经 SurfaceHost 检查模型、解码、懒加载识别。
     */
    private suspend fun executeClipboardRecognize(context: ExecutorContext): ActionResult {
        val content = ClipboardCenter.read()
        return when (content) {
            is ClipboardContent.Text -> {
                if (content.text.isBlank()) {
                    ActionResult.Failed(
                        reason = ActionFailure.InvalidParameter,
                        userMessage = context.environment.appContext.getString(R.string.clipboard_no_text_or_image),
                    )
                } else {
                    val opened = context.surfaces.showOcrSegmentText(content.text)
                    if (opened) ActionResult.Success() else ActionResult.Failed(ActionFailure.ExecutionFailed)
                }
            }
            is ClipboardContent.Image ->
                context.surfaces.showClipboardOcr(content.uri, content.mimeType)
            else -> ActionResult.Failed(
                reason = ActionFailure.InvalidParameter,
                userMessage = context.environment.appContext.getString(R.string.clipboard_no_text_or_image),
            )
        }
    }
}
