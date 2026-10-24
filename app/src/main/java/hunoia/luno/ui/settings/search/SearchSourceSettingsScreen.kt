package hunoia.luno.ui.settings.search

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
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
import hunoia.luno.config.model.allEngines
import hunoia.luno.config.model.effectiveTrigger
import hunoia.luno.domain.search.SearchCenter
import hunoia.luno.quicklaunch.query.SearchSourceRegistry
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import kotlinx.coroutines.launch

/** 来源 key → 开关字段读取：全部来源都可开关（应用/动作等不再常开）。 */
private val SourceSwitches: Map<String, (QuickSearchSettings) -> Boolean> = mapOf(
    SRC_APP to { it.searchApps },
    SRC_SHORTCUT to { it.searchShortcuts },
    SRC_ACTION to { it.searchActions },
    SRC_SETTINGS to { it.searchSettingsPages },
    SRC_CONTACT to { it.searchContacts },
    SRC_SMS to { it.searchSms },
    SRC_CALL to { it.searchCalls },
    SRC_MUSIC to { it.searchMusic },
    SRC_FILE to { it.searchFiles },
    SRC_CLIP to { it.searchClipboard },
    SRC_CALENDAR to { it.searchCalendar },
    SRC_TOGGLE to { it.searchToggles },
)

/** 内容源与来源标识设置：按类型分组，组内开关行（开关 = 启用，点行 = 改标识）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSourceSettingsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val settings by SearchCenter.search.collectAsStateWithLifecycle(initialValue = QuickSearchSettings())
    // 来源标识编辑目标（源 key）：点行开 dialog，开关仍可独立切换。
    var triggerTarget by remember { mutableStateOf<String?>(null) }
    fun update(transform: (QuickSearchSettings) -> QuickSearchSettings) {
        scope.launch { SearchCenter.updateSearch(transform) }
    }
    triggerTarget?.let { key ->
        SourceTriggerDialog(
            title = sourceTitle(key),
            current = settings.effectiveTrigger(key),
            // 跟引擎标识 + 其他来源标识判重（自己除外）。
            existingTriggers = settings.allEngines().map { it.trigger } +
                SearchSourceRegistry.all.map { it.key }
                    .filter { it != key }
                    .map { settings.effectiveTrigger(it) },
            onDismissRequest = { triggerTarget = null },
            onConfirm = { v ->
                update { current ->
                    current.copy(sourceTriggers = current.sourceTriggers + (key to v))
                }
                triggerTarget = null
            },
        )
    }
    LunoScreen(
        title = stringResource(R.string.search_source_settings),
        onBack = onBack,
    ) {
        val groups = listOf(
            R.string.search_source_group_apps to listOf(SRC_APP, SRC_SHORTCUT, SRC_ACTION, SRC_SETTINGS),
            R.string.search_source_group_comms to listOf(SRC_CONTACT, SRC_SMS, SRC_CALL),
            R.string.search_source_group_media to listOf(SRC_MUSIC, SRC_FILE, SRC_CLIP),
            R.string.search_source_group_system to listOf(SRC_CALENDAR, SRC_TOGGLE),
        )
        groups.forEach { (titleRes, keys) ->
            LunoGroup(title = stringResource(titleRes)) {
                keys.forEachIndexed { index, key ->
                    LunoSwitchRow(
                        title = sourceTitle(key),
                        checked = SourceSwitches.getValue(key)(settings),
                        subtitle = settings.effectiveTrigger(key)
                            .ifBlank { stringResource(R.string.quick_search_trigger_closed) },
                        onClick = { triggerTarget = key },
                        onCheckedChange = { v ->
                            update { current ->
                                when (key) {
                                    SRC_APP -> current.copy(searchApps = v)
                                    SRC_SHORTCUT -> current.copy(searchShortcuts = v)
                                    SRC_ACTION -> current.copy(searchActions = v)
                                    SRC_SETTINGS -> current.copy(searchSettingsPages = v)
                                    SRC_CONTACT -> current.copy(searchContacts = v)
                                    SRC_SMS -> current.copy(searchSms = v)
                                    SRC_CALL -> current.copy(searchCalls = v)
                                    SRC_MUSIC -> current.copy(searchMusic = v)
                                    SRC_FILE -> current.copy(searchFiles = v)
                                    SRC_CLIP -> current.copy(searchClipboard = v)
                                    SRC_CALENDAR -> current.copy(searchCalendar = v)
                                    SRC_TOGGLE -> current.copy(searchToggles = v)
                                    else -> current
                                }
                            }
                        },
                        shape = segmentedShape(index, keys.size),
                    )
                }
            }
        }
    }
}

/** 来源 key → 显示名：统一从来源注册表取，面板提示与设置页共用。 */
@Composable
internal fun sourceTitle(key: String): String =
    stringResource(SearchSourceRegistry.titleRes(key))
