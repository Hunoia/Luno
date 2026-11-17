package hunoia.luno.quickaccess.query

/**
 * 选择页与快速搜索共用的文本匹配入口。
 *
 * 语义与 [AppSearch] 一致：查询按空白拆成多段；每段去掉非字母数字后作为**连续字符块**
 * 去匹配字段（首字母 / 全拼 / 多音变体 / 原文，精确不中再降模糊音档）。
 * 多段之间是 **AND**：每段都要在某个字段命中（如 `微信 发消息` 可同时命中应用名与
 * 快捷方式名）。段内不支持乱序（`xw` 不会命中微信）。
 */
class SearchText internal constructor(
    private val chunks: List<SearchQueryPlan>,
) {
    val isEmpty: Boolean get() = chunks.isEmpty()

    /**
     * 每段都命中某一字段时返回各段命中的最优档（rank 用），否则 [TieredMatch.NO_MATCH]。
     * [fields] 是同一实体的可搜索字段（应用名、包名、快捷方式名……）。
     */
    fun best(fields: List<String>): TieredMatch {
        if (chunks.isEmpty()) return TieredMatch(0, 0, 0)
        var overall = TieredMatch.NO_MATCH
        for (chunk in chunks) {
            val chunkBest = bestOfChunk(chunk, fields)
            if (chunkBest.tier < 0) return TieredMatch.NO_MATCH
            if (chunkBest.tier > overall.tier ||
                (chunkBest.tier == overall.tier && chunkBest.offset < overall.offset)
            ) {
                overall = chunkBest
            }
        }
        return overall
    }

    fun matches(vararg fields: String): Boolean = best(fields.asList()).tier >= 0

    fun matches(fields: List<String>): Boolean = best(fields).tier >= 0

    /** 任一段命中任一字段：用于父级已整体命中时对子项做相关剪枝（不做 AND）。 */
    fun matchesAnyChunk(fields: List<String>): Boolean = chunks.any { bestOfChunk(it, fields).tier >= 0 }

    private fun bestOfChunk(chunk: SearchQueryPlan, fields: List<String>): TieredMatch {
        var best = TieredMatch.NO_MATCH
        for (field in fields) {
            val match = AppSearch.matchTextTier(field, chunk)
            if (match.tier < 0) continue
            // 前缀锚定已是本段理论上限（tier 2 / offset 0），后面的字段不用再扫。
            if (match.tier == MAX_TIER && match.offset == 0) return match
            if (match.tier > best.tier ||
                (match.tier == best.tier && match.offset < best.offset)
            ) {
                best = match
            }
        }
        return best
    }

    companion object {
        private const val MAX_TIER = 2
        private val WHITESPACE = Regex("\\s+")

        /** 空查询或只有符号时返回 null（调用方按“无搜索”处理）。 */
        fun compile(query: String): SearchText? {
            val chunks = query.trim().split(WHITESPACE).mapNotNull { part ->
                val tokens = tokensOf(part)
                if (tokens.isEmpty()) {
                    null
                } else {
                    AppSearch.queryPlan(tokens, tokensOf(AppSearch.foldPinyin(part)))
                }
            }
            return if (chunks.isEmpty()) null else SearchText(chunks)
        }

        private fun tokensOf(text: String): List<String> =
            text.lowercase().filter { it.isLetterOrDigit() }.map { it.toString() }
    }
}

/**
 * 预建拼音索引（可在 `Dispatchers.Default` 调用）：把首键搜索的建索引成本挪到预热期。
 * 有界 LRU 缓存由 [AppSearch] 管，重复调用只命中缓存。
 */
fun warmSearchIndex(text: String) {
    AppSearch.buildIndex(text)
}
