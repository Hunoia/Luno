package hunoia.luno.ui.settings.action

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.BuildConfig
import hunoia.luno.R
import hunoia.luno.core.LogLevel
import hunoia.luno.facade.IncidentStats
import hunoia.luno.facade.LogStats
import hunoia.luno.ui.facade.LocalFacades
import hunoia.luno.ui.navigation.PendingIncidentView
import hunoia.luno.ui.component.row.LunoDropdownRow
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SheetListMaxHeight
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREVIEW_LINES = 200
private const val INCIDENT_PREVIEW_ENTRIES = 50

/** release 下 INFO 及以下已从调用点裁掉，下拉只保留实际可记录的档位。 */
private val supportedLevels: List<LogLevel> =
    if (BuildConfig.DEBUG) LogLevel.entries else listOf(LogLevel.WARN, LogLevel.ERROR)

/**
 * 运行日志页：记录开关、文件大小、最近日志预览、清空与导出。
 *
 * 只调 `facades.logs`；不直接读文件。日志导出走系统分享，可另存到本地。
 */
@Composable
fun LogSettingsScreen(onBack: () -> Unit) {
    val facades = LocalFacades.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var stats by remember { mutableStateOf(LogStats()) }
    var incidentStats by remember { mutableStateOf(IncidentStats()) }
    var showPreview by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showIncidents by remember { mutableStateOf(false) }
    var showClearIncidentsConfirm by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf("") }
    var incidentText by remember { mutableStateOf("") }

    val pendingIncident by PendingIncidentView.requested.collectAsStateWithLifecycle()
    LaunchedEffect(pendingIncident) {
        if (pendingIncident) {
            showIncidents = true
            PendingIncidentView.consume()
        }
    }

    LaunchedEffect(tick) {
        stats = withContext(Dispatchers.IO) { facades.logs.stats() }
        incidentStats = withContext(Dispatchers.IO) { facades.logs.incidentStats() }
    }
    LaunchedEffect(showPreview) {
        if (showPreview) {
            preview = withContext(Dispatchers.IO) { facades.logs.readTail(PREVIEW_LINES) }
        }
    }
    LaunchedEffect(showIncidents) {
        if (showIncidents) {
            incidentText = withContext(Dispatchers.IO) {
                facades.logs.readIncidents(INCIDENT_PREVIEW_ENTRIES)
            }
        }
    }

    LunoScreen(title = stringResource(R.string.log_settings_title), onBack = onBack) {
        LunoGroup(title = stringResource(R.string.log_settings_group)) {
            LunoSwitchRow(
                title = stringResource(R.string.log_settings_recording),
                subtitle = stringResource(R.string.log_settings_recording_hint),
                checked = stats.enabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            facades.logs.setRecordingEnabled(enabled)
                        }
                        tick++
                    }
                },
                shape = segmentedShape(0, 2),
            )
            LunoDropdownRow(
                title = stringResource(R.string.log_settings_level),
                options = supportedLevels.map { stringResource(it.labelRes()) },
                selectedIndex = supportedLevels.indexOf(stats.level),
                subtitle = levelSummary(stats),
                onSelect = { index ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            facades.logs.setLevel(supportedLevels[index])
                        }
                        tick++
                    }
                },
                shape = segmentedShape(1, 2),
            )
        }
        LunoGroup(title = stringResource(R.string.log_settings_files)) {
            if (stats.files.isEmpty()) {
                LunoSettingsRow(
                    title = stringResource(R.string.log_settings_empty_files),
                    shape = segmentedShape(0, 1),
                )
            } else {
                stats.files.forEachIndexed { index, file ->
                    LunoSettingsRow(
                        title = file.name,
                        subtitle = formatBytes(file.sizeBytes),
                        shape = segmentedShape(index, stats.files.size),
                    )
                }
            }
        }
        LunoGroup(title = stringResource(R.string.incident_group)) {
            LunoSwitchRow(
                title = stringResource(R.string.incident_notification),
                subtitle = stringResource(R.string.incident_notification_hint),
                checked = incidentStats.notifyEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            facades.logs.setIncidentNotifyEnabled(enabled)
                        }
                        tick++
                    }
                },
                shape = segmentedShape(0, 3),
            )
            LunoSettingsRow(
                title = stringResource(R.string.incident_recent),
                subtitle = incidentSummary(incidentStats),
                shape = segmentedShape(1, 3),
                onClick = { showIncidents = true },
            )
            LunoSettingsRow(
                title = stringResource(R.string.incident_clear),
                subtitle = formatBytes(incidentStats.sizeBytes),
                shape = segmentedShape(2, 3),
                onClick = { showClearIncidentsConfirm = true },
            )
        }
        LunoGroup(title = stringResource(R.string.log_settings_manage)) {
            LunoSettingsRow(
                title = stringResource(R.string.log_settings_total),
                subtitle = stringResource(
                    R.string.log_settings_total_value,
                    stats.files.size,
                    formatBytes(stats.totalBytes),
                ),
                shape = segmentedShape(0, 3),
            )
            LunoSettingsRow(
                title = stringResource(R.string.log_settings_export),
                subtitle = stringResource(R.string.log_settings_export_hint),
                shape = segmentedShape(1, 3),
                onClick = {
                    scope.launch {
                        val ok = facades.logs.exportAndShare(context)
                        if (!ok) {
                            showToast(R.string.log_settings_export_failed)
                        }
                    }
                },
            )
            LunoSettingsRow(
                title = stringResource(R.string.log_settings_clear),
                shape = segmentedShape(2, 3),
                onClick = { showClearConfirm = true },
            )
        }
        LunoGroup(title = stringResource(R.string.log_settings_preview)) {
            LunoSettingsRow(
                title = stringResource(R.string.log_settings_preview),
                subtitle = stringResource(R.string.log_settings_preview_hint, PREVIEW_LINES),
                shape = segmentedShape(0, 1),
                onClick = { showPreview = true },
            )
        }
    }

    if (showPreview) {
        LunoSheet(onDismissRequest = { showPreview = false }, showDragHandle = true) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = PageGutter),
            ) {
                Text(
                    text = stringResource(R.string.log_settings_preview_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                SelectionContainer {
                    Text(
                        text = preview.ifBlank {
                            stringResource(R.string.log_settings_preview_empty)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = SheetListMaxHeight)
                            .verticalScroll(rememberScrollState())
                            .padding(top = PageGutter),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showIncidents) {
        LunoSheet(onDismissRequest = { showIncidents = false }, showDragHandle = true) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = PageGutter),
            ) {
                Text(
                    text = stringResource(R.string.incident_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                SelectionContainer {
                    Text(
                        text = incidentText.ifBlank {
                            stringResource(R.string.incident_empty)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = SheetListMaxHeight)
                            .verticalScroll(rememberScrollState())
                            .padding(top = PageGutter),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showClearIncidentsConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.incident_clear),
            text = stringResource(R.string.incident_clear_confirm),
            destructive = true,
            onDismissRequest = { showClearIncidentsConfirm = false },
            onConfirm = {
                showClearIncidentsConfirm = false
                scope.launch {
                    withContext(Dispatchers.IO) { facades.logs.clearIncidents() }
                    incidentText = ""
                    tick++
                    showToast(R.string.incident_cleared)
                }
            },
        )
    }

    if (showClearConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.log_settings_clear),
            text = stringResource(R.string.log_settings_clear_confirm),
            destructive = true,
            onDismissRequest = { showClearConfirm = false },
            onConfirm = {
                showClearConfirm = false
                scope.launch {
                    withContext(Dispatchers.IO) { facades.logs.clear() }
                    tick++
                    showToast(R.string.log_settings_cleared)
                }
            },
        )
    }
}

@Composable
private fun levelSummary(stats: LogStats): String {
    val level = stringResource(stats.level.labelRes())
    val hint = stringResource(
        when {
            !stats.enabled -> R.string.log_settings_level_recording_off
            !BuildConfig.DEBUG -> R.string.log_settings_level_release_hint
            else -> R.string.log_settings_level_hint
        },
    )
    return "$level · $hint"
}

@Composable
private fun incidentSummary(stats: IncidentStats): String {
    if (stats.count == 0) return stringResource(R.string.incident_none)
    return stringResource(
        R.string.incident_summary,
        stats.count,
        formatIncidentTime(stats.lastTimeMs),
    )
}

private val incidentTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

private fun formatIncidentTime(timeMs: Long): String =
    runCatching {
        Instant.ofEpochMilli(timeMs).atZone(ZoneId.systemDefault()).format(incidentTimeFormatter)
    }.getOrDefault("")

private fun LogLevel.labelRes(): Int = when (this) {
    LogLevel.VERBOSE -> R.string.log_settings_level_verbose
    LogLevel.DEBUG -> R.string.log_settings_level_debug
    LogLevel.INFO -> R.string.log_settings_level_info
    LogLevel.WARN -> R.string.log_settings_level_warn
    LogLevel.ERROR -> R.string.log_settings_level_error
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(java.util.Locale.US, "%.1f KB", kb)
    return String.format(java.util.Locale.US, "%.1f MB", kb / 1024.0)
}
