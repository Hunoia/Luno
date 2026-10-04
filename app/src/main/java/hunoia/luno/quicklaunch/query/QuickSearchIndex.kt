package hunoia.luno.quicklaunch.query

import android.content.Context
import hunoia.luno.R
import hunoia.luno.action.api.ActionFacade
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.action.definitions.ParameterDefinition
import hunoia.luno.action.model.NewActionLibraryEntry
import hunoia.luno.action.model.SettingsPage
import hunoia.luno.action.model.StoredAction
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.ActionLibraryRefData
import hunoia.luno.config.model.NewActionData
import hunoia.luno.config.model.QuickAppLauncherSettings
import hunoia.luno.config.model.QuickSearchSettings
import hunoia.luno.config.model.VALUE_LIBRARY_REF
import hunoia.luno.config.model.VALUE_NEW_ACTION
import hunoia.luno.config.model.defaultEngine
import hunoia.luno.config.model.effectiveEngines
import hunoia.luno.core.JsonSerializer
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.LauncherInfo
import hunoia.luno.quicklaunch.query.AppSearch.key
import hunoia.luno.ui.component.resolvedIcon
import java.net.URLEncoder

/**
 * 快速搜索结果：一个可被 [ActionDispatcher] 执行的条目。
 * [toConfigAction] 把结果转回 [Action] 交给 [hunoia.luno.runtime.GestureHost.dispatchAction] 执行。
 */
sealed interface QuickSearchResult {
    val title: String
    val subtitle: String

    /** 内置动作（新模型 typeId + 参数） */
    data class ActionDef(val stored: StoredAction, override val title: String, val icon: Any?) : QuickSearchResult {
        override val subtitle: String get() = ""
    }
    /** 动作库条目引用 */
    data class LibraryEntry(val entry: NewActionLibraryEntry, val fallbackTitle: String, val icon: Any?) : QuickSearchResult {
        override val title: String get() = entry.name.ifBlank { fallbackTitle }
        override val subtitle: String get() = fallbackTitle
    }
    /** 启动应用（含禁用应用：点击走启用并启动流程） */
    data class App(val appInfo: AppInfo, val isDisabled: Boolean = false) : QuickSearchResult {
        override val title: String get() = appInfo.label
        override val subtitle: String get() = appInfo.packageName
    }
    /** 启动快捷方式 */
    data class Shortcut(val shortcut: LauncherInfo.ShortcutInfo) : QuickSearchResult {
        override val title: String get() = shortcut.label
        override val subtitle: String get() = shortcut.packageName
    }
    /** 打开系统设置页 */
    data class Settings(val page: SettingsPage, override val title: String) : QuickSearchResult {
        override val subtitle: String get() = ""
    }
    /** 打开网页 */
    data class Url(val url: String) : QuickSearchResult {
        override val title: String get() = url
        override val subtitle: String get() = ""
    }
    /** 搜索引擎兜底：title 在构造时由调用方按当前语言填好 */
    data class EngineSearch(val query: String, val engineTitle: String, val urlTemplate: String) : QuickSearchResult {
        override val title: String get() = engineTitle
        override val subtitle: String get() = query
    }
}

/**
 * 快速搜索索引：懒加载各数据源，按 query 的单字 token 做拼音/首字母/原文 tier 匹配。
 *
 * 加载分两层：[ensureLoaded] 只做无 IO 的定义表；[refreshApps] 查包（含禁用应用）
 * 必须在后台线程调，由 overlay 的 show() 触发，[search] 内保留兜底。
 */
class QuickSearchIndex(private val context: Context) {

    private var actions: List<QuickSearchResult.ActionDef> = emptyList()
    private var settingsPages: List<QuickSearchResult.Settings> = emptyList()
    private var apps: List<AppInfo> = emptyList()
    private var disabledPkgs: Set<String> = emptySet()
    private var shortcuts: List<LauncherInfo.ShortcutInfo> = emptyList()
    @Volatile private var defsLoaded = false
    @Volatile private var appsLoaded = false

    fun ensureLoaded() {
        if (defsLoaded) return
        defsLoaded = true
        actions = ActionDefinitions.pickerDefinitions().map { def ->
            QuickSearchResult.ActionDef(
                StoredAction(def.typeId),
                def.name,
                def.icon,
            )
        }
        settingsPages = systemSettingsPages()
    }

