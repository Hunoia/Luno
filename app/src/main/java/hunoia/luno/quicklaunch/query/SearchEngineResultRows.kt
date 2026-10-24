package hunoia.luno.quicklaunch.query

import android.content.Context
import hunoia.luno.R
import hunoia.luno.config.model.QuickSearchSettings
import hunoia.luno.config.model.SearchEngine
import hunoia.luno.config.model.SearchLaunch
import hunoia.luno.config.model.badgeText
import hunoia.luno.config.model.copyUrl
import hunoia.luno.config.model.primaryEngineOrNull
import hunoia.luno.config.model.effectiveEngines
import hunoia.luno.config.model.effectiveLaunch

/**
 * 默认引擎行；由搜索流在后台线程调用。
 * 换引擎入口已由面板引擎条与长按 Sheet 的「用其他引擎搜索」覆盖，
 * 列表不再出「其他引擎」聚合行（原 es-other，与引擎条功能重复）。
 */
internal fun buildEngineResultRows(
    context: Context,
    query: String,
    settings: QuickSearchSettings,
): List<SearchResult> {
    val engines = settings.effectiveEngines()
    if (engines.isEmpty()) return emptyList()
    val primary = settings.primaryEngineOrNull() ?: return emptyList()
    return listOf(engineResultRow(context, query, primary))
}

/**
 * 单个引擎的结果行：默认行与「标识直达」行共用这一份。
 * key 固定为 `es#<引擎id>|<query>`，两条路径产出的行才能完全一致
 * （图标文字、复制链接、分享图片、用其他引擎搜索）。
 */
internal fun engineResultRow(
    context: Context,
    query: String,
    engine: SearchEngine,
): SearchResult = engineResultRow(
    title = context.getString(R.string.quick_search_engine_search, engine.name),
    query = query,
    engine = engine,
)

/** 不依赖 Context 的版本：标题由调用方给（索引层已有解析好的标题）。 */
internal fun engineResultRow(
    title: String,
    query: String,
    engine: SearchEngine,
): SearchResult {
    val launch = engine.effectiveLaunch()
    val copy = launch.copyUrl(query)
    val actions = buildList {
        copy?.let {
            add(
                SearchActionItem(
                    SearchActionLabel.Res(R.string.search_action_copy_link),
                    SearchAction.Copy(it),
                ),
            )
        }
        if (launch is SearchLaunch.ShareImage) {
            add(
                SearchActionItem(
                    SearchActionLabel.Res(R.string.search_action_share_image),
                    SearchAction.PickImage(launch.packageName),
                ),
            )
        }
        add(
            SearchActionItem(
                SearchActionLabel.Res(R.string.search_action_use_other_engine),
                SearchAction.ShowEngines(query),
            ),
        )
    }
    return SearchResult(
        key = "es#${engine.id}|$query",
        source = SOURCE_ENGINE,
        title = title,
        subtitle = "",
        icon = SearchIcon.Text(engine.badgeText),
        primary = launch.toSearchAction(query),
        actions = actions,
    )
}
