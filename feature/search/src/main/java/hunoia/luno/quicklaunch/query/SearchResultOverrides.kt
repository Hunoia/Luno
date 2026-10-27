package hunoia.luno.quicklaunch.query

import hunoia.luno.config.model.MAX_PINS_PER_QUERY
import hunoia.luno.config.model.QuickSearchSettings
import hunoia.luno.config.model.SRC_CLIP

/** query 归一化：Pin 以 trim + lowercase 为键，避免大小写/尾空格产生重复桶。 */
fun normalizePinQuery(query: String): String =
    query.trim().lowercase()

/**
 * 当前 query 的 Pin 键（已截断到 [MAX_PINS_PER_QUERY]）。
 * 索引层裁剪与展示层置顶必须用同一份，否则被裁掉的结果永远置不了顶。
 */
fun QuickSearchSettings.pinnedKeyList(query: String): List<String> =
    pinnedResults[normalizePinQuery(query)].orEmpty().take(MAX_PINS_PER_QUERY)

/** 引擎/URL/计算器/剪贴板行随 query 变化或已有独立 Pin 语义，不参与结果级 Pin。 */
fun SearchResult.isResultOverrideAllowed(): Boolean = when (source) {
    SOURCE_ENGINE, SOURCE_URL, SOURCE_CALC, SRC_CLIP -> false
    else -> true
}

/** 与 [SearchResult.isResultOverrideAllowed] 同口径的索引层版本：裁剪时不能把「本来不允许 Pin」的行当 Pin 旁路。 */
fun QuickSearchResult.isResultPinAllowed(): Boolean = when (this) {
    is QuickSearchResult.EngineSearch,
    is QuickSearchResult.Url,
    is QuickSearchResult.Calc,
    is QuickSearchResult.ClipEntry,
    -> false
    else -> true
}

/** 当前 query 的 Pin 置顶；其余结果保持原排序。 */
fun List<SearchResult>.applyResultPins(
    query: String,
    settings: QuickSearchSettings,
): List<SearchResult> {
    val pinnedKeys = settings.pinnedKeyList(query)
    if (pinnedKeys.isEmpty()) return this
    val rank = HashMap<String, Int>(pinnedKeys.size)
    pinnedKeys.forEachIndexed { index, key ->
        rank.putIfAbsent(key, index)
    }
    if (rank.isEmpty()) return this
    val pinned = ArrayList<SearchResult>(rank.size)
    val rest = ArrayList<SearchResult>(size)
    for (result in this) {
        if (result.isResultOverrideAllowed() && rank.containsKey(result.key)) {
            pinned.add(result)
        } else {
            rest.add(result)
        }
    }
    if (pinned.isEmpty()) return this
    pinned.sortBy { rank[it.key] ?: Int.MAX_VALUE }
    return ArrayList<SearchResult>(size).apply {
        addAll(pinned)
        addAll(rest)
    }
}
