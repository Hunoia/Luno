package hunoia.luno.ui.annotate

import android.graphics.Bitmap

/**
 * `:service` 进程内的图片编辑交接（与 [hunoia.luno.ui.ocr.OcrResultHandoff] 同款）。
 *
 * 识屏框选 / OCR 结果页已经在内存里拿到位图，直接交接对象，
 * 不走 Intent（Binder 事务上限）。只保留最后一次；进程死亡即丢失。
 */
object ImageAnnotateHandoff {

    private data class Session(val id: Long, val bitmap: Bitmap)

    private var nextId = 1L
    private var session: Session? = null

    @Synchronized
    fun put(bitmap: Bitmap): Long {
        val id = nextId++
        session = Session(id, bitmap)
        return id
    }

    @Synchronized
    fun take(id: Long): Bitmap? {
        val current = session ?: return null
        if (current.id != id || id < 0) return null
        session = null
        return current.bitmap
    }

    /** 已被消费但没人接走时释放，避免交接对象泄漏大位图。 */
    @Synchronized
    fun discard(id: Long) {
        val current = session ?: return
        if (current.id == id) {
            session = null
            if (!current.bitmap.isRecycled) current.bitmap.recycle()
        }
    }
}
