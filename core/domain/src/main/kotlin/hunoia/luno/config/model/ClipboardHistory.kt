package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 剪贴板条目：文本或图片。
 *
 * 设备本地派生数据：普通历史不进备份；**固定项**随 `BackupDomain.CLIPBOARD` 备份
 * （用户显式动作，语义上等同于「我要留着」）。图片存应用私有目录副本，`imagePath`
 * 只是文件名，不是绝对路径（备份、迁移目录都不会把它变坏）。
 */
@Serializable
@Keep
data class ClipboardEntry(
    val id: String = UUID.randomUUID().toString(),
    val kind: ClipKind = ClipKind.TEXT,
    /** 文本内容（kind = TEXT）；敏感条目由 [sensitive] 标记，展示侧负责遮蔽。 */
    val text: String = "",
    /** 图片文件名（kind = IMAGE），落在 [ClipboardImageDirName] 目录下。 */
    val imagePath: String? = null,
    val mime: String? = null,
    /** 固定项：不参与条数裁剪，随备份导出。 */
    val pinned: Boolean = false,
    /** 敏感文本：默认只存在于会话内存，用户显式固定后才落盘。 */
    val sensitive: Boolean = false,
    /** 记录时刻（epoch millis）：只用于排序与去重置顶，不直接展示。 */
    val time: Long = 0L,
) {
    /** 去重键：文本按 trim 后全等，图片按文件名全等。 */
    val key: String
        get() = when (kind) {
            ClipKind.TEXT -> "t:${text.trim()}"
            ClipKind.IMAGE -> "i:${imagePath.orEmpty()}"
        }

}

@Serializable
@Keep
enum class ClipKind { TEXT, IMAGE }

/**
 * 导入 / 导出用的单条文本：只带正文与固定标记。
 *
 * 图片是私有目录文件（跨机无效）、敏感项是用户没同意落盘的内容，两者都不进导出；
 * 图片与敏感条目的过滤在 [exportableEntries] 里完成。
 */
@Serializable
@Keep
data class ClipboardExportEntry(
    val text: String = "",
    val pinned: Boolean = false,
)

/** 剪贴板 JSON 交换格式；[version] 是格式版本，不匹配时导入方只认当前版本。 */
@Serializable
@Keep
data class ClipboardExport(
    val version: Int = ClipboardExportVersion,
    val entries: List<ClipboardExportEntry> = emptyList(),
)

const val ClipboardExportVersion = 1

/**
 * 剪贴板历史（最新在最前）。
 *
 * 容量口径：**未固定**条目最多 [ClipboardHistoryMaxEntries] 条；固定项不设上限、
 * 永不裁剪。不做时间过期，只按条数裁剪最旧的未固定条目。
 */
@Serializable
@Keep
data class ClipboardHistory(
    val entries: List<ClipboardEntry> = emptyList(),
) {
    val pinned: List<ClipboardEntry> get() = entries.filter { it.pinned }
    val unpinned: List<ClipboardEntry> get() = entries.filterNot { it.pinned }
}

/** 未固定条目的默认上限（可在剪贴板设置页调整，范围见 [ClipboardMaxEntriesRange]）。 */
const val ClipboardHistoryMaxEntries = 50

/** 剪贴板设置页允许的条数区间。 */
val ClipboardMaxEntriesRange = 10..200

/** 单条文本上限：超过直接丢弃，不截断（截断会破坏「复制原文」语义）。 */
const val ClipboardMaxTextChars = 100 * 1024

/** 图片副本长边上限：够预览与 OCR，又不让私有目录无限膨胀。 */
const val ClipboardImageMaxPx = 1600

/** 图片副本文件字节上限：解码前先按流长度拦，避免把大图读进内存。 */
const val ClipboardImageMaxBytes = 8L * 1024 * 1024

/** 图片副本目录名（`filesDir/clipboard_images`）。 */
const val ClipboardImageDirName = "clipboard_images"

/**
 * 纯函数记录一条内容：
 * - 文本空白 / 超长 / 图片缺文件名 → 原样返回（不入库）；
 * - 去重键命中时摘掉旧条目再置顶：保留原 `id`、`pinned` 与 `sensitive`（固定与敏感
 *   是用户显式决定，不因再次复制同一内容丢失），内容与时间刷新；
 * - 裁剪只作用于未固定项，超出 [maxEntries] 从最旧的未固定项开始丢。
 */
fun ClipboardHistory.record(
    entry: ClipboardEntry,
    now: Long,
    maxEntries: Int = ClipboardHistoryMaxEntries,
): ClipboardHistory {
    when (entry.kind) {
        ClipKind.TEXT -> {
            if (entry.text.isBlank()) return this
            if (entry.text.trim().toByteArray(Charsets.UTF_8).size > ClipboardMaxTextChars) return this
        }
        ClipKind.IMAGE -> if (entry.imagePath.isNullOrBlank()) return this
    }
    val key = entry.key
    val existing = entries.firstOrNull { it.key == key }
    val rest = entries.filterNot { it.key == key }
    val head = if (existing == null) {
        entry.copy(time = now)
    } else {
        existing.copy(
            kind = entry.kind,
            text = entry.text,
            imagePath = entry.imagePath ?: existing.imagePath,
            mime = entry.mime ?: existing.mime,
            // 固定 / 敏感是用户显式决定：再次复制同一内容、或导入一条固定副本，
            // 都只能升级不能降级。
            pinned = existing.pinned || entry.pinned,
            sensitive = existing.sensitive || entry.sensitive,
            time = now,
        )
    }
    return copy(entries = listOf(head) + rest).trimmed(maxEntries)
}

