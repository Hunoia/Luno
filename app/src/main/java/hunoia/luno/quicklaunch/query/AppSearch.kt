package hunoia.luno.quicklaunch.query

import com.github.houbb.pinyin.constant.enums.PinyinStyleEnum
import com.github.houbb.pinyin.util.PinyinHelper
import androidx.collection.LruCache
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.LauncherInfo
import hunoia.luno.config.model.QuickAppLauncherSettings
import kotlin.math.ln
import kotlin.math.pow

/**
 * 档位 + 紧密度：tier 定档，offset 定同档先后；只认连续块（token 依次占相邻字符，块可在任意位置）。
 * 无块即 NO_MATCH（稀疏子序列不是命中）；块在 0 位 → 锚定，span 恒 = token 数。
 */
internal data class TieredMatch(val tier: Int, val span: Int, val offset: Int) {
    companion object {
        val NO_MATCH = TieredMatch(-1, Int.MAX_VALUE, Int.MAX_VALUE)
    }
}

object AppSearch {

    data class AppSearchIndex(
        val raw: String,
        val pinyin: String,
        val initials: String,
        /** 折叠串（模糊音）：ing→in、eng→en、ang→ang、zh→z、ch→c、sh→s，声母不变。 */
        val pinyinFolded: String = "",
        /** 多音字变体（单字单换，防组合爆炸，cap 8）：如 音乐→[yinle, yinyue]。 */
        val altPinyins: List<String> = emptyList(),
        val altPinyinsFolded: List<String> = emptyList(),
        /** 变体首字母串（单字单换，独立 cap）：如 番茄小说→[bqxs, pqxs, fqxs]。
         * 首字母串只用各字第一读音构建（茄:jiā,qié → fjxs），换音读法只活在全拼变体里，
         * 没有 initials 变体则 fq 这类相邻双字母在全拼中隔着韵母永远命不中。 */
        val altInitials: List<String> = emptyList(),
    )

    private data class SearchEntry(
        val app: AppInfo,
        val matchTier: Int,
        val offset: Int,
        val usageScore: Double,
        val labelLower: String,
        val labelLen: Int,
    )

    // 索引常驻（总量被各源 cap 封顶）：打字只查不建，houbb 调用只发生在 IO 线程的预热期。
    // 容量按条目数封顶 + LRU 淘汰：短信正文/文件名各占一条，无界会在长会话里累积到几十 MB。
    // 上界估算峰值约 2.6 万 → 48k 留 2 倍余量；用 androidx.collection.LruCache（纯 JVM 可用，
    // 内部线程安全），不自己维护 accessOrder 锁。
    private const val PINYIN_CACHE_CAP = 48_000
    private val pinyinIndexCache = LruCache<String, AppSearchIndex>(PINYIN_CACHE_CAP)

    private fun cachedIndex(key: String): AppSearchIndex? = pinyinIndexCache.get(key)

    private fun putIndex(key: String, index: AppSearchIndex) {
        pinyinIndexCache.put(key, index)
    }

    /** 单测用：当前缓存条数（验证有界性）。 */
    internal fun cacheSizeForTest(): Int = pinyinIndexCache.size()

    /**
     * 搜索面板关闭后把拼音缓存压回小上限：进程级索引缓存已经保留了应用/联系人等
     * 原始表，没必要再让 48k 条拼音索引长期占着 Java 堆。下次打字按需重建。
     */
    fun trimPinyinCache(maxEntries: Int = 4_000) {
        pinyinIndexCache.trimToSize(maxEntries)
    }

    private const val NO_MATCH = -1
    private const val DEGRADED = 0
    private const val ANCHOR = 1

