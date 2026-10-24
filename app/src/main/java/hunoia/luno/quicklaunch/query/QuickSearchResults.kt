package hunoia.luno.quicklaunch.query

import hunoia.luno.action.model.ActionLibraryEntry
import hunoia.luno.config.model.QuickAppLauncherSettings
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
 * 展示层入口：一次完成匹配 → 旧模型适配 → 引擎行 → 隐藏过滤 → Pin 排序。
 * 必须在后台线程调用，UI 只消费结果。
 */
fun QuickSearchIndex.searchOutcome(
    query: String,
    libraryEntries: List<ActionLibraryEntry>,
    launcherSettings: QuickAppLauncherSettings = QuickAppLauncherSettings(),
    searchSettings: QuickSearchSettings = QuickSearchSettings(),
    torchOn: Boolean = false,
): SearchResults {
    val outcome = search(query, libraryEntries, launcherSettings, searchSettings, torchOn)
    val raw = outcome.results
    if (raw.isEmpty()) return SearchResults(emptyList(), outcome.directive)
    val mapped = raw.map { it.toSearchResult() }
    val hasEngineFallback = raw.any { it is QuickSearchResult.EngineSearch }
    val rows = if (hasEngineFallback && !outcome.directive) {
        // 非指令态：默认引擎行统一由 buildEngineResultRows 产出（带图标文字与完整动作），
        // 索引层那张先丢掉，避免同一条引擎出两行。
        mapped.filterNot { it.source == SOURCE_ENGINE } +
            buildEngineResultRows(context, query.trim(), searchSettings)
    } else {
        mapped
    }
    return SearchResults(
        results = rows
            .applyResultPins(query, searchSettings),
        directive = outcome.directive,
    )
}