/** 按容量裁剪：固定项不占额度、永不裁剪；其余保留最新的 [maxEntries] 条。 */
fun ClipboardHistory.trimmed(maxEntries: Int = ClipboardHistoryMaxEntries): ClipboardHistory {
    val limit = maxEntries.coerceIn(ClipboardMaxEntriesRange.first, ClipboardMaxEntriesRange.last)
    var remaining = limit
    val kept = entries.filter { entry ->
        if (entry.pinned) {
            true
        } else if (remaining > 0) {
            remaining--
            true
        } else {
            false
        }
    }
    return if (kept.size == entries.size) this else copy(entries = kept)
}

/** 删除单条；不存在时原样返回（幂等）。 */
fun ClipboardHistory.remove(id: String): ClipboardHistory =
    if (entries.none { it.id == id }) this else copy(entries = entries.filterNot { it.id == id })

/** 清空全部历史（含固定项）；本来就空时原样返回（避免无意义写盘）。 */
fun ClipboardHistory.cleared(): ClipboardHistory =
    if (entries.isEmpty()) this else ClipboardHistory()

/** 设置固定态；已是目标态时原样返回（幂等）。固定项同时置顶。 */
fun ClipboardHistory.setPinned(id: String, pinned: Boolean): ClipboardHistory {
    val target = entries.firstOrNull { it.id == id } ?: return this
    if (target.pinned == pinned) return this
    val next = target.copy(pinned = pinned)
    val rest = entries.filterNot { it.id == id }
    return copy(entries = listOf(next) + rest)
}

/** 改变容量上限后的即时裁剪（改小上限时立刻生效）。 */
fun ClipboardHistory.withMaxEntries(maxEntries: Int): ClipboardHistory = trimmed(maxEntries)

/** 备份导出用固定项：只导文本，敏感项永不导出（用户显式固定的敏感内容仍不应跨机）。 */
fun ClipboardHistory.exportablePinned(): List<ClipboardEntry> =
    pinned.filter { it.kind == ClipKind.TEXT && !it.sensitive && it.text.isNotBlank() }

/**
 * 备份导入：把固定文本并进当前历史。按 trim 全等去重，保留已有 `id`，
 * 导入项一律 `pinned = true`；图片与敏感项不进（备份里本就不会出现）。
 */
fun ClipboardHistory.mergePinned(imported: List<ClipboardEntry>): ClipboardHistory {
    var result = this
    imported.asReversed().forEach { entry ->
        if (entry.kind != ClipKind.TEXT || entry.sensitive || entry.text.isBlank()) return@forEach
        if (entry.text.trim().toByteArray(Charsets.UTF_8).size > ClipboardMaxTextChars) return@forEach
        result = result.record(entry.copy(kind = ClipKind.TEXT, pinned = true), now = entry.time)
    }
    return result
}

/**
 * 导入 / 导出用：全部非敏感文本（固定与普通都带 `pinned` 标记），按新→旧。
 * 图片与敏感项被排除，保证导出文件里没有跨机失效的路径或密码类内容。
 */
fun ClipboardHistory.exportableEntries(): List<ClipboardExportEntry> =
    entries
        .filter { it.kind == ClipKind.TEXT && !it.sensitive && it.text.isNotBlank() }
        .map { ClipboardExportEntry(text = it.text, pinned = it.pinned) }

/**
 * JSON 导入：按 trim 全等去重合并，不覆盖本机已有历史。
 *
 * 保留导入项的 `pinned` 标记；图片与敏感项永远不导入。按导入顺序逐条 [record]，
 * 因此同一条文本导入后置顶，最后按 [maxEntries] 裁剪未固定项。
 */
fun ClipboardHistory.mergeImported(
    imported: List<ClipboardExportEntry>,
    now: Long,
    maxEntries: Int = ClipboardHistoryMaxEntries,
): ClipboardHistory {
    var result = this
    imported.asReversed().forEach { item ->
        if (item.text.isBlank()) return@forEach
        if (item.text.trim().toByteArray(Charsets.UTF_8).size > ClipboardMaxTextChars) return@forEach
        result = result.record(
            ClipboardEntry(
                kind = ClipKind.TEXT,
                text = item.text,
                pinned = item.pinned,
                time = now,
            ),
            now = now,
            maxEntries = maxEntries,
        )
    }
    return result
}

/** 搜索池：固定文本（含敏感项，用户已显式落盘）与普通文本历史，均按新→旧。 */
fun ClipboardHistory.searchPool(): Pair<List<String>, List<String>> {
    // 敏感项即使已固定也不进搜索：搜索结果会把正文直接铺在面板上，等于绕过管理页的遮蔽。
    val pinnedTexts = pinned
        .filter { it.kind == ClipKind.TEXT && it.text.isNotBlank() && !it.sensitive }
        .map { it.text }
    val historyTexts = unpinned
        .filter { it.kind == ClipKind.TEXT && it.text.isNotBlank() && !it.sensitive }
        .map { it.text }
    return pinnedTexts to historyTexts
}