    fun refreshApps() {
        val combined = DisabledAppQuery.queryQuickAppLauncherApps(context)
        apps = combined.apps
        disabledPkgs = combined.disabledPkgs
        shortcuts = QuickLaunchFacade.queryShortcuts(context).flatMap { it.shortcuts }
        AppSearch.warmIndexes(apps)
        appsLoaded = true
    }

    /** 设置页 label 与动作选择页同源：从 system.openSettings 的 Enum options 动态取。 */
    private fun systemSettingsPages(): List<QuickSearchResult.Settings> {
        val def = ActionDefinitions.byTypeId("system.openSettings") ?: return emptyList()
        val enum = def.parameters.filterIsInstance<ParameterDefinition.Enum>()
            .firstOrNull { it.key == "page" } ?: return emptyList()
        return enum.options.mapNotNull { opt ->
            runCatching { SettingsPage.valueOf(opt.value) }.getOrNull()?.let {
                QuickSearchResult.Settings(it, opt.label)
            }
        }
    }

    /** 把 query 拆成单字 token（只保留字母数字），复用 AppSearch 拼音引擎。 */
    private fun tokenize(query: String): List<String> =
        query.lowercase().filter { it.isLetterOrDigit() }.map { it.toString() }

    private fun tierOf(text: String, tokens: List<String>): Int =
        AppSearch.matchTextTier(text, tokens)

    /** 与 AppSearch.sortApps 同款的使用频率分（对数压制 + 3 天半衰），key 与记录侧一致。 */
    private fun usageScore(key: String, settings: QuickAppLauncherSettings, now: Long): Double {
        val count = settings.launchCount[key] ?: 0L
        val recent = settings.recentLaunchTime[key] ?: 0L
        if (count <= 0L || recent <= 0L) return 0.0
        return AppSearch.decayedUsage(count, (now - recent) / 86400000.0)
    }

    fun search(
        query: String,
        libraryEntries: List<NewActionLibraryEntry>,
        launcherSettings: QuickAppLauncherSettings = QuickAppLauncherSettings(),
        searchSettings: QuickSearchSettings = QuickSearchSettings(),
    ): List<QuickSearchResult> {
        ensureLoaded()
        if (!appsLoaded) refreshApps()
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        // 快捷方式按所属包聚合该包的最大分，一次算好供 O(1) 查询。
        val pkgScore = mutableMapOf<String, Double>()
        for ((key, count) in launcherSettings.launchCount) {
            val pkg = key.substringBefore('/')
            val recent = launcherSettings.recentLaunchTime[key] ?: 0L
            if (count <= 0L || recent <= 0L) continue
            val score = AppSearch.decayedUsage(count, (now - recent) / 86400000.0)
            if (score > (pkgScore[pkg] ?: 0.0)) pkgScore[pkg] = score
        }

        val results = mutableListOf<Triple<QuickSearchResult, Int, Double>>()

        for (a in actions) {
            val tier = tierOf(a.title, tokens)
            if (tier >= 0) results.add(Triple(a, tier, 0.0))
        }
        for (entry in libraryEntries) {
            val def = ActionDefinitions.byTypeId(entry.typeId)
            val fallback = def?.name ?: entry.typeId
            val tier = maxOf(tierOf(entry.name, tokens), tierOf(fallback, tokens))
            if (tier >= 0) {
                results.add(
                    Triple(
                        QuickSearchResult.LibraryEntry(entry, fallback, entry.resolvedIcon()),
                        tier,
                        0.0,
                    ),
                )
            }
        }
        for (app in apps) {
            val tier = tierOf(app.label, tokens)
            if (tier >= 0) {
                results.add(
                    Triple(
                        QuickSearchResult.App(app, app.packageName in disabledPkgs),
                        tier,
                        usageScore(app.key(), launcherSettings, now),
                    ),
                )
            }
        }
        for (sc in shortcuts) {
            val tier = tierOf(sc.label, tokens)
            if (tier >= 0) {
                results.add(Triple(QuickSearchResult.Shortcut(sc), tier, pkgScore[sc.packageName] ?: 0.0))
            }
        }
        for (sp in settingsPages) {
            val tier = tierOf(sp.title, tokens)
            if (tier >= 0) results.add(Triple(sp, tier, 0.0))
        }

        val urlResult = urlResult(query)
        if (urlResult != null) results.add(Triple(urlResult, 3, 0.0))

        val sorted = results.sortedWith(
            compareByDescending<Triple<QuickSearchResult, Int, Double>> { it.second }
                // 同 tier 内精确意图（动作/设置页）在前，应用靠使用分个性化在后。
                // 动作类 usage 恒 0，否则永远被用过的应用压住，Check 会打中下面的
                // 应用而不是视觉更靠前的动作。
                .thenBy { typeRank(it.first) }
                .thenByDescending { it.third }
                .thenBy { it.first.title.length }
                .thenBy { it.first.title },
        ).map { it.first }

        // 搜索引擎兜底排最后：只有非 URL 关键词才有。标识直达优先（如 bing 新闻），
        // 否则用设置里的默认引擎。
        val engine = if (urlResult == null) {
            directedEngine(query.trim(), searchSettings) ?: run {
                val default = searchSettings.defaultEngine()
                QuickSearchResult.EngineSearch(
                    query.trim(),
                    context.getString(R.string.quick_search_engine_search, default.name),
                    default.urlTemplate,
                )
            }
        } else {
            null
        }
        return if (engine != null) sorted + engine else sorted
    }

