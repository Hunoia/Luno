package hunoia.luno.ui.settings.search

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.config.model.QuickSearchSettings
import hunoia.luno.domain.search.SearchCenter
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 隐藏结果的展示时间：本地时区「月-日 时:分」，够认出什么时候藏的即可。 */
private val HiddenTimeFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MM-dd HH:mm")

/** 结果治理与体验设置：从引擎设置页拆出的子页。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchGovernanceSettingsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val settings by SearchCenter.search.collectAsStateWithLifecycle(initialValue = QuickSearchSettings())
    fun update(transform: (QuickSearchSettings) -> QuickSearchSettings) {
        scope.launch { SearchCenter.updateSearch(transform) }
    }
    LunoScreen(
        title = stringResource(R.string.search_governance_settings),
        onBack = onBack,
    ) {
        LunoGroup(title = stringResource(R.string.search_experience_group)) {
            LunoSwitchRow(
                title = stringResource(R.string.search_restore_last_query),
                checked = settings.restoreLastQuery,
                onCheckedChange = { v ->
                    update { it.copy(restoreLastQuery = v) }
                },
            )
            LunoSwitchRow(
                title = stringResource(R.string.search_ascii_enter),
                checked = settings.asciiEnterSearch,
                onCheckedChange = { v ->
                    update { it.copy(asciiEnterSearch = v) }
                },
            )
        }
        // 有时间戳的按时间倒序（新的在前）；旧数据没时间的垫底、保持原相对顺序。
        val orderedKeys = remember(settings.hiddenResultKeys, settings.hiddenResultTimes) {
            val times = settings.hiddenResultTimes
            settings.hiddenResultKeys.sortedByDescending { times[it] ?: 0L }
        }
        LunoGroup(title = stringResource(R.string.search_hidden_results)) {
            if (orderedKeys.isEmpty()) {
                LunoSettingsRow(
                    title = stringResource(R.string.search_hidden_results_empty),
                    enabled = false,
                )
            } else {
                if (orderedKeys.size > 1) {
                    LunoSettingsRow(
                        title = stringResource(R.string.search_restore_all),
                        onClick = {
                            update {
                                it.copy(
                                    hiddenResultKeys = emptyList(),
                                    hiddenResultLabels = emptyMap(),
                                    hiddenResultTimes = emptyMap(),
                                )
                            }
                        },
                    )
                }
                orderedKeys.forEachIndexed { index, key ->
                    val label = settings.hiddenResultLabels[key]
                    val timeText = settings.hiddenResultTimes[key]?.let { millis ->
                        HiddenTimeFormat.format(
                            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()),
                        )
                    }
                    LunoSettingsRow(
                        // 标题优先用隐藏时记下的行标题；没有就回落 key（只有 key 时认不出是哪条）。
                        title = label ?: key,
                        subtitle = timeText ?: key,
                        shape = segmentedShape(index + if (orderedKeys.size > 1) 1 else 0, orderedKeys.size + if (orderedKeys.size > 1) 1 else 0),
                        trailing = {
                            TextButton(
                                onClick = {
                                    update {
                                        it.copy(
                                            hiddenResultKeys = it.hiddenResultKeys.filterNot { k -> k == key },
                                            hiddenResultLabels = it.hiddenResultLabels - key,
                                            hiddenResultTimes = it.hiddenResultTimes - key,
                                        )
                                    }
                                },
                            ) {
                                Text(stringResource(R.string.search_restore))
                            }
                        },
                    )
                }
            }
        }
    }
}
