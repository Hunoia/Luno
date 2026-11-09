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
        "internal.clipboardSegment",
        "internal.clipboardOcr",
    )

    override suspend fun execute(action: Action, context: ExecutorContext): ActionResult = when (action) {
        is Action.ImageEdit -> {
            val opened = context.surfaces.openImageAnnotate()
            if (opened) ActionResult.Success() else ActionResult.Failed(ActionFailure.ExecutionFailed)
        }
        is Action.ClipboardSegment -> executeClipboardSegment(context)
        is Action.ClipboardOcr -> executeClipboardOcr(context)
        else -> ActionResult.Failed(ActionFailure.Unsupported)
    }

    /**
     * 分词：只认剪贴板文本；空 / 图片 / 其他类型一律「剪贴板无文本」。
     * 文本交给结果页分词视图；拉起失败按执行失败收口。
     */
    private suspend fun executeClipboardSegment(context: ExecutorContext): ActionResult {
        val content = ClipboardCenter.readCurrent()
        val text = (content as? ClipboardContent.Text)?.text
        if (text.isNullOrBlank()) {
            return ActionResult.Failed(
                reason = ActionFailure.InvalidParameter,
                userMessage = context.environment.appContext.getString(R.string.clipboard_no_text),
            )
        }
        val opened = context.surfaces.showOcrSegmentText(text)
        return if (opened) ActionResult.Success()
        else ActionResult.Failed(ActionFailure.ExecutionFailed)
    }

    /**
     * 剪贴板 OCR：只认剪贴板图片；空 / 文本 / 其他类型一律「剪贴板无图片」。
     * 模型检查、解码与懒加载 OCR 在 `:app`（SurfaceHost 实现），失败带因原样透传。
     */
    private suspend fun executeClipboardOcr(context: ExecutorContext): ActionResult {
        val content = ClipboardCenter.readCurrent()
        if (content !is ClipboardContent.Image) {
            return ActionResult.Failed(
                reason = ActionFailure.InvalidParameter,
                userMessage = context.environment.appContext.getString(R.string.clipboard_no_image),
            )
        }
        return context.surfaces.showClipboardOcr(content.uri, content.mimeType)
    }
}