    /**
     * 标识直达：“标识 + 空格 + 内容”（如 bing 新闻）用标识对应的引擎。
     * 大小写不敏感；无空格分隔、标识未知、有名无实（内容空）都返回 null 走默认兜底。
     */
    private fun directedEngine(query: String, settings: QuickSearchSettings): QuickSearchResult.EngineSearch? {
        val rest = query.substringAfter(' ', "")
        if (rest.isBlank()) return null
        val head = query.substringBefore(' ')
        val target = settings.effectiveEngines()
            .firstOrNull { it.trigger.isNotBlank() && it.trigger.equals(head, ignoreCase = true) }
            ?: return null
        return QuickSearchResult.EngineSearch(
            rest.trim(),
            context.getString(R.string.quick_search_engine_search, target.name),
            target.urlTemplate,
        )
    }

    /**
     * 同 tier 内的类型权重：小数字排前（列表首 = 视觉底部 = Check 目标）。
     * EngineSearch 实际不参与排序（追加在最后），分支只为 when 穷尽。
     */
    private fun typeRank(result: QuickSearchResult): Int = when (result) {
        is QuickSearchResult.Url -> 0
        is QuickSearchResult.ActionDef -> 1
        is QuickSearchResult.LibraryEntry -> 1
        is QuickSearchResult.Settings -> 1
        is QuickSearchResult.App -> 2
        is QuickSearchResult.Shortcut -> 2
        is QuickSearchResult.EngineSearch -> 3
    }

    private fun urlResult(query: String): QuickSearchResult.Url? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null
        val hasScheme = trimmed.contains("://")
        val looksLikeDomain = trimmed.contains('.') && !trimmed.contains(' ')
            && trimmed.matches(Regex("^[^\\s]+\\.[^\\s]{2,}.*$"))
        return if (hasScheme || looksLikeDomain) QuickSearchResult.Url(trimmed) else null
    }
}

/** 把搜索结果转回可执行的 [Action]。 */
fun QuickSearchResult.toConfigAction(): Action = when (this) {
    is QuickSearchResult.ActionDef -> Action(
        value = VALUE_NEW_ACTION,
        data = JsonSerializer.encodeToString(NewActionData(stored)),
    )
    is QuickSearchResult.LibraryEntry -> Action(
        value = VALUE_LIBRARY_REF,
        data = JsonSerializer.encodeToString(ActionLibraryRefData(entry.id)),
    )
    is QuickSearchResult.App -> Action(
        value = ActionFacade.EXTRA_LAUNCH_APP,
        data = JsonSerializer.encodeToString(appInfo),
    )
    is QuickSearchResult.Shortcut -> Action(
        value = ActionFacade.EXTRA_LAUNCH_SHORTCUT,
        data = JsonSerializer.encodeToString(shortcut),
    )
    is QuickSearchResult.Settings -> Action(
        value = VALUE_NEW_ACTION,
        data = JsonSerializer.encodeToString(NewActionData(StoredAction.of("system.openSettings", "page" to page.name))),
    )
    is QuickSearchResult.Url -> Action(
        value = VALUE_NEW_ACTION,
        data = JsonSerializer.encodeToString(NewActionData(StoredAction.of("intent.openUrl", "url" to url))),
    )
    is QuickSearchResult.EngineSearch -> Action(
        value = VALUE_NEW_ACTION,
        data = JsonSerializer.encodeToString(
            NewActionData(
                StoredAction.of(
                    "intent.openUrl",
                    "url" to urlTemplate.replace("%s", URLEncoder.encode(query, Charsets.UTF_8)),
                ),
            ),
        ),
    )
}
