package hunoia.luno.bridge.system

import android.net.Uri
import android.os.SystemClock

/**
 * 记录 Luno 自己写入剪贴板的内容，短时间窗内忽略对应的剪贴板回调。
 *
 * 新 ROM 后台读剪贴板依赖 Shizuku 轮询，写入后下一次轮询仍会读到同一条内容；
 * 若不做标记，复制动作本身会立刻弹出一条「剪贴板」气泡。这里只做内存标记，
 * 不持久化，也不阻塞用户随后手动复制同样内容（窗口外即可再次入库）。
 */
object SelfWriteGuard {

    private const val WINDOW_MS = 2_000L

    private data class Mark(
        val key: String,
        val atElapsedMs: Long,
    )

    private val marks = ArrayDeque<Mark>()

    fun markText(text: String) = mark("text:$text")

    fun markImage(uri: Uri) = mark("image:$uri")

    fun matches(content: ClipboardContent): Boolean = synchronized(marks) {
        purgeExpired()
        val key = when (content) {
            is ClipboardContent.Text -> "text:${content.text}"
            is ClipboardContent.Image -> "image:${content.uri}"
        }
        marks.any { it.key == key }
    }

    private fun mark(key: String) = synchronized(marks) {
        purgeExpired()
        marks.addLast(Mark(key, SystemClock.elapsedRealtime()))
    }

    private fun purgeExpired() {
        val now = SystemClock.elapsedRealtime()
        while (marks.isNotEmpty() && now - marks.first().atElapsedMs > WINDOW_MS) {
            marks.removeFirst()
        }
    }
}
