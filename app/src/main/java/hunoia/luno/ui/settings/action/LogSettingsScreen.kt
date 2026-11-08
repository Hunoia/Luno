package hunoia.luno.ui.settings.action

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.R
import hunoia.luno.core.LogLevel
import hunoia.luno.facade.LogStats
import hunoia.luno.ui.facade.LocalFacades
import hunoia.luno.ui.component.CheckDropdownMenuItem
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.theme.ElevationSmall
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SheetListMaxHeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREVIEW_LINES = 200

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
    var showPreview by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var levelMenuOpen by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf("") }

    LaunchedEffect(tick) {
        stats = withContext(Dispatchers.IO) { facades.logs.stats() }
    }
    LaunchedEffect(showPreview) {
        if (showPreview) {
            preview = withContext(Dispatchers.IO) { facades.logs.readTail(PREVIEW_LINES) }
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
            Box(modifier = Modifier.fillMaxWidth()) {
                LunoSettingsRow(
                    title = stringResource(R.string.log_settings_level),
                    subtitle = levelSummary(stats),
                    shape = segmentedShape(1, 2),
                    onClick = { levelMenuOpen = true },
                )
                Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                    DropdownMenu(
                        expanded = levelMenuOpen,
                        onDismissRequest = { levelMenuOpen = false },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = LargeShape,
                        tonalElevation = ElevationSmall,
                        shadowElevation = 0.dp,
                    ) {
                        LogLevel.entries.forEach { level ->
                            CheckDropdownMenuItem(
                                label = stringResource(level.labelRes()),
                                selected = stats.level == level,
                                onClick = {
                                    levelMenuOpen = false
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            facades.logs.setLevel(level)
                                        }
                                        tick++
                                    }
                                },
                            )
                        }
                    }
                }
            }
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
                    .padding(horizontal = PageGutter)
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
        if (stats.enabled) R.string.log_settings_level_hint
        else R.string.log_settings_level_recording_off,
    )
    return "$level · $hint"
}

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
