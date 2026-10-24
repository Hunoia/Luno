package hunoia.luno.quicklaunch.query

import androidx.annotation.StringRes
import hunoia.luno.R
import hunoia.luno.action.model.ActionLibraryEntry
import hunoia.luno.config.model.QuickAppLauncherSettings
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
    val launcher: QuickAppLauncherSettings,
    val libraryEntries: List<ActionLibraryEntry>,
    val tokens: List<String>,
    val foldedTokens: List<String>,
    val now: Long,
    val torchOn: Boolean,
)

internal object SearchSourceRegistry {

    private fun source(
        key: String,
        @StringRes titleRes: Int,
        enabled: (QuickSearchSettings) -> Boolean = { true },
        match: (SearchContext) -> List<RankedHit>,
        bare: (SearchContext) -> List<QuickSearchResult>?,
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
            match = { c -> c.index.matchApps(c.tokens, c.foldedTokens, c.launcher, c.now) },
            bare = { c -> c.index.bareSource(SRC_APP, c.launcher, c.settings) },
        ),
        source(
            SRC_SHORTCUT,
            R.string.quick_search_group_shortcuts,
            enabled = { it.searchShortcuts },
            match = { c ->
                c.index.matchShortcuts(
                    c.tokens,
                    c.foldedTokens,
                    c.index.pkgScores(c.launcher, c.now),
                )
            },
            bare = { c -> c.index.bareSource(SRC_SHORTCUT, c.launcher, c.settings) },
        ),
        source(
            SRC_ACTION,
            R.string.quick_search_group_actions,
            enabled = { it.searchActions },
            match = { c ->
                c.index.matchActions(c.tokens, c.foldedTokens) +
                    c.index.matchLibrary(c.libraryEntries, c.tokens, c.foldedTokens)
            },
            bare = { c -> c.index.bareSource(SRC_ACTION, c.launcher, c.settings) },
        ),
        source(
            SRC_SETTINGS,
            R.string.quick_search_group_settings,
            enabled = { it.searchSettingsPages },
            match = { c -> c.index.matchSettingsPages(c.tokens, c.foldedTokens) },
            bare = { c -> c.index.bareSource(SRC_SETTINGS, c.launcher, c.settings) },
        ),
        source(
            SRC_CONTACT,
            R.string.quick_search_contacts,
            enabled = { it.searchContacts },
            match = { c ->
                c.index.matchContacts(c.tokens, c.foldedTokens, c.settings)
            },
            bare = { c -> c.index.bareSource(SRC_CONTACT, c.launcher, c.settings) },
        ),
        source(
            SRC_MUSIC,
            R.string.quick_search_music,
            enabled = { it.searchMusic },
            match = { c -> c.index.matchTracks(c.tokens, c.foldedTokens, c.settings) },
            bare = { c -> c.index.bareSource(SRC_MUSIC, c.launcher, c.settings) },
        ),
        source(
            SRC_FILE,
            R.string.quick_search_files,
            enabled = { it.searchFiles },
            match = { c ->
                c.index.matchGallery(c.tokens, c.foldedTokens, c.settings) +
                    c.index.matchDocs(c.tokens, c.foldedTokens, c.settings)
            },
            bare = { c -> c.index.bareSource(SRC_FILE, c.launcher, c.settings) },
        ),
        source(
            SRC_CALENDAR,
            R.string.quick_search_calendar,
            enabled = { it.searchCalendar },
            match = { c -> c.index.matchCalendar(c.tokens, c.foldedTokens, c.settings) },
            bare = { c -> c.index.bareSource(SRC_CALENDAR, c.launcher, c.settings) },
        ),
        source(
            SRC_SMS,
            R.string.quick_search_sms,
            enabled = { it.searchSms },
            match = { c -> c.index.matchSms(c.tokens, c.foldedTokens, c.settings) },
            bare = { c -> c.index.bareSource(SRC_SMS, c.launcher, c.settings) },
        ),
        source(
            SRC_CALL,
            R.string.quick_search_calls,
            enabled = { it.searchCalls },
            match = { c -> c.index.matchCalls(c.tokens, c.foldedTokens, c.settings) },
            bare = { c -> c.index.bareSource(SRC_CALL, c.launcher, c.settings) },
        ),
        source(
            SRC_TOGGLE,
            R.string.quick_search_toggles,
            enabled = { it.searchToggles },
            match = { c -> c.index.matchToggles(c.tokens, c.foldedTokens, c.settings) },
            bare = { c -> c.index.bareSource(SRC_TOGGLE, c.launcher, c.settings) },
        ),
        source(
            SRC_CLIP,
            R.string.quick_search_clipboard,
            enabled = { it.searchClipboard },
            match = { c -> c.index.matchClips(c.tokens, c.foldedTokens, c.settings) },
            bare = { c -> c.index.bareSource(SRC_CLIP, c.launcher, c.settings) },
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
