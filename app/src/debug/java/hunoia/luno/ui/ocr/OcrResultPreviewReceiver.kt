package hunoia.luno.ui.ocr

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * debug-only：广播把任意文本送进 OCR 结果页，真机验证排版 / 分词 / 选择。
 *
 * 真实入口要经过手势框选 + OCR，迭代 UI 时成本太高；这里只做“把文本交给同一个
 * Activity”，不复制 UI 逻辑，因此不构成第二条生产路径。
 *
 * 用法：
 * `adb shell am broadcast -a hunoia.luno.debug.OCR_PREVIEW --es text '...'`
 */
class OcrResultPreviewReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra("text")?.takeIf { it.isNotBlank() } ?: return
        val session = OcrResultHandoff.put(text)
        val launch = Intent(context, OcrResultActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(OcrResultActivity.EXTRA_SESSION, session)
        }
        runCatching { context.startActivity(launch) }
    }
}
