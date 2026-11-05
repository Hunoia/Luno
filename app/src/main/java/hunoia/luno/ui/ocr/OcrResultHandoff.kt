package hunoia.luno.ui.ocr

import android.graphics.Bitmap

/**
 * 交给结果页的一份结果：OCR 文本、同一区域的 a11y 文本、以及裁剪图。
 *
 * 三者都可为空，但不能同时为空（调用方先判空）。结果页据此渲染
 * 「无障碍 / OCR」与「文本 / 图片」两组切换。
 * 命中文本节点时 [ocrText] 可为空，由 [ocrLoader] 在用户切到 OCR 来源时按需识别。
 */
data class OcrResultPayload(
    val ocrText: String = "",
    val a11yText: String? = null,
    /** 框选区域裁剪图；由结果页负责回收。 */
    val image: Bitmap? = null,
    /**
     * 按需 OCR：对给定位图识别文字。结果页切到 OCR 来源或重新识别选区时调用。
     * 返回 null / 空串表示识别失败或没有文字；入参位图由调用方持有与回收。
     */
    val ocrLoader: (suspend (Bitmap) -> String?)? = null,
    /**
     * 初始来源偏好：框选菜单点 OCR 时应先展示 OCR（即使还在懒加载），
     * 幽灵卡命中 a11y 文本时仍先展示无障碍。
     */
    val preferOcr: Boolean = false,
    /**
     * 初始形态偏好：有图片时先展示图片视图（懒加载 OCR 完成后自动切回文本）。
     * 剪贴板 OCR 动作使用，让用户先看到被识别的内容再等文本。
     */
    val preferImage: Boolean = false,
) {
    /** 有没有可用的 OCR 来源：已识别文本或可懒加载的识别入口。 */
    val hasOcrSource: Boolean
        get() = ocrText.isNotBlank() || ocrLoader != null

    val isEmpty: Boolean
        get() = ocrText.isBlank() && a11yText.isNullOrBlank() && image == null
}

/**
 * `:service` 进程内的 OCR 结果交接。
 *
 * 结果页 Activity 与识屏浮窗在同一进程，直接交接对象即可；不把大文本/位图塞进 Intent，
 * 避免 Binder 事务上限和多余序列化。只保留最后一次结果，进程死亡即丢失。
 */
object OcrResultHandoff {

    private data class Session(val id: Long, val payload: OcrResultPayload)

    private var nextId = 1L
    private var session: Session? = null

    @Synchronized
    fun put(payload: OcrResultPayload): Long {
        val id = nextId++
        session = Session(id, payload)
        return id
    }

    @Synchronized
    fun take(id: Long): OcrResultPayload? {
        val current = session ?: return null
        if (current.id != id) return null
        session = null
        return current.payload
    }

    @Synchronized
    fun discard(id: Long) {
        if (session?.id == id) {
            session?.payload?.image?.recycle()
            session = null
        }
    }
}
