package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Keep
data class SearchEngine(
    /**
     * 引擎标识（排序/隐藏/覆盖都按它索引）。
     * 空 = 还没分配：旧备份与导入数据解码后由 [withUniqueEngineTriggers] 按内容派生
     * 稳定 id（不能用随机 UUID——每次解码都变会让顺序/隐藏/覆盖全部失效）。
     */
    val id: String = "",
    val name: String = "",
    /** 关键词替换 %s 的 URL 模板，如 https://www.bing.com/search?q=%s */
    val urlTemplate: String = "",
    /**
     * 直达标识：搜索输入“标识 + 空格 + 内容”时用该引擎（如 bing 新闻）。
     * 空 = 不启用；大小写不敏感；自定义引擎在管理页设置。
     */
    val trigger: String = "",
    /** 新打开方式；null = 旧数据，按 [effectiveLaunch] 回退到 urlTemplate。 */
    val launch: SearchLaunch? = null,
    /** 引擎图标文字（1~2 字）；空 = 取名称首字。 */
    val iconText: String = "",
)

@Serializable
@Keep
data class QuickSearchSettings(
    /**
     * 旧「默认引擎」遗留字段：主引擎已改为「列表第一位」（拖拽排序决定），
     * 归一化时把旧默认引擎一次性挪到顺序首位后清空，仅为迁移保留，勿再读写。
     */
    val defaultEngineId: String = "",
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
    /** 应用：应用名/拼音匹配；关闭后标识直达与全源匹配一并停。 */
    val searchApps: Boolean = true,
    /** 快捷方式：已声明的 App Shortcut 匹配。 */
    val searchShortcuts: Boolean = true,
    /** 动作：动作库条目匹配。 */
    val searchActions: Boolean = true,
    /** 设置页：Luno 自身设置项匹配。 */
    val searchSettingsPages: Boolean = true,
    /** 剪贴板历史（去重 cap 50）：空输入时多行展示，点回填、长按写回；总开关复用 searchClipboard。 */
    val clipHistory: List<String> = emptyList(),
    /** 固定剪贴（无上限，存全文）：空页标识页最贴胶囊、搜索态固定源内置顶；和 clipHistory 互不交叉。 */
    val pinnedClips: List<String> = emptyList(),
    /**
     * 来源标识自定义（源 key → 标识，如 "sms" → "duanxin"）：缺 key 用内置默认，
     * 空串 = 关闭该源标识（只走全源搜）。跟引擎标识同规则，保存时判重。
     */
    val sourceTriggers: Map<String, String> = emptyMap(),
    /** 引擎显示顺序（含内置 id）；空 = 内置 + 自定义默认顺序。 */
    val engineOrder: List<String> = emptyList(),
    /** 从快速搜索/引擎条隐藏的引擎 id。 */
    val hiddenEngineIds: List<String> = emptyList(),
    /** 内置引擎的打开方式覆盖；自定义引擎继续用 SearchEngine.launch。 */
    val engineLaunchOverrides: Map<String, SearchLaunch> = emptyMap(),
    /** 全局隐藏的结果 key，cap [MAX_HIDDEN_RESULTS]。 */
    val hiddenResultKeys: List<String> = emptyList(),
    /** 隐藏结果的展示标题（key → 标题），只为设置页可读，随 [hiddenResultKeys] 一起清。 */
    val hiddenResultLabels: Map<String, String> = emptyMap(),
    /** 隐藏时间戳（key → epoch millis）：设置页按时间倒序展示；旧数据缺时间按列表顺序兜底。 */
    val hiddenResultTimes: Map<String, Long> = emptyMap(),
    /** query（trim + lowercase）→ 固定结果 key 顺序，cap [MAX_PINNED_QUERIES] 个 query。 */
    val pinnedResults: Map<String, List<String>> = emptyMap(),
    /** 最近使用过的 Pin query，用于容量淘汰（新的在前：写侧前插，淘汰时丢尾部）。 */
    val pinnedQueryOrder: List<String> = emptyList(),
    /** 上次查询草稿；restoreLastQuery 开启时面板打开即回填。 */
    val lastQuery: String = "",
    val restoreLastQuery: Boolean = true,
    /**
     * 回车即搜索（英文键盘）：开 = KeyboardType.Ascii，回车直达确认但输入法锁英文；
     * 关（默认）= 中文输入法可用，回车交给输入法，确认走右侧按钮/引擎条。
     */
    val asciiEnterSearch: Boolean = false,
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

/** 引擎图标文字；空 = 取名称首字。引擎条/引擎行/选择 Sheet 共用同一取值。 */
val SearchEngine.badgeText: String
    get() = iconText.takeIf { it.isNotBlank() } ?: name.firstOrNull()?.toString().orEmpty()

/** 内置 id 集合：管理页据此禁用删除（打开方式仍可覆盖）。 */
val BuiltinSearchEngineIds: Set<String> = BuiltinSearchEngines.map { it.id }.toSet()

/** Pin/Hide 容量：全部有上限，避免 DataStore 随使用无限膨胀。 */
const val MAX_HIDDEN_RESULTS = 300
const val MAX_PINNED_QUERIES = 80
const val MAX_PINS_PER_QUERY = 20

/** 全部引擎：应用内置覆盖 + 用户顺序，包含被隐藏项（设置页管理用）。 */
fun QuickSearchSettings.allEngines(): List<SearchEngine> {
    val base = (BuiltinSearchEngines + customEngines).map { engine ->
        engineLaunchOverrides[engine.id]?.let { engine.copy(launch = it) } ?: engine
    }
    if (engineOrder.isEmpty()) return base
    // 用列表删除而不是 associateBy：id 可能为空/重复（未归一化的旧数据），
    // associateBy 会把同 id 的引擎互相顶掉。
    val rest = base.toMutableList()
    val ordered = ArrayList<SearchEngine>(base.size)
    for (id in engineOrder) {
        val index = rest.indexOfFirst { it.id == id }
        if (index >= 0) ordered.add(rest.removeAt(index))
    }
    ordered.addAll(rest)
    return ordered
}

/**
 * 引擎 id 缺失时按内容派生稳定 id：同一条引擎每次解码都得到同一个 id，
 * 顺序/隐藏/覆盖的引用才不会因为一次解码就全失效。已落库的非空 id 永不改动。
 */
private fun derivedEngineId(engine: SearchEngine): String =
    "ce-" + UUID.nameUUIDFromBytes(
        "${engine.name}\u0001${engine.trigger}\u0001${engine.urlTemplate}".toByteArray(Charsets.UTF_8),
    )

/** 给自定义引擎补齐缺失/冲突的 id：内容派生 + 重名后缀，顺序稳定。 */
private fun assignEngineIds(engines: List<SearchEngine>): List<SearchEngine> {
    val used = HashSet<String>(BuiltinSearchEngineIds)
    return engines.map { engine ->
        if (engine.id.isNotBlank() && used.add(engine.id)) return@map engine
        val derived = derivedEngineId(engine)
        var candidate = derived
        var suffix = 2
        while (!used.add(candidate)) {
            candidate = "$derived-$suffix"
            suffix++
        }
        engine.copy(id = candidate)
    }
}

/** 可见引擎：排序后过滤隐藏项，供结果行/标识/空态提示用。 */
fun QuickSearchSettings.effectiveEngines(): List<SearchEngine> =
    allEngines().filterNot { it.id in hiddenEngineIds }

/**
 * 完整顺序里把 [fromId] 移到 [toId] 的槽位（拖拽与菜单上移/下移共用）：
 * 下移落在 [toId] 之后、上移落在其前——removeAt 后插入下标 [toId] 恰好两种方向都对。
 */
fun moveEngineBetween(order: List<String>, fromId: String, toId: String): List<String> {
    val from = order.indexOf(fromId)
    val to = order.indexOf(toId)
    if (from < 0 || to < 0 || from == to) return order
    return order.toMutableList().apply {
        removeAt(from)
        add(to, fromId)
    }
}

/**
 * 统一归一化：清洗标识、引擎顺序/隐藏/覆盖、Pin/Hide 容量与草稿。
 * 读 settings 与写入前都会调用，旧数据/导入数据无需迁移即可用。
 */
fun QuickSearchSettings.withUniqueEngineTriggers(): QuickSearchSettings {
    // 先补 id：后面的顺序/隐藏/覆盖都按 id 索引，id 缺失时这些引用无处落地。
    val idFixed = assignEngineIds(customEngines)
    var changed = idFixed != customEngines
    val all = (BuiltinSearchEngines + idFixed).map { engine ->
        engineLaunchOverrides[engine.id]?.let { engine.copy(launch = it) } ?: engine
    }
    val knownIds = all.mapTo(mutableSetOf()) { it.id }
    val used = BuiltinSearchEngines.mapTo(mutableSetOf()) { it.trigger.lowercase() }
    val kept = arrayOfNulls<SearchEngine>(idFixed.size)
    for (index in idFixed.indices.reversed()) {
        val engine = idFixed[index]
        val trigger = engine.trigger.trim()
        val normalized = if (trigger == engine.trigger) engine else engine.copy(trigger = trigger)
        if (trigger.isEmpty()) {
            kept[index] = normalized
            if (normalized !== engine) changed = true
        } else if (used.add(trigger.lowercase())) {
            kept[index] = normalized
            if (normalized !== engine) changed = true
        } else {
            kept[index] = engine.copy(trigger = "")
            changed = true
        }
    }
    val normalizedCustom = kept.filterNotNull()

    val normalizedOrder = engineOrder.filter { it in knownIds }.distinct()
    if (normalizedOrder.size != engineOrder.size) changed = true

    val normalizedHidden = hiddenEngineIds.filter { it in knownIds }.distinct()
    if (normalizedHidden.size != hiddenEngineIds.size) changed = true
    // 至少保留一个可见引擎：全隐藏时恢复默认/首个引擎。
    val visibleIds = knownIds - normalizedHidden.toSet()
    val safeHidden = if (visibleIds.isEmpty() && knownIds.isNotEmpty()) {
        changed = true
        emptyList()
    } else {
        normalizedHidden
    }

    val normalizedOverrides = engineLaunchOverrides.filterKeys { it in knownIds }
    if (normalizedOverrides.size != engineLaunchOverrides.size) changed = true

    // 旧「默认引擎」一次性迁移：把默认引擎挪到顺序首位后清空字段，
    // 此后主引擎 = 列表第一位（拖拽排序决定）。默认引擎已被隐藏时不迁移、直接清。
    val legacyDefault = defaultEngineId.takeIf { it.isNotBlank() && it in knownIds && it !in safeHidden }
    val orderWithLegacy = if (legacyDefault == null) {
        normalizedOrder
    } else {
        val base = normalizedOrder.ifEmpty { all.map { it.id } }
        if (base.firstOrNull() == legacyDefault) {
            base
        } else {
            base.toMutableList().apply {
                remove(legacyDefault)
                add(0, legacyDefault)
            }
        }
    }
    if (orderWithLegacy != engineOrder || defaultEngineId.isNotEmpty()) changed = true

    val normalizedHiddenResults = hiddenResultKeys.distinct().takeLast(MAX_HIDDEN_RESULTS)
    if (normalizedHiddenResults.size != hiddenResultKeys.size) changed = true

    // 隐藏标题：只留还在隐藏列表里的 key，空标题回落成 key 本身。
    val normalizedLabels = hiddenResultLabels
        .filterKeys { it in normalizedHiddenResults }
        .mapValues { (key, title) -> title.ifBlank { key } }
    if (normalizedLabels != hiddenResultLabels) changed = true

    val normalizedTimes = hiddenResultTimes.filterKeys { it in normalizedHiddenResults }
    if (normalizedTimes.size != hiddenResultTimes.size) changed = true

    val normalizedPinOrder = run {
        val keys = pinnedResults.filterValues { it.isNotEmpty() }.keys
        // pinnedQueryOrder 是「新的在前」（写侧前插），所以淘汰丢尾部，
        // 取 take(MAX) 而不是 takeLast——否则刚 pin 的 query 会被立刻淘汰。
        val ordered = pinnedQueryOrder.filter { it in keys }.distinct() +
            keys.filterNot { it in pinnedQueryOrder }
        ordered.take(MAX_PINNED_QUERIES)
    }
    val normalizedPins = normalizedPinOrder.associateWith { query ->
        pinnedResults[query].orEmpty().distinct().take(MAX_PINS_PER_QUERY)
    }.filterValues { it.isNotEmpty() }
    if (normalizedPins != pinnedResults || normalizedPinOrder != pinnedQueryOrder) changed = true

    val normalizedDraft = lastQuery.take(MAX_QUERY_CHARS)
    if (normalizedDraft != lastQuery) changed = true

    return if (!changed) this else copy(
        customEngines = normalizedCustom,
        engineOrder = orderWithLegacy,
        hiddenEngineIds = safeHidden,
        engineLaunchOverrides = normalizedOverrides,
        hiddenResultKeys = normalizedHiddenResults,
        hiddenResultLabels = normalizedLabels,
        hiddenResultTimes = normalizedTimes,
        pinnedResults = normalizedPins,
        pinnedQueryOrder = normalizedPinOrder,
        lastQuery = normalizedDraft,
        defaultEngineId = "",
    )
}

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

/** 来源内置默认标识：统一全英文短缩写。 */
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

/** 主引擎：可见引擎列表第一位（顺序由设置页拖拽决定）；用户不再单独选默认。 */
fun QuickSearchSettings.primaryEngineOrNull(): SearchEngine? = effectiveEngines().firstOrNull()

/** 兼容旧调用：没有可见引擎时回退到全部引擎首个。 */
fun QuickSearchSettings.primaryEngine(): SearchEngine =
    primaryEngineOrNull() ?: allEngines().first()
