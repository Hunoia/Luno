package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Keep
data class SearchEngine(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    /** 关键词替换 %s 的 URL 模板，如 https://www.bing.com/search?q=%s */
    val urlTemplate: String = "",
)

@Serializable
@Keep
data class QuickSearchSettings(
    val defaultEngineId: String = BuiltinSearchEngines.first().id,
    val customEngines: List<SearchEngine> = emptyList(),
)

/** 内置引擎：代码常量，可设默认、不可删改。 */
val BuiltinSearchEngines: List<SearchEngine> = listOf(
    SearchEngine(id = "bing", name = "Bing", urlTemplate = "https://www.bing.com/search?q=%s"),
    SearchEngine(id = "google", name = "Google", urlTemplate = "https://www.google.com/search?q=%s"),
    SearchEngine(id = "duckduckgo", name = "DuckDuckGo", urlTemplate = "https://duckduckgo.com/?q=%s"),
)

/** 内置 id 集合：管理页据此禁用删改。 */
val BuiltinSearchEngineIds: Set<String> = BuiltinSearchEngines.map { it.id }.toSet()

fun QuickSearchSettings.effectiveEngines(): List<SearchEngine> =
    BuiltinSearchEngines + customEngines

fun QuickSearchSettings.defaultEngine(): SearchEngine {
    val all = effectiveEngines()
    return all.firstOrNull { it.id == defaultEngineId } ?: all.first()
}
