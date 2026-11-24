package hunoia.luno.floatball.settings.alert

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import hunoia.luno.action.definitions.ActionCategory
import hunoia.luno.action.definitions.ActionDefinition
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.actions.library.LibraryCategory
import hunoia.luno.actions.library.displayNameRes
import hunoia.luno.actions.library.libraryCategoryOf
import hunoia.luno.config.model.ActionAlertSettings
import hunoia.luno.core.domain.ActionLibraryEntry
import hunoia.luno.config.model.AlertLimits
import hunoia.luno.config.model.AlertPreset
import hunoia.luno.domain.floatball.FloatBallSettingsCenter
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.LunoFab
import hunoia.luno.ui.component.LunoSearchField
import hunoia.luno.ui.component.PressFilterChip
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.SegmentedRows
import hunoia.luno.ui.component.row.chunkedGroup
import hunoia.luno.ui.component.row.segmentedRows
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.facade.LocalFacades
import hunoia.luno.ui.theme.CapsuleShape
import hunoia.luno.ui.theme.FloatingContentBottom
import kotlinx.coroutines.launch

/** 绑定的目标：动作选择页里的类型（typeId）或动作库条目（entryId），二者互斥。 */
private data class BindingTarget(
    val typeId: String?,
    val entryId: String?,
)

/** 动效页的两个列表：选择页动作 / 动作库条目（两集合天然不重叠）。 */
private enum class ActionAlertTab { Picker, Library }

/**
 * 动作动效：动作执行成功后按绑定播一份预设。
 *
 * - 与到达提示共用预设与素材库，但强度 / 速度是独立的一套；
 * - 两个 tab 分别覆盖「动作选择页的动作（按类型）」与「动作库条目（按条目）」；
 * - 行尾显示当前绑定的预设名，点行开选择 sheet；右下 FAB 试播最近选中的预设；
 * - 绑定即落盘，动作落定时由服务读取（见 `ActionAlertSettings`）。
 */