    /**
     * 单字全部读音：houbb/pinyin 给多音全集（NORMAL 无调），库里没有才手写。
     * ü→v 归一（跟键盘输入习惯一致，打 lv），去重；非中文按原文（字母数字）。
     */
    private fun readingsOf(ch: Char): List<String> {
        val block = Character.UnicodeBlock.of(ch)
        val isCjk = block === Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block === Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block === Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
            block === Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
        if (!isCjk) return listOf(ch.lowercaseChar().toString())
        return runCatching {
            PinyinHelper.toPinyinList(ch, PinyinStyleEnum.NORMAL)
                .map { it.lowercase().replace('ü', 'v') }
                .map { it.filter { c -> c in 'a'..'z' } }
                .filter { it.isNotEmpty() }
                .distinct()
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: listOf(ch.lowercaseChar().toString())
    }

    /**
     * 模糊音折叠：前后鼻音 + 平翘舌归到短式（打错/打全的人能命中）。
     * 精确匹配优先，折叠只走降档 tier，所以英文词（如 shopping→shopin）不受影响。
     */
    fun foldPinyin(s: String): String {
        var t = s.lowercase()
        t = t.replace("ing", "in").replace("eng", "en").replace("ang", "an").replace("ong", "on")
        t = t.replace("zh", "z").replace("ch", "c").replace("sh", "s")
        return t
    }
    /**
     * 使用频率分：对数压制次数（老人不无限累积）× 指数衰减（3 天半衰，新欢说了算）。
     * 三处调用（sortApps / QuickSearchIndex / 快捷方式包聚合）必须同公式。
     */
    internal fun decayedUsage(count: Long, ageDays: Double): Double =
        ln(1.0 + count) * 0.5.pow(ageDays / 3.0)

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

    /**
     * 连续块扫描：从 0 位起找首个“token 依次相邻”的窗口，首个即赢。
     * 0 位块 → 锚定；中段块 → 降级（offset = 块起点）；无块 → NO_MATCH。
     * 稀疏子序列不是命中——搜的都是连续词。
     */
    private fun matchWithMasks(text: String, masks: LongArray): TieredMatch {
        if (masks.isEmpty()) return TieredMatch(DEGRADED, 0, 0)
        if (masks.size > text.length) return TieredMatch.NO_MATCH

        for (start in 0..(text.length - masks.size)) {
            var ok = true
            for (i in masks.indices) {
                if (!charMatches(text[start + i], masks[i])) { ok = false; break }
            }
            // span 恒 = masks.size（块长即 token 数）；排序只看 tier/offset。
            if (ok) return if (start == 0) TieredMatch(ANCHOR, masks.size, 0)
            else TieredMatch(DEGRADED, masks.size, start)
        }
        return TieredMatch.NO_MATCH
    }

    internal fun matchTier(index: AppSearchIndex, tokens: List<String>, masks: LongArray): TieredMatch {
        if (tokens.isEmpty()) return TieredMatch(0, 0, 0)

        if (masks.any { it == -1L }) {
            return if (matchesTokens(index.initials, tokens, true)
                || matchesTokens(index.pinyin, tokens, true)
                || matchesTokens(index.raw, tokens, true)
            ) {
                TieredMatch(0, tokens.size, 0)
            } else {
                TieredMatch.NO_MATCH
            }
        }

        // 代表选取：各字段（initials/pinyin/变体/raw）的连续块按档位+起点取最优。
        // initials（基准 + 变体）0 位块独占 tier 2；其余字段 0 位块 tier 1；中段块 tier 0。
        val cands = mutableListOf<TieredMatch>()
        val ini = matchWithMasks(index.initials, masks)
        cands.add(if (ini.tier == ANCHOR) TieredMatch(2, ini.span, ini.offset) else ini)
        // 变体首字母与基准同档：换音只是库里读音排序问题（茄:jiā,qié），用户意图相同
        // （fq/fj 都在搜番茄小说），不降档。
        for (altIni in index.altInitials) {
            val r = matchWithMasks(altIni, masks)
            cands.add(if (r.tier == ANCHOR) TieredMatch(2, r.span, r.offset) else r)
        }
        fun addAnchor(text: String) {
            val r = matchWithMasks(text, masks)
            cands.add(if (r.tier == ANCHOR) TieredMatch(1, r.span, r.offset) else r)
        }
        addAnchor(index.pinyin)

        // 多音字变体与基准同档（如 yinyue 对 yinle/yinyue 都是 tier 1）。
        for (alt in index.altPinyins) {
            addAnchor(alt)
        }
        addAnchor(index.raw)
        return cands.maxWith(compareBy({ it.tier }, { -it.offset }))
    }

    /**
     * 模糊音降档：只比折叠串（基准 + 变体），命中恒 tier 0。
     * 精确匹配优先——本函数只在精确返回 -1 后调，打对的人排名不受影响。
     */
    internal fun matchTierFuzzy(index: AppSearchIndex, foldedTokens: List<String>): TieredMatch {
        if (foldedTokens.isEmpty()) return TieredMatch.NO_MATCH
        val masks = compileTokenMasks(foldedTokens)
        if (masks.any { it == -1L }) {
            return if (matchesTokens(index.pinyinFolded, foldedTokens, false)) {
                TieredMatch(0, foldedTokens.size, 0)
            } else {
                TieredMatch.NO_MATCH
            }
        }
        val cands = mutableListOf(matchWithMasks(index.pinyinFolded, masks))
        for (alt in index.altPinyinsFolded) {
            cands.add(matchWithMasks(alt, masks))
        }
        val best = cands.maxWith(compareBy({ it.tier }, { -it.offset }))
        return if (best.tier >= DEGRADED) TieredMatch(0, best.span, best.offset) else TieredMatch.NO_MATCH
    }

    fun sortApps(apps: List<AppInfo>, settings: QuickAppLauncherSettings, tokens: List<String>): List<AppInfo> {
        val now = System.currentTimeMillis()
        val masks = if (tokens.isNotEmpty()) compileTokenMasks(tokens) else LongArray(0)
        // 模糊音输入：分词前先整体折叠（adjacency 丢了就没法折），降档用。
        val foldedTokens = if (tokens.isNotEmpty()) {
            foldPinyin(tokens.joinToString("")).map { it.toString() }
        } else {
            emptyList()
        }

        val entries = ArrayList<SearchEntry>(apps.size)
        for (app in apps) {
            val index = buildIndex(app.label)
            var m = matchTier(index, tokens, masks)
            if (m.tier < 0) m = matchTierFuzzy(index, foldedTokens)
            if (m.tier < 0) continue

            val key = app.key()
            val launchCount = settings.launchCount[key] ?: 0L
            val recentTime = settings.recentLaunchTime[key] ?: 0L
            val usageScore = if (launchCount > 0 && recentTime > 0) {
                decayedUsage(launchCount, (now - recentTime) / 86400000.0)
            } else 0.0

            entries.add(SearchEntry(app, m.tier, m.offset, usageScore, index.raw, app.label.length))
        }

        // 连续块语义：档位 → 起点 → 使用分 → 长度 → 字典序。
        // tier ≥ 1 的命中 offset 恒 0（0 位块写死），tier/offset 永不打架。
        entries.sortWith(
            compareByDescending<SearchEntry> { it.matchTier }
                .thenBy { it.offset }
                .thenByDescending { it.usageScore }
                .thenBy { it.labelLen }
                .thenBy { it.labelLower }
        )

        val result = ArrayList<AppInfo>(entries.size)
        for (entry in entries) result.add(entry.app)
        return result
    }

    fun warmIndexes(apps: List<AppInfo>) {
        for (app in apps) {
            buildIndex(app.label)
            // 包名也参与匹配（副标题本来就显示包名）：ascii 串虽无拼音成本，
            // 预热后打字期连字符串拼接都不再发生。
            buildIndex(app.packageName)
        }
    }

    /** 快捷方式同样按 label + 包名匹配（见 matchShortcuts），索引一并预热。 */
    fun warmShortcutIndexes(shortcuts: List<LauncherInfo.ShortcutInfo>) {
        for (sc in shortcuts) {
            buildIndex(sc.label)
            buildIndex(sc.packageName)
        }
    }

    internal fun buildIndex(text: String): AppSearchIndex {
        // 绝大多数输入已经是小写：先拿原文直查，命中就省一次 lowercase 分配。
        cachedIndex(text)?.let { return it }
        val key = if (text.any { it.isUpperCase() }) text.lowercase() else text
        cachedIndex(key)?.let { return it }
        // 长文本只索引前 200 字、不建多音变体（短信正文类）：200 字之后的中段命中出局，
        // 代价可接受（连续块语义下 offset 大的本来也沉底）。缓存 key 也用截断串：一条 5KB
        // 短信正文若用全长 key 会以 5KB 常驻；前 200 字相同的长文本共用一份索引，结果一致。
        val short = text.length <= 200
        // 长文本先用截断串查一次缓存：索引本来就只看前 200 字，命中直接复用。
        if (!short) cachedIndex(key.substring(0, 200))?.let { return it }
        val work = if (short) key else key.substring(0, 200)
        // 每字读音：库给多音全集（首个为基准串，其余进变体）。
        val readings = ArrayList<List<String>>()
        val basePinyin = StringBuilder()
        val initials = StringBuilder()
        work.forEach { ch ->
            if (ch.isLetterOrDigit()) {
                val r = readingsOf(ch)
                readings.add(r)
                basePinyin.append(r[0])
                initials.append(r[0].first())
            }
        }
        // 变体：每次只换一个多音字（防组合爆炸），如 音乐→[yinle, yinyue]；长文本跳过
        // （变体是短标题多音字的活，正文里建了也排不上）。全拼与 initials 各自独立 cap：
        // 共用名额会被多读音字（如 番 的 7 个变体）挤占关键变体，故分开计数。
        val alts = ArrayList<String>()
        val altInis = LinkedHashSet<String>()
        if (short) {
            for (i in readings.indices) {
                val r = readings[i]
                if (r.size < 2) continue
                val initialsBase = r[0].first()
                for (alt in r.subList(1, r.size)) {
                    if (alts.size < 8) {
                        val sb = StringBuilder()
                        for (j in readings.indices) sb.append(if (j == i) alt else readings[j][0])
                        val s = sb.toString()
                        if (s !in alts) alts.add(s)
                    }
                    val ini = alt.first()
                    if (ini != initialsBase && altInis.size < 16) {
                        val sb = StringBuilder()
                        for (j in readings.indices) sb.append(if (j == i) ini else readings[j][0].first())
                        altInis.add(sb.toString())
                    }
                }
            }
        }
        val base = basePinyin.toString()
        val result = AppSearchIndex(
            // raw 取 work（被索引的前 200 字）：非 ASCII 回落走它，截断语义一致。
            raw = work,
            pinyin = base,
            initials = initials.toString(),
            pinyinFolded = foldPinyin(base),
            altPinyins = alts,
            altPinyinsFolded = alts.map { foldPinyin(it) },
            altInitials = altInis.toList(),
        )
        putIndex(work, result)
        return result
    }
    fun AppInfo.key() = "$packageName/$className"

    /** 对任意文本做与 sortApps 相同的 tier 评定，供快速搜索复用。 */
    internal fun matchTextTier(text: String, tokens: List<String>, foldedTokens: List<String> = emptyList()): TieredMatch {
        if (tokens.isEmpty()) return TieredMatch(0, 0, 0)
        val index = buildIndex(text)
        val tier = matchTier(index, tokens, compileTokenMasks(tokens))
        if (tier.tier >= 0 || foldedTokens.isEmpty()) return tier
        return matchTierFuzzy(index, foldedTokens)
    }
}

/**
 * token 序列匹配：所有 token 依次出现在 text 的某个窗口内。
 * - [emptyMatches] 是必需的：`AppSearch` 侧空查询应匹配全部（true），而 `pageMatchesTokens`
 *   由三个 `||` 串联，空查询返回 true 会恒真、分页永不前进（false）。
 * - 内层不用 `token.lowercase().contains(...)`：那会在每个 (start, i) 组合上分配新 String，
 *   比较器被调用 O(n log n) 次，每次每个 token 都分配一次。
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
