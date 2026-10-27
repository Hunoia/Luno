package hunoia.luno.quicklaunch.query

import androidx.annotation.StringRes
import hunoia.luno.R
import hunoia.luno.core.domain.ActionLibraryEntry
import hunoia.luno.config.model.AppUsageStats
import hunoia.luno.config.model.QuickSearchSettings
import hunoia.luno.config.model.SRC_ACTION
import hunoia.luno.config.model.SRC_APP
import hunoia.luno.config.model.SRC_CALENDAR
import hunoia.luno.config.model.SRC_CALL
import hunoia.luno.config.model.SRC_CLIP
import hunoia.luno.config.model.SRC_CONTACT
import hunoia.luno.config.model.SRC_FILE
import hunoia.luno.config.model.SRC_MUSIC
import hunoia.luno.config.model.SRC_SETTINGS
import hunoia.luno.config.model.SRC_SHORTCUT
import hunoia.luno.config.model.SRC_SMS
import hunoia.luno.config.model.SRC_TOGGLE
import hunoia.luno.config.model.effectiveTrigger

/**
 * 搜索来源的唯一注册表：标识、开关、匹配与裸标识列表都在这里声明。
 * 新增来源只改本文件，不再改 parseDirective / 设置页提示列表的 when。
 */
internal data class SearchSource(
    val key: String,
    @param:StringRes val titleRes: Int,
    val enabled: (QuickSearchSettings) -> Boolean = { true },
    val match: (SearchContext) -> List<RankedHit>,
    val bare: (SearchContext) -> List<QuickSearchResult>?,
)

internal data class SearchContext(
    val index: QuickSearchIndex,
    val settings: QuickSearchSettings,
    val usage: AppUsageStats,
    val libraryEntries: List<ActionLibraryEntry>,
    val plan: SearchQueryPlan,
    val now: Long,
    val torchOn: Boolean,
)

internal object SearchSourceRegistry {

    private fun source(
        key: String,
        @StringRes titleRes: Int,
        enabled: (QuickSearchSettings) -> Boolean = { true },
        match: (SearchContext) -> List<RankedHit>,
        // 默认裸标识列表就是本来源的近项列表：key 由 source() 注入，避免每个来源手抄 SRC_X。
        bare: (SearchContext) -> List<QuickSearchResult>? = { c ->
            c.index.bareSource(key, c.usage, c.settings)
        },
    ) = SearchSource(
        key = key,
        titleRes = titleRes,
        enabled = enabled,
        match = match,
        bare = bare,
    )

    val all: List<SearchSource> = listOf(
        source(
            SRC_APP,
            R.string.quick_search_group_apps,
            enabled = { it.searchApps },
            match = { c -> c.index.matchApps(c.plan, c.usage, c.now) },
        ),
        source(
            SRC_SHORTCUT,
            R.string.quick_search_group_shortcuts,
            enabled = { it.searchShortcuts },
            match = { c ->
                c.index.matchShortcuts(
                    c.plan,
                    c.index.pkgScores(c.usage, c.now),
                )
            },
        ),
        source(
            SRC_ACTION,
            R.string.quick_search_group_actions,
            enabled = { it.searchActions },
            match = { c ->
                c.index.matchActions(c.plan) +
                    c.index.matchLibrary(c.libraryEntries, c.plan)
            },
        ),
        source(
            SRC_SETTINGS,
            R.string.quick_search_group_settings,
            enabled = { it.searchSettingsPages },
            match = { c -> c.index.matchSettingsPages(c.plan) },
        ),
        source(
            SRC_CONTACT,
            R.string.quick_search_contacts,
            enabled = { it.searchContacts },
            match = { c ->
                c.index.matchContacts(c.plan, c.settings)
            },
        ),
        source(
            SRC_MUSIC,
            R.string.quick_search_music,
            enabled = { it.searchMusic },
            match = { c -> c.index.matchTracks(c.plan, c.settings) },
        ),
        source(
            SRC_FILE,
            R.string.quick_search_files,
            enabled = { it.searchFiles },
            match = { c ->
                c.index.matchGallery(c.plan, c.settings) +
                    c.index.matchDocs(c.plan, c.settings)
            },
        ),
        source(
            SRC_CALENDAR,
            R.string.quick_search_calendar,
            enabled = { it.searchCalendar },
            match = { c -> c.index.matchCalendar(c.plan, c.settings) },
        ),
        source(
            SRC_SMS,
            R.string.quick_search_sms,
            enabled = { it.searchSms },
            match = { c -> c.index.matchSms(c.plan, c.settings) },
        ),
        source(
            SRC_CALL,
            R.string.quick_search_calls,
            enabled = { it.searchCalls },
            match = { c -> c.index.matchCalls(c.plan, c.settings) },
        ),
        source(
            SRC_TOGGLE,
            R.string.quick_search_toggles,
            enabled = { it.searchToggles },
            match = { c -> c.index.matchToggles(c.plan, c.settings) },
        ),
        source(
            SRC_CLIP,
            R.string.quick_search_clipboard,
            enabled = { it.searchClipboard },
            match = { c -> c.index.matchClips(c.plan, c.settings) },
        ),
    )

    private val byKey: Map<String, SearchSource> = all.associateBy { it.key }

    fun byTrigger(headLower: String, settings: QuickSearchSettings): SearchSource? {
        if (headLower.isEmpty()) return null
        return all.firstOrNull { source ->
            val trigger = settings.effectiveTrigger(source.key).trim().lowercase()
            trigger.isNotEmpty() && trigger == headLower
        }
    }

    @StringRes
    fun titleRes(key: String): Int = byKey[key]?.titleRes ?: R.string.quick_search_group_settings

    fun enabled(source: String, settings: QuickSearchSettings): Boolean {
        val entry = byKey[source] ?: return false
        return entry.enabled(settings)
    }

    fun match(source: String, context: SearchContext): List<RankedHit> {
        val entry = byKey[source] ?: return emptyList()
        if (!entry.enabled(context.settings)) return emptyList()
        return entry.match(context)
    }

    fun bare(source: String, context: SearchContext): List<QuickSearchResult>? {
        val entry = byKey[source] ?: return null
        if (!entry.enabled(context.settings)) return null
        return entry.bare(context)
    }
}
