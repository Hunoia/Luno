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
    val defaultEngineId: String = "duckduckgo",
    val customEngines: List<SearchEngine> = emptyList(),
    /** 最近确认的搜索词（去重 cap 50）：空输入时展示，点即回填。 */
    val recentQueries: List<String> = emptyList(),
    /** 剪贴板行：空输入时展示当前剪贴文本，点即回填。 */
    val searchClipboard: Boolean = true,
    /** 联系人：姓名/号码匹配，点拨号、长按短信；需 READ_CONTACTS。 */
    val searchContacts: Boolean = true,
    /** 音乐：标题/艺术家匹配，点直接播；需 READ_MEDIA_AUDIO。 */
    val searchMusic: Boolean = true,
    /** 文件：图片/视频/文档匹配，点开、长按分享；图片视频需媒体权限，文档需 all-files。 */
    val searchFiles: Boolean = true,
    /** 日历：标题/地点匹配，点看事件、长按复制；需 READ_CALENDAR。 */
    val searchCalendar: Boolean = true,
    /** 快捷开关：WiFi/蓝牙/手电/勿扰/定位/热点，能直切就切、否则跳设置；无敏感权限。 */
    val searchToggles: Boolean = true,
    /** 短信：发件人/正文匹配，点开会话、长按复制；需 READ_SMS。 */
    val searchSms: Boolean = true,
    /** 通话记录：姓名/号码匹配，点拨号、长按短信；需 READ_CALL_LOG。 */
    val searchCalls: Boolean = true,
    /** 剪贴板历史（去重 cap 50）：空输入时多行展示，点回填、长按写回；总开关复用 searchClipboard。 */
    val clipHistory: List<String> = emptyList(),
    /** 固定剪贴（无上限，存全文）：空页标识页最贴胶囊、搜索态固定源内置顶；和 clipHistory 互不交叉。 */
    val pinnedClips: List<String> = emptyList(),
    /**
     * 来源标识自定义（源 key → 标识，如 "sms" → "duanxin"）：缺 key 用内置默认，
     * 空串 = 关闭该源标识（只走全源搜）。跟引擎标识同规则，保存时判重。
     */
    val sourceTriggers: Map<String, String> = emptyMap(),
)

/** 历史缓存上限；空输入只展最近 [BLANK_RECENT_LIMIT] 条，搜索时匹配的全部参展。 */
const val MAX_RECENT_QUERIES = 50
const val BLANK_RECENT_LIMIT = 3
/** 历史只记 trim 后长度达标的：短词（指令缩写/单字）不进历史，展示侧同样过滤。 */
const val MIN_HISTORY_CHARS = 6
/** 剪贴板历史上限（聚焦读到新文本才记，去重）。 */
const val MAX_CLIP_HISTORY = 50
/**
 * 搜索框/历史入库上限：MB 级文本进 TextField 会在主线程量数秒（ANR）。
 * 输入框超长直接拦，回填截断到此，历史超长丢弃。
 */
const val MAX_QUERY_CHARS = 2000
/**
 * 剪贴入库上限：富文本 clip（HTML 内嵌 base64 图片）转文本可达 MB 级，
 * 记历史纯占 DataStore 且无法匹配（匹配只看前 64 字），超长直接跳过；
 * 固定是用户显式动作，保留全文（长按原样复制语义不断）。
 */
const val MAX_CLIP_STORE_CHARS = 100 * 1024

/** 内置引擎：代码常量，可设默认、不可删改；trigger 预置同样不可改（短缩写：bb/gg/ddg）。 */
val BuiltinSearchEngines: List<SearchEngine> = listOf(
    SearchEngine(id = "bing", name = "Bing", urlTemplate = "https://www.bing.com/search?q=%s", trigger = "bb"),
    SearchEngine(id = "google", name = "Google", urlTemplate = "https://www.google.com/search?q=%s", trigger = "gg"),
    SearchEngine(id = "duckduckgo", name = "DuckDuckGo", urlTemplate = "https://duckduckgo.com/?q=%s", trigger = "ddg"),
)

/** 内置 id 集合：管理页据此禁用删改。 */
val BuiltinSearchEngineIds: Set<String> = BuiltinSearchEngines.map { it.id }.toSet()

fun QuickSearchSettings.effectiveEngines(): List<SearchEngine> =
    BuiltinSearchEngines + customEngines

/** 来源 key（稳定，存 sourceTriggers 用）：跟 typeRank/设置开关一一对应。 */
const val SRC_APP = "app"
const val SRC_SHORTCUT = "sc"
const val SRC_ACTION = "act"
const val SRC_SETTINGS = "set"
const val SRC_CONTACT = "con"
const val SRC_MUSIC = "mus"
const val SRC_FILE = "fil"
const val SRC_CALENDAR = "cal"
const val SRC_SMS = "sms"
const val SRC_CALL = "call"
const val SRC_TOGGLE = "sw"
const val SRC_CLIP = "clp"

/** 来源内置默认标识：全英文短缩写（用户嫌中英混排，要求统一）。 */
val DefaultSourceTriggers: Map<String, String> = mapOf(
    SRC_APP to "app",
    SRC_SHORTCUT to "sc",
    SRC_ACTION to "act",
    SRC_SETTINGS to "set",
    SRC_CONTACT to "con",
    SRC_MUSIC to "mus",
    SRC_FILE to "fil",
    SRC_CALENDAR to "cal",
    SRC_SMS to "sms",
    SRC_CALL to "call",
    SRC_TOGGLE to "sw",
    SRC_CLIP to "clp",
)

/** 生效标识：自定义优先，缺 key 用默认，空串表示关闭（调用方跳过空串）。 */
fun QuickSearchSettings.effectiveTrigger(source: String): String =
    sourceTriggers[source] ?: DefaultSourceTriggers[source].orEmpty()

fun QuickSearchSettings.defaultEngine(): SearchEngine {
    val all = effectiveEngines()
    return all.firstOrNull { it.id == defaultEngineId } ?: all.first()
}