@Composable
fun ActionAlertSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val facades = LocalFacades.current
    var actionAlert by remember { mutableStateOf(TuneConfig.latestAction().floatBall.actionAlert) }
    var library by remember { mutableStateOf(TuneConfig.latestAction().floatBall.notificationCapsule.alertLibrary) }
    var entries by remember {
        mutableStateOf<List<ActionLibraryEntry>>(
            FloatBallSettingsCenter.latestActionLibrarySettings().entries,
        )
    }
    var tab by rememberSaveable { mutableStateOf(ActionAlertTab.Picker) }
    var query by rememberSaveable { mutableStateOf("") }
    var collapsedGroups by remember { mutableStateOf(emptySet<String>()) }
    var previewPresetId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickTarget by remember { mutableStateOf<BindingTarget?>(null) }

    LifecycleResumeEffect(Unit) {
        val action = TuneConfig.latestAction()
        actionAlert = action.floatBall.actionAlert
        library = action.floatBall.notificationCapsule.alertLibrary
        entries = FloatBallSettingsCenter.latestActionLibrarySettings().entries
        onPauseOrDispose { }
    }

    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    fun update(transform: (ActionAlertSettings) -> ActionAlertSettings) {
        actionAlert = transform(actionAlert)
        scope.launch {
            TuneConfig.updateAction { current ->
                current.copy(
                    floatBall = current.floatBall.copy(
                        actionAlert = transform(current.floatBall.actionAlert),
                    ),
                )
            }
        }
    }

    val pickerDefinitions = remember {
        ActionDefinitions.pickerDefinitions()
            .filter { it.category != ActionCategory.NONE }
            .sortedWith(
                compareBy(
                    { ActionDefinitions.categoryOrder(it.category) },
                    { it.name },
                ),
            )
    }
    val normalizedQuery = query.trim()

    fun matches(name: String, typeId: String): Boolean =
        normalizedQuery.isEmpty() ||
            name.contains(normalizedQuery, ignoreCase = true) ||
            typeId.contains(normalizedQuery, ignoreCase = true)

    fun boundPresetLabel(presetId: String?): String {
        if (presetId == null) return context.getString(R.string.action_alert_unbound)
        val preset: AlertPreset? = library.presetById(presetId)
        return preset?.displayNameString(context)
            ?: context.getString(R.string.float_ball_alert_preset_missing)
    }

    val previewPreset: AlertPreset? = previewPresetId?.let { library.presetById(it) }

    LunoScreenLazy(
        title = stringResource(R.string.action_alert_title),
        onBack = onBack,
        bottomContentPadding = FloatingContentBottom,
        floatingContent = {
            LunoFab(
                onClick = {
                    when {
                        !actionAlert.enabled -> toast(
                            context.getString(R.string.action_alert_preview_disabled),
                        )
                        previewPreset == null -> toast(
                            context.getString(R.string.action_alert_preview_none),
                        )
                        else -> facades.system.previewFloatBallAlertPreset(
                            context,
                            previewPreset,
                            intensityPercent = actionAlert.intensityPercent,
                            speedPercent = actionAlert.speedPercent,
                        )
                    }
                },
                icon = Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.action_alert_preview),
            )
        },
        sheets = {
            pickTarget?.let { target ->
                AlertPresetSelectSheet(
                    library = library,
                    currentId = actionAlert.presetFor(target.typeId, target.entryId),
                    onDismiss = { pickTarget = null },
                    onSelect = { presetId ->
                        pickTarget = null
                        previewPresetId = presetId
                        update { it.withBinding(target.typeId, target.entryId, presetId) }
                    },
                    noneSubtitle = stringResource(R.string.action_alert_unbound_hint),
                )
            }
        },
    ) {
        item(key = "basic") {
            val rows = segmentedRows {
                item { shape ->
                    LunoSwitchRow(
                        title = stringResource(R.string.action_alert_enabled),
                        subtitle = stringResource(R.string.action_alert_enabled_subtitle),
                        checked = actionAlert.enabled,
                        shape = shape,
                        onCheckedChange = { checked ->
                            update { it.copy(enabled = checked) }
                        },
                    )
                }
                item { shape ->
                    AlertKnobSlider(
                        label = stringResource(R.string.action_alert_intensity),
                        value = actionAlert.intensityPercent,
                        range = ActionIntensityRange,
                        display = { "${it.toInt()}%" },
                        shape = shape,
                        onCommit = { value -> update { it.copy(intensityPercent = value) } },
                    )
                }
                item { shape ->
                    AlertKnobSlider(
                        label = stringResource(R.string.action_alert_speed),
                        value = actionAlert.speedPercent,
                        range = ActionSpeedRange,
                        display = { "${it.toInt()}%" },
                        shape = shape,
                        onCommit = { value -> update { it.copy(speedPercent = value) } },
                    )
                }
            }
            LunoGroup(
                title = stringResource(R.string.action_alert_basic_group),
                subtitle = stringResource(R.string.action_alert_basic_hint),
            ) {
                SegmentedRows(rows)
            }
        }
        item(key = "tabs") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PressFilterChip(
                    selected = tab == ActionAlertTab.Picker,
                    onClick = { tab = ActionAlertTab.Picker },
                    label = { Text(stringResource(R.string.action_alert_tab_picker)) },
                )
                PressFilterChip(
                    selected = tab == ActionAlertTab.Library,
                    onClick = { tab = ActionAlertTab.Library },
                    label = { Text(stringResource(R.string.action_alert_tab_library)) },
                )
            }
        }
        item(key = "search") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = CapsuleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LunoSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = stringResource(R.string.action_alert_search),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        when (tab) {
            ActionAlertTab.Picker -> {
                val grouped = pickerDefinitions
                    .filter { matches(it.name, it.typeId) }
                    .groupBy { it.category }
                if (grouped.isEmpty()) {
                    item(key = "picker_empty") {
                        EmptyHint(stringResource(R.string.action_alert_search_empty))
                    }
                }
                grouped.forEach { (category, actions) ->
                    item(key = "category_${category.name}") {
                        val groupKey = "picker_${category.name}"
                        val expanded = normalizedQuery.isNotEmpty() || groupKey !in collapsedGroups
                        LunoGroup(
                            title = stringResource(category.displayNameRes),
                            badgeText = "${actions.size}",
                            collapsible = true,
                            expanded = expanded,
                            onToggle = {
                                collapsedGroups = if (groupKey in collapsedGroups) {
                                    collapsedGroups - groupKey
                                } else {
                                    collapsedGroups + groupKey
                                }
                            },
                        ) {
                            val rows = segmentedRows {
                                actions.forEach { definition ->
                                    item { shape ->
                                        ActionBindingRow(
                                            title = definition.name,
                                            icon = definition.icon,
                                            boundLabel = boundPresetLabel(
                                                actionAlert.presetFor(definition.typeId, null),
                                            ),
                                            shape = shape,
                                            onClick = {
                                                pickTarget = BindingTarget(
                                                    typeId = definition.typeId,
                                                    entryId = null,
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                            SegmentedRows(rows)
                        }
                    }
                }
            }

            ActionAlertTab.Library -> {
                val filtered = entries.filter { entry ->
                    val def = ActionDefinitions.byTypeId(entry.typeId)
                    val name = entry.displayTitle(def)
                    matches(name, entry.typeId)
                }
                if (filtered.isEmpty()) {
                    item(key = "library_empty") {
                        LunoGroup(
                            title = stringResource(R.string.action_alert_tab_library),
                            subtitle = stringResource(R.string.action_alert_library_empty_hint),
                        ) {
                            SegmentedRows(
                                segmentedRows {
                                    item { shape ->
                                        LunoSettingsRow(
                                            title = stringResource(
                                                R.string.action_alert_library_empty,
                                            ),
                                            icon = Icons.Default.Search,
                                            shape = shape,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
                LibraryCategory.entries.forEach { category ->
                    val categoryEntries = filtered.filter {
                        runCatching { libraryCategoryOf(it.typeId) }.getOrNull() == category
                    }
                    if (categoryEntries.isEmpty()) return@forEach
                    chunkedGroup(
                        items = categoryEntries,
                        keyPrefix = "lib_${category.name}",
                        title = { stringResource(category.displayNameRes) },
                        subtitle = { null },
                    ) { shape, _, entry ->
                        val def = ActionDefinitions.byTypeId(entry.typeId)
                        ActionBindingRow(
                            title = entry.displayTitle(def),
                            icon = def?.icon,
                            boundLabel = boundPresetLabel(
                                actionAlert.presetFor(null, entry.id),
                            ),
                            shape = shape,
                            onClick = {
                                pickTarget = BindingTarget(
                                    typeId = null,
                                    entryId = entry.id,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionBindingRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    boundLabel: String,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
) {
    LunoSettingsRow(
        title = title,
        subtitle = boundLabel,
        icon = icon,
        shape = shape,
        onClick = onClick,
    )
}

@Composable
private fun EmptyHint(text: String) {
    LunoGroup {
        SegmentedRows(
            segmentedRows {
                item { shape ->
                    LunoSettingsRow(
                        title = text,
                        shape = shape,
                    )
                }
            },
        )
    }
}

/** 动作库条目的展示名：自定义名为空时回退动作类型名。 */
private fun ActionLibraryEntry.displayTitle(definition: ActionDefinition?): String =
    name.ifBlank { definition?.name ?: typeId }

private val ActionIntensityRange =
    AlertLimits.IntensityMin..AlertLimits.IntensityMax
private val ActionSpeedRange =
    AlertLimits.SpeedMin..AlertLimits.SpeedMax
