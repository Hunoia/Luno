package hunoia.luno.ui.ocr

/**
 * `:service` 进程内的 OCR 结果交接。
 *
 * 结果页 Activity 与识屏浮窗在同一进程，直接交接文本即可；不把大文本塞进 Intent，
 * 避免 Binder 事务上限和多余序列化。只保留最后一次结果，进程死亡即丢失。
 */
object OcrResultHandoff {

    private data class Session(val id: Long, val text: String)

    private var nextId = 1L
    private var session: Session? = null

    @Synchronized
    fun put(text: String): Long {
        val id = nextId++
        session = Session(id, text)
        return id
    }

    @Synchronized
    fun take(id: Long): String? {
        val current = session ?: return null
        if (current.id != id) return null
        session = null
        return current.text
    }

    @Synchronized
    fun discard(id: Long) {
        if (session?.id == id) session = null
    }
}
