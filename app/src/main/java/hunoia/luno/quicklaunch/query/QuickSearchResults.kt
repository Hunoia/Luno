package hunoia.luno.quicklaunch.query

import hunoia.luno.action.model.ActionLibraryEntry
import hunoia.luno.config.model.AppUsageStats
import hunoia.luno.config.model.QuickSearchSettings

/**
 * 索引层搜索产出：原始结果 + 是否命中标识指令（bb 新闻 / sms 138）。
 * 指令态只回目标结果，历史行不参展——由搜索流直接回报，
 * 面板不用再单独判一次（判一次要摸裸标识列表，可能触发全量查包）。
 */
data class SearchOutcome(
    val results: List<QuickSearchResult>,
    val directive: Boolean,
)

/** 展示层产出：[SearchResult] 列表 + 是否命中标识指令。 */
data class SearchResults(
    val results: List<SearchResult>,
    val directive: Boolean,
)

/**
 * 展示层入口：一次完成匹配 → 旧模型适配 → Pin 排序。
 * 必须在后台线程调用，UI 只消费结果。
 *
 * [isActive] 返回 false 时抛 [kotlinx.coroutines.CancellationException]（协作式取消），
 * 调用方需保证在协程内且不再执行后续逻辑。
 */
fun QuickSearchIndex.searchOutcome(
    query: String,
    libraryEntries: List<ActionLibraryEntry>,
    usageStats: AppUsageStats = AppUsageStats(),
    searchSettings: QuickSearchSettings = QuickSearchSettings(),
    torchOn: Boolean = false,
    isActive: () -> Boolean = { true },
): SearchResults {
    val outcome = search(query, libraryEntries, usageStats, searchSettings, torchOn, isActive)
    if (outcome.results.isEmpty()) return SearchResults(emptyList(), outcome.directive)
    return SearchResults(
        results = outcome.results
            .map { it.toSearchResult() }
            .applyResultPins(query, searchSettings),
        directive = outcome.directive,
    )
}
