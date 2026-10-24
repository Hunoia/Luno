package hunoia.luno.quicklaunch.query

import hunoia.luno.config.model.MAX_PINS_PER_QUERY
import hunoia.luno.config.model.QuickSearchSettings
import hunoia.luno.config.model.SRC_CLIP

/** query 归一化：Pin 以 trim + lowercase 为键，避免大小写/尾空格产生重复桶。 */
internal fun normalizePinQuery(query: String): String =
    query.trim().lowercase()

/** 引擎/URL/计算器/剪贴板行随 query 变化或已有独立 Pin 语义，不参与结果级 Pin/Hide。 */
internal fun SearchResult.isResultOverrideAllowed(): Boolean = when (source) {
    SOURCE_ENGINE, SOURCE_URL, SOURCE_CALC, SRC_CLIP -> false
    else -> true
}

/** 全局隐藏过滤；引擎行等不允许隐藏的结果即使误入 key 列表也保留。 */
internal fun List<SearchResult>.filterHiddenResults(
    settings: QuickSearchSettings,
): List<SearchResult> {
    val hidden = settings.hiddenResultKeys.toHashSet()
    if (hidden.isEmpty()) return this
    return filterNot { it.isResultOverrideAllowed() && it.key in hidden }
}

/** 当前 query 的 Pin 置顶；其余结果保持原排序。 */
internal fun List<SearchResult>.applyResultPins(
    query: String,
    settings: QuickSearchSettings,
): List<SearchResult> {
    val pinnedKeys = settings.pinnedResults[normalizePinQuery(query)].orEmpty()
    if (pinnedKeys.isEmpty()) return this
    val rank = HashMap<String, Int>(pinnedKeys.size)
    pinnedKeys.take(MAX_PINS_PER_QUERY).forEachIndexed { index, key ->
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
