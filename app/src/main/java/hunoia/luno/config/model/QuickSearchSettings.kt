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
    /**
     * 直达标识：搜索输入“标识 + 空格 + 内容”时用该引擎（如 bing 新闻）。
     * 空 = 不启用；大小写不敏感；自定义引擎在管理页设置。
     */
    val trigger: String = "",
)

@Serializable
@Keep
data class QuickSearchSettings(
    val defaultEngineId: String = BuiltinSearchEngines.first().id,
    val customEngines: List<SearchEngine> = emptyList(),
)

/** 内置引擎：代码常量，可设默认、不可删改；trigger 预置同样不可改。 */
val BuiltinSearchEngines: List<SearchEngine> = listOf(
    SearchEngine(id = "bing", name = "Bing", urlTemplate = "https://www.bing.com/search?q=%s", trigger = "bing"),
    SearchEngine(id = "google", name = "Google", urlTemplate = "https://www.google.com/search?q=%s", trigger = "google"),
    SearchEngine(id = "duckduckgo", name = "DuckDuckGo", urlTemplate = "https://duckduckgo.com/?q=%s", trigger = "ddg"),
)

/** 内置 id 集合：管理页据此禁用删改。 */
val BuiltinSearchEngineIds: Set<String> = BuiltinSearchEngines.map { it.id }.toSet()

fun QuickSearchSettings.effectiveEngines(): List<SearchEngine> =
    BuiltinSearchEngines + customEngines

fun QuickSearchSettings.defaultEngine(): SearchEngine {
    val all = effectiveEngines()
    return all.firstOrNull { it.id == defaultEngineId } ?: all.first()
}
