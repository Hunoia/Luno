package hunoia.luno.quicklaunch.query

import com.github.promeg.pinyinhelper.Pinyin
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.config.model.QuickAppLauncherSettings
import java.util.Collections
import java.util.LinkedHashMap

object AppSearch {

    data class AppSearchIndex(val raw: String, val pinyin: String, val initials: String)

    private val pinyinIndexCache: MutableMap<String, AppSearchIndex> = Collections.synchronizedMap(
        object : LinkedHashMap<String, AppSearchIndex>(1024, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AppSearchIndex>?): Boolean = size > 1024
        }
    )

    fun sortApps(apps: List<AppInfo>, settings: QuickAppLauncherSettings, tokens: List<String>): List<AppInfo> {
        val matched = if (tokens.isEmpty()) apps else apps.filter { app -> matchesApp(app, tokens) }
        return matched.sortedWith(compareByDescending<AppInfo> { prefixRank(it, tokens) }
            .thenByDescending { settings.recentLaunchTime[it.key()] ?: 0L }
            .thenByDescending { settings.launchCount[it.key()] ?: 0L }
            .thenByDescending { containsRank(it, tokens) }
            .thenBy { it.label.lowercase() })
    }

    private fun matchesApp(app: AppInfo, tokens: List<String>): Boolean {
        val index = buildIndex(app.label)
        return matchesTokens(index.initials, tokens, true) || matchesTokens(index.pinyin, tokens, true) || matchesTokens(index.raw, tokens, true)
    }

    private fun prefixRank(app: AppInfo, tokens: List<String>): Int {
        if (tokens.isEmpty()) return 0
        val index = buildIndex(app.label)
        val joined = tokens.joinToString("")
        return when {
            matchesTokens(index.initials, tokens, true) && index.initials.startsWith(joined) -> 2
            matchesTokens(index.raw, tokens, true) && index.raw.startsWith(joined) -> 1
            matchesTokens(index.pinyin, tokens, true) && index.pinyin.startsWith(joined) -> 1
            else -> 0
        }
    }

    private fun containsRank(app: AppInfo, tokens: List<String>): Int {
        if (tokens.isEmpty()) return 0
        val index = buildIndex(app.label)
        return if (matchesTokens(index.initials, tokens, true) || matchesTokens(index.pinyin, tokens, true) || matchesTokens(index.raw, tokens, true)) 1 else 0
    }

    private fun buildIndex(text: String): AppSearchIndex {
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