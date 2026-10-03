package hunoia.luno.quicklaunch.query

import com.github.promeg.pinyinhelper.Pinyin
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.config.model.QuickAppLauncherSettings
import java.util.Collections
import java.util.LinkedHashMap

object AppSearch {

    data class AppSearchIndex(val raw: String, val pinyin: String, val initials: String)

    private data class SearchEntry(
        val app: AppInfo,
        val matchTier: Int,
        val usageScore: Double,
        val labelLower: String,
        val labelLen: Int,
    )

    private val pinyinIndexCache: MutableMap<String, AppSearchIndex> = Collections.synchronizedMap(
        object : LinkedHashMap<String, AppSearchIndex>(1024, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AppSearchIndex>?): Boolean = size > 1024
        }
    )

    private const val NO_MATCH = -1
    private const val DEGRADED = 0
    private const val ANCHOR = 1

    internal fun compileTokenMasks(tokens: List<String>): LongArray {
        val masks = LongArray(tokens.size)
        for (i in tokens.indices) {
            var mask = 0L
            for (c in tokens[i]) {
                val bit = when {
                    c in 'a'..'z' -> 1L shl (c - 'a')
                    c in 'A'..'Z' -> 1L shl (c - 'A')
                    c in '0'..'9' -> 1L shl (c - '0' + 26)
                    else -> { mask = -1L; break }
                }
                mask = mask or bit
            }
            masks[i] = mask
        }
        return masks
    }

    private fun charMatches(ch: Char, mask: Long): Boolean {
        val bit = when {
            ch in 'a'..'z' -> 1L shl (ch - 'a')
            ch in 'A'..'Z' -> 1L shl (ch - 'A')
            ch in '0'..'9' -> 1L shl (ch - '0' + 26)
            else -> return false
        }
        return (mask and bit) != 0L
    }

    private fun matchWithMasks(text: String, masks: LongArray): Int {
        if (masks.isEmpty()) return DEGRADED
        if (masks.size > text.length) return NO_MATCH

        if (charMatches(text[0], masks[0])) {
            var pos = 1
            var ok = true
            for (i in 1 until masks.size) {
                var found = false
                while (pos < text.length) {
                    if (charMatches(text[pos], masks[i])) { found = true; pos++; break }
                    pos++
                }
                if (!found) { ok = false; break }
            }
            if (ok) return ANCHOR
        }

        var pos = 0
        var allMatched = true
        for (i in masks.indices) {
            var found = false
            while (pos < text.length) {
                if (charMatches(text[pos], masks[i])) { found = true; pos++; break }
                pos++
            }
            if (!found) { allMatched = false; break }
        }
        return if (allMatched) DEGRADED else NO_MATCH
    }

    internal fun matchTier(index: AppSearchIndex, tokens: List<String>, masks: LongArray): Int {
        if (tokens.isEmpty()) return 0

        if (masks.any { it == -1L }) {
            return if (matchesTokens(index.initials, tokens, true)
                || matchesTokens(index.pinyin, tokens, true)
                || matchesTokens(index.raw, tokens, true)) 0 else -1
        }

        val initials = matchWithMasks(index.initials, masks)
        if (initials == ANCHOR) return 2
        var anyDegraded = initials == DEGRADED

        val pinyin = matchWithMasks(index.pinyin, masks)
        if (pinyin == ANCHOR) return 1
        anyDegraded = anyDegraded || pinyin == DEGRADED

        val raw = matchWithMasks(index.raw, masks)
        if (raw == ANCHOR) return 1
        anyDegraded = anyDegraded || raw == DEGRADED

        return if (anyDegraded) 0 else -1
    }

    fun sortApps(apps: List<AppInfo>, settings: QuickAppLauncherSettings, tokens: List<String>): List<AppInfo> {
        val now = System.currentTimeMillis()
        val masks = if (tokens.isNotEmpty()) compileTokenMasks(tokens) else LongArray(0)

        val entries = ArrayList<SearchEntry>(apps.size)
        for (app in apps) {
            val index = buildIndex(app.label)
            val tier = matchTier(index, tokens, masks)
            if (tier < 0) continue

            val key = app.key()
            val launchCount = settings.launchCount[key] ?: 0L
            val recentTime = settings.recentLaunchTime[key] ?: 0L
            val usageScore = if (launchCount > 0 && recentTime > 0) {
                val ageDays = (now - recentTime) / 86400000.0
                launchCount / (1.0 + ageDays / 7.0)
            } else 0.0

            entries.add(SearchEntry(app, tier, usageScore, index.raw, app.label.length))
        }

        entries.sortWith(
            compareByDescending<SearchEntry> { it.matchTier }
                .thenByDescending { it.usageScore }
                .thenBy { it.labelLen }
                .thenBy { it.labelLower }
        )

        val result = ArrayList<AppInfo>(entries.size)
        for (entry in entries) result.add(entry.app)
        return result
    }

    fun warmIndexes(apps: List<AppInfo>) {
        for (app in apps) buildIndex(app.label)
    }

    internal fun buildIndex(text: String): AppSearchIndex {
        val key = text.lowercase()
        pinyinIndexCache[key]?.let { return it }
        val pinyin = StringBuilder()
        val initials = StringBuilder()
        text.forEach { ch ->
            if (ch.isLetterOrDigit()) {
                val py = Pinyin.toPinyin(ch).lowercase()
                pinyin.append(py)
                initials.append(py.first())
            }
        }
        val result = AppSearchIndex(key, pinyin.toString(), initials.toString())
        pinyinIndexCache[key] = result
        return result
    }

    fun AppInfo.key() = "$packageName/$className"

    /** 对任意文本做与 sortApps 相同的 tier 评定，供快速搜索复用。 */
    internal fun matchTextTier(text: String, tokens: List<String>): Int {
        if (tokens.isEmpty()) return 0
        return matchTier(buildIndex(text), tokens, compileTokenMasks(tokens))
    }
}

/**
 * token 序列匹配：所有 token 依次出现在 text 的某个窗口内。
 *
 * [emptyMatches] 是这个参数存在的原因 —— 两处调用点的空 token 语义**相反**且都是必需的：
 * - `AppSearch` 侧空查询应匹配全部，true
 * - `pageMatchesTokens` 是三个 `||` 串起来的，空查询若返回 true 则恒真、分页永不前进，false
 *
 * 内层循环刻意不用 `token.lowercase().contains(...)`：那会在每个 (start, i) 组合上分配
 * 一个新 String，比较器被调用 O(n log n) 次、每次每个 token 都要分配一次。
 */
internal fun matchesTokens(text: String, tokens: List<String>, emptyMatches: Boolean): Boolean {
    if (tokens.isEmpty()) return emptyMatches
    if (tokens.size > text.length) return false
    for (start in 0..(text.length - tokens.size)) {
        var ok = true
        for (i in tokens.indices) {
            val token = tokens[i]
            val ch = text[start + i]
            if (token.length == 1 && token[0].isDigit()) {
                if (token[0] != ch) { ok = false; break }
            } else if (!containsIgnoreCase(token, ch)) { ok = false; break }
        }
        if (ok) return true
    }
    return false
}

private fun containsIgnoreCase(token: String, ch: Char): Boolean {
    for (t in token) if (t.lowercaseChar() == ch.lowercaseChar()) return true
    return false
}
