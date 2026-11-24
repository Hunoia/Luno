package hunoia.luno.floatball.settings.alert

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import hunoia.luno.config.model.AlertLibraryExport
import hunoia.luno.config.model.AlertPreset
import hunoia.luno.core.domain.JsonSerializer
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.feedback.LunoIconButton
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.SegmentedRows
import hunoia.luno.ui.component.row.segmentedRows
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.facade.LocalFacades
import hunoia.luno.ui.theme.MarkColorSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 到达提示·预设库（设置页根目录独立入口）：内置单层素材 + 我的组合。
 *
 * - 内置只读，点一下复制成用户预设再编辑（快照口径）；
 * - 我的预设可编辑 / 删除；
 * - 导出 / 导入只作用于「预设 + 调色板」，合并导入，不冲掉现有素材。
 */
@Composable
fun AlertPresetLibraryScreen(
    onBack: () -> Unit,
    onEditPreset: (String) -> Unit,
) {
    val editor = rememberAlertEditor()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val facades = LocalFacades.current
    var deleting by remember { mutableStateOf<AlertPreset?>(null) }
    var busy by remember { mutableStateOf(false) }
    val library = editor.library
    val mine = library.userPresets()
    val builtin = library.pickerPresets().filter { it.builtin }

    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val payload = JsonSerializer.globalJson.encodeToString(
                library.toExport(System.currentTimeMillis()),
            )
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(payload.toByteArray(Charsets.UTF_8))
                    } ?: error("no stream")
                }.isSuccess
            }
            busy = false
            toast(
                context.getString(
                    if (ok) R.string.float_ball_preset_export_done
                    else R.string.float_ball_preset_export_failed,
                ),
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val decoded = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bytes = stream.readBytes()
                        if (bytes.size > AlertLibraryImportMaxBytes) error("file too large")
                        JsonSerializer.globalJson.decodeFromString<AlertLibraryExport>(
                            bytes.toString(Charsets.UTF_8),
                        )
                    }
                }.getOrNull()
            }
            val merged = decoded?.let { library.mergeImport(it) }
            if (decoded == null || merged == null) {
                busy = false
                toast(context.getString(R.string.float_ball_preset_import_failed))
                return@launch
            }
            val added = merged.presets.size - library.presets.size
            editor.commitLibrary { merged }
            busy = false
            toast(context.getString(R.string.float_ball_preset_import_done, added))
        }
    }

    LunoScreenLazy(
        title = stringResource(R.string.float_ball_preset_library_title),
        onBack = onBack,
        dialogs = {
            deleting?.let { preset ->
                ConfirmDialog(
                    title = stringResource(R.string.float_ball_preset_delete_confirm_title),
                    text = stringResource(R.string.float_ball_preset_delete_confirm_text, preset.displayNameString(context)),
                    destructive = true,
                    onDismissRequest = { deleting = null },
                    onConfirm = {
                        deleting = null
                        scope.launch {
                            editor.commitLibrary { lib ->
                                lib.copy(presets = lib.presets.filterNot { it.id == preset.id })
                            }
                            // 正在使用的预设被删：把选择清掉，避免设置页显示「已失效」。
                            if (editor.alert.presetId == preset.id) {
                                editor.commitAlert { it.copy(presetId = null) }
                            }
                        }
                    },
                )
            }
        },
    ) {
        item(key = "actions") {
            LunoGroup(subtitle = stringResource(R.string.float_ball_preset_library_hint)) {
                val rows = segmentedRows {
                    item { shape ->
                        LunoSettingsRow(
                            title = stringResource(R.string.float_ball_preset_new),
                            subtitle = stringResource(R.string.float_ball_preset_new_subtitle),
                            icon = Icons.Default.Add,
                            shape = shape,
                            enabled = !busy,
                            onClick = {
                                if (busy) return@LunoSettingsRow
                                busy = true
                                val name = uniquePresetName(
                                    context.getString(R.string.float_ball_preset_default_name),
                                    library.presets.map { it.name },
                                )
                                val preset = newUserPreset(name)
                                scope.launch {
                                    editor.commitLibrary { it.copy(presets = it.presets + preset) }
                                    busy = false
                                    onEditPreset(preset.id)
                                }
                            },
                        )
                    }
                    item { shape ->
                        LunoSettingsRow(
                            title = stringResource(R.string.float_ball_preset_export),
                            subtitle = stringResource(R.string.float_ball_preset_export_subtitle),
                            shape = shape,
                            enabled = !busy,
                            onClick = {
                                exportLauncher.launch(
                                    "luno-alert-effects-${System.currentTimeMillis()}.json",
                                )
                            },
                        )
                    }
                    item { shape ->
                        LunoSettingsRow(
                            title = stringResource(R.string.float_ball_preset_import),
                            subtitle = stringResource(R.string.float_ball_preset_import_subtitle),
                            shape = shape,
                            enabled = !busy,
                            onClick = { importLauncher.launch(arrayOf("application/json")) },
                        )
                    }
                }
                SegmentedRows(rows)
            }
        }
        item(key = "mine") {
            LunoGroup(
                title = stringResource(R.string.float_ball_preset_group_mine),
                subtitle = stringResource(R.string.float_ball_preset_group_mine_hint),
            ) {
                val rows = segmentedRows {
                    if (mine.isEmpty()) {
                        item { shape ->
                            LunoSettingsRow(
                                title = stringResource(R.string.float_ball_preset_empty),
                                subtitle = stringResource(R.string.float_ball_preset_empty_hint),
                                shape = shape,
                            )
                        }
                    }
                    mine.forEach { preset ->
                        item { shape ->
                            LunoSettingsRow(
                                title = preset.displayName(),
                                subtitle = "${preset.layerSummary()}  ${preset.categorySummary()}",
                                shape = shape,
                                leading = { PresetSwatch(library.swatchFor(preset)) },
                                trailing = {
                                    LunoIconButton(
                                        icon = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.float_ball_preset_delete),
                                        onClick = { deleting = preset },
                                    )
                                },
                                onClick = { onEditPreset(preset.id) },
                            )
                        }
                    }
                }
                SegmentedRows(rows)
            }
        }
        item(key = "builtin") {
            LunoGroup(
                title = stringResource(R.string.float_ball_preset_group_builtin),
                subtitle = stringResource(R.string.float_ball_preset_group_builtin_edit_hint),
            ) {
                val rows = segmentedRows {
                    builtin.forEach { preset ->
                        item { shape ->
                            LunoSettingsRow(
                                title = preset.displayName(),
                                subtitle = "${preset.layerSummary()}  ${preset.categorySummary()}",
                                shape = shape,
                                leading = { PresetSwatch(library.swatchFor(preset)) },
                                trailing = {
                                    LunoIconButton(
                                        icon = Icons.Default.PlayArrow,
                                        contentDescription = stringResource(R.string.float_ball_preset_preview),
                                        onClick = { facades.system.previewFloatBallAlertPreset(context, preset) },
                                    )
                                },
                                onClick = {
                                    if (busy) return@LunoSettingsRow
                                    busy = true
                                    val copy = preset.asUserCopy().let { copied ->
                                        val name = uniquePresetName(
                                            context.getString(
                                                R.string.float_ball_preset_copy_name,
                                                preset.displayNameString(context),
                                            ),
                                            library.presets.map { it.name },
                                        )
                                        copied.copy(name = name)
                                    }
                                    scope.launch {
                                        editor.commitLibrary { it.copy(presets = it.presets + copy) }
                                        busy = false
                                        onEditPreset(copy.id)
                                    }
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

/** 预设卡片左侧的小色块（调色板首色）；没有颜色时用主题主色。 */
@Composable
private fun PresetSwatch(colors: List<androidx.compose.ui.graphics.Color>) {
    Box(
        modifier = Modifier
            .size(MarkColorSize)
            .background(
                color = colors.firstOrNull() ?: MaterialTheme.colorScheme.primary,
                shape = CircleShape,
            ),
    )
}

/** 预设卡片色点：预设级调色板 → 层颜色 → 主题主色。 */
@Composable
private fun hunoia.luno.config.model.AlertLibrary.swatchFor(
    preset: AlertPreset,
): List<androidx.compose.ui.graphics.Color> =
    preset.swatchColors(this, MaterialTheme.colorScheme.primary)

/** 卡片副标题的类别串（不依赖 Composable 上下文时用）。 */
@Composable
internal fun AlertPreset.categorySummary(): String {
    val parts = categories().map { stringResource(it.titleRes()) }
    return parts.joinToString(" · ")
}

/** 非 Composable 场景（Toast / 对话框）里的展示名：内置走系统语言资源，用户预设用自身名称。 */
internal fun AlertPreset.displayNameString(context: android.content.Context): String {
    val res = builtinPresetNameRes(id)
    return if (res != null) context.getString(res) else name.ifBlank { id }
}