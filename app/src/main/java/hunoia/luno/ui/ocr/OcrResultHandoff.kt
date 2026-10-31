package hunoia.luno.ui.ocr

import android.graphics.Bitmap

/**
 * 交给结果页的一份结果：OCR 文本、同一区域的 a11y 文本、以及裁剪图。
 *
 * 三者都可为空，但不能同时为空（调用方先判空）。结果页据此渲染
 * 「无障碍 / OCR」与「文本 / 图片」两组切换。
 */
data class OcrResultPayload(
    val ocrText: String = "",
    val a11yText: String? = null,
    /** 框选区域裁剪图；由结果页负责回收。 */
    val image: Bitmap? = null,
) {
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
