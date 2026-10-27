package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 剪贴板历史（纯文本，最新在最前）。
 *
 * 设备本地派生数据：不进备份、不支持跨机恢复（`content://` 之类的 URI 出机即失效，
 * 也没有恢复的意义）。图片与敏感文本只出现在「当前主剪贴项」视图，不落历史。
 */
@Serializable
@Keep
data class ClipboardHistory(
    val entries: List<ClipboardHistoryEntry> = emptyList(),
)

@Serializable
@Keep
data class ClipboardHistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    /** 记录时刻（epoch millis）。仅用于「重复内容刷新到最前」，不直接展示。 */
    val time: Long = 0L,
)

/** 历史条数上限：纯文本，单条也小；再多用户也翻不到。 */
const val ClipboardHistoryMaxEntries = 50

/**
 * 纯函数记录一条文本：
 * - 空白不记；
 * - 已在历史里的相同文本先摘掉再置顶（不产生重复行），时间刷新为最新；
 * - 超出 [maxEntries] 从尾部裁掉。
 */
fun ClipboardHistory.record(
    text: String,
    now: Long,
    maxEntries: Int = ClipboardHistoryMaxEntries,
): ClipboardHistory {
    if (text.isBlank()) return this
    val limit = maxEntries.coerceAtLeast(1)
    val existing = entries.firstOrNull { it.text == text }
    val rest = entries.filter { it.text != text }
    val head = existing?.copy(time = now) ?: ClipboardHistoryEntry(text = text, time = now)
    val merged = listOf(head) + rest
    return copy(entries = if (merged.size > limit) merged.take(limit) else merged)
}

/** 删除单条；不存在时原样返回（幂等）。 */
fun ClipboardHistory.remove(id: String): ClipboardHistory =
    if (entries.none { it.id == id }) this else copy(entries = entries.filterNot { it.id == id })

/** 清空全部历史；本来就空时原样返回（避免无意义写盘）。 */
fun ClipboardHistory.cleared(): ClipboardHistory =
    if (entries.isEmpty()) this else ClipboardHistory()
