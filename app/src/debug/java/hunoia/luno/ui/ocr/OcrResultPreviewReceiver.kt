package hunoia.luno.ui.ocr

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.delay

/**
 * debug-only：广播把任意文本送进 OCR 结果页，真机验证排版 / 分词 / 选择。
 *
 * 真实入口要经过手势框选 + OCR，迭代 UI 时成本太高；这里只做“把文本交给同一个
 * Activity”，不复制 UI 逻辑，因此不构成第二条生产路径。
 *
 * 用法：
 * - 只有 OCR 文本：`adb shell am broadcast -a hunoia.luno.debug.OCR_PREVIEW --es text '...'`
 * - 双来源：加 `--es a11y '...'`，默认展示无障碍文本（与长按命中文本节点一致）。
 * - 懒加载 OCR：再加 `--ez lazy true`，[OcrResultPayload.ocrText] 留空，
 *   切到「OCR」分段时才走 [OcrResultPayload.ocrLoader]（模拟真实按需识别）。
 */
class OcrResultPreviewReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra("text")?.takeIf { it.isNotBlank() }
        val a11y = intent.getStringExtra("a11y")?.takeIf { it.isNotBlank() }
        if (text == null && a11y == null) return
        val lazy = intent.getBooleanExtra("lazy", false)
        val payload = OcrResultPayload(
            ocrText = if (lazy) "" else text.orEmpty(),
            a11yText = a11y,
            ocrLoader = if (lazy && text != null) {
                {
                    // 留一段等待，肉眼确认加载态没有阻塞无障碍文本。
                    delay(600)
                    text
                }
            } else {
                null
            },
        )
        val session = OcrResultHandoff.put(payload)
        val launch = Intent(context, OcrResultActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(OcrResultActivity.EXTRA_SESSION, session)
        }
        runCatching { context.startActivity(launch) }
    }
}
