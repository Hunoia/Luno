package hunoia.luno.config.model

import hunoia.luno.core.domain.StoredAction
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import java.net.URLEncoder

/**
 * 搜索引擎的打开方式。设置里只持久化这个描述，运行时再构建 Intent/Action。
 */
@Serializable
sealed interface SearchLaunch {
    /** 浏览器/系统默认；[packageName] 非空时强制指定应用，失败回退 [fallbackTemplate]。 */
    @Serializable
    data class Url(
        val template: String,
        val packageName: String = "",
        val fallbackTemplate: String = "",
        /** 用 Custom Tabs 打开（[packageName] 为空时生效），否则系统浏览器/ACTION_VIEW。 */
        val useCustomTabs: Boolean = false,
        /**
         * 用 Luno 小窗卡片（WebView）打开，优先级高于 [useCustomTabs] 与指定应用：
         * 卡片即用即关，长按页面图片可保存。设置页只在默认浏览器模式展示该开关。
         */
        val useSmallWindow: Boolean = false,
    ) : SearchLaunch

    /** 每次弹出系统选择器。 */
    @Serializable
    data class Chooser(val template: String) : SearchLaunch

    /** 分享文本到指定应用（ACTION_SEND text/plain）；[textTemplate] 支持 %q。 */
    @Serializable
    data class Share(
        val textTemplate: String = "%q",
        val packageName: String = "",
    ) : SearchLaunch

    /** 分享图片到指定应用；图片由执行时选择/剪贴板提供。 */
    @Serializable
    data class ShareImage(val packageName: String) : SearchLaunch

    /** 高级：任意动作模板，字符串参数里的 %s / %q 会替换成查询词。 */
    @Serializable
    data class Action(
        val action: StoredAction,
        val copyTemplate: String = "",
    ) : SearchLaunch

    /**
     * 跳转到目标并注入查询词（参考 GestureEVO 的 JUMP_TO_ACTIVITY / EXTERN_JUMP_LINK）：
     * [targetActivity] 非空 → 打开该 Activity；否则打开 [externLink]（在 [targetPackage] 里）。
     * 打开后把查询词注入焦点输入框，[autoInputEnter] 时再按一次 IME 回车。
     */
    @Serializable
    data class Activity(
        val targetPackage: String,
        val targetActivity: String = "",
        val externLink: String = "",
        val autoInputEnter: Boolean = false,
    ) : SearchLaunch
}

fun SearchEngine.effectiveLaunch(): SearchLaunch =
    launch ?: SearchLaunch.Url(urlTemplate)

/** 查询词填入模板：%s = URL 编码，%q = 原词。 */
private const val RAW_QUERY_TOKEN = "\u0000LUNO_RAW_QUERY\u0000"

fun String.fillSearchQuery(query: String): String =
    replace("%q", RAW_QUERY_TOKEN)
        .replace("%s", URLEncoder.encode(query, Charsets.UTF_8))
        .replace(RAW_QUERY_TOKEN, query)

/** 长按复制用的 URL；Action 没有可复制链接时返回 null，Share 无链接。 */
fun SearchLaunch.copyUrl(query: String): String? = when (this) {
    is SearchLaunch.Url -> template.fillSearchQuery(query)
    is SearchLaunch.Chooser -> template.fillSearchQuery(query)
    is SearchLaunch.Share -> null
    is SearchLaunch.ShareImage -> null
    is SearchLaunch.Activity -> null
    is SearchLaunch.Action -> {
        val fromTemplate = copyTemplate.takeIf { it.isNotBlank() }?.fillSearchQuery(query)
        fromTemplate ?: (action.params["url"] as? JsonPrimitive)
            ?.contentOrNull
            ?.fillSearchQuery(query)
    }
}

/** 把 %s 替换进动作参数字符串；非字符串原样保留。 */
fun StoredAction.fillSearchQuery(query: String): StoredAction = copy(
    params = buildJsonObject {
        params.forEach { (key, value) -> put(key, value.fillSearchQuery(query)) }
    },
)

private fun JsonElement.fillSearchQuery(query: String): JsonElement = when (this) {
    is JsonPrimitive -> if (isString) JsonPrimitive(content.fillSearchQuery(query)) else this
    is JsonObject -> JsonObject(mapValues { (_, value) -> value.fillSearchQuery(query) })
    is JsonArray -> JsonArray(map { it.fillSearchQuery(query) })
}
