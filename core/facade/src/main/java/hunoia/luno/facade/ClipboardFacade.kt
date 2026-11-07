package hunoia.luno.facade

import android.content.Context
import android.net.Uri

/**
 * 剪贴板页面需要的 `:app` 侧能力（UI 只认这份契约）。
 *
 * 页面本身在 `:feature:clipboard`，读写历史走 `ClipboardCenter`；只有两件事必须留在
 * 组装根：平台剪贴板写入的敏感标记口径、以及 OCR 结果页（页面与引擎都在 `:app`）。
 */
interface ClipboardFacade {

    /** 写文本（[sensitive] = true 时打系统敏感标记）；返回是否写入成功。 */
    suspend fun copyText(text: String, label: String, sensitive: Boolean = false): Boolean

    /**
     * 对图片条目拉起 OCR 结果页（引擎在 `:app` 侧懒加载，结果页后台识别）。
     * 返回是否成功拉起；模型未就绪 / 解码失败返回 false，页面给轻提示。
     */
    suspend fun ocrImage(context: Context, uri: Uri, mimeType: String): Boolean
}
