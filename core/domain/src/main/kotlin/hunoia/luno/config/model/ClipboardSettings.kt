package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 剪贴板设置：记录开关与未固定条目上限。
 *
 * 与历史分开存（两个 DataStore 文件）：设置进备份，普通历史不进（见
 * `BackupDomain.CLIPBOARD`）。上限只约束未固定条目，固定项不设上限。
 */
@Serializable
@Keep
data class ClipboardSettings(
    /** 关闭后不再记录新内容（当前主剪贴项仍可读、可复制）。 */
    val recordEnabled: Boolean = true,
    /** 未固定条目上限；超出范围时由 [coerced] 收敛。 */
    val maxEntries: Int = ClipboardHistoryMaxEntries,
    /** 剪贴板悬浮窗：位置 / 缩放 / 透明度。 */
    val clipFloat: ClipFloatSettings = ClipFloatSettings(),
) {
    fun coerced(): ClipboardSettings {
        val clamped = maxEntries.coerceIn(ClipboardMaxEntriesRange.first, ClipboardMaxEntriesRange.last)
        val safeFloat = clipFloat.coerced()
        return if (clamped == maxEntries && safeFloat == clipFloat) {
            this
        } else {
            copy(maxEntries = clamped, clipFloat = safeFloat)
        }
    }
}
