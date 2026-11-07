package hunoia.luno.domain.clipboard

import android.content.Context
import android.net.Uri
import hunoia.luno.clipboard.ClipboardCenter
import hunoia.luno.core.domain.ActionResult
import hunoia.luno.domain.ocr.ClipboardOcrLauncher
import hunoia.luno.facade.ClipboardFacade

/** `ClipboardFacade` 的默认实现：只做委托，规则全在 `ClipboardCenter` / OCR 启动器里。 */
object ClipboardUseCases : ClipboardFacade {

    override suspend fun copyText(text: String, label: String, sensitive: Boolean): Boolean =
        ClipboardCenter.copy(text, sensitive = sensitive, label = label)

    override suspend fun ocrImage(context: Context, uri: Uri, mimeType: String): Boolean =
        ClipboardOcrLauncher.launchImage(context, uri, mimeType) is ActionResult.Success
}
