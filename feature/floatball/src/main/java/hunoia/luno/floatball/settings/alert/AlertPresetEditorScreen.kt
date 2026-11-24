package hunoia.luno.floatball.settings.alert

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.config.model.AlertPreset
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.PressFilterChip
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.SegmentedRows
import hunoia.luno.ui.component.row.segmentedRows
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.facade.LocalFacades
import hunoia.luno.ui.theme.LargeShape
import kotlinx.coroutines.launch

/**
 * 预设编辑器：名称 + 组合级调色板 + 层列表（拖动顺序 = 绘制顺序）+ 增删层。
 *
 * 改动立即落盘（个人工具，不维护草稿态）；「试播」用当前预设，不依赖是否已选中。
 * 全参数在单层编辑页，这里只做结构。
 */
@Composable
fun AlertPresetEditorScreen(
    presetId: String,
    onBack: () -> Unit,
    onEditLayer: (layerId: String) -> Unit,
    onAddLayer: () -> Unit,
    onGone: () -> Unit,
) {
    val editor = rememberAlertEditor()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val facades = LocalFacades.current
    val current = editor.library.presetById(presetId)
    var deleting by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var nameDraft by remember(presetId) {
        mutableStateOf(editor.library.presetById(presetId)?.name.orEmpty())
    }

    LaunchedEffect(presetId, current) {
        if (current == null && !leaving) onGone()
    }
    if (current == null) return

    fun update(transform: (AlertPreset) -> AlertPreset) {
        scope.launch {
            editor.commitLibrary { lib ->
                lib.copy(
                    presets = lib.presets.map {
                        if (it.id == current.id) transform(it) else it
                    },
                )
            }
        }
    }

    fun commitName() {
        val trimmed = nameDraft.trim()
        if (trimmed.isEmpty() || trimmed == current.name) {
            nameDraft = current.name
            return
        }
        update { it.copy(name = trimmed) }
    }

    fun leave() {
        if (leaving) return
        val trimmed = nameDraft.trim()
        val needsName = trimmed.isNotEmpty() && trimmed != current.name
        leaving = true
        scope.launch {
            if (needsName) {
                editor.commitLibrary { lib ->
                    lib.copy(
                        presets = lib.presets.map {
                            if (it.id == current.id) it.copy(name = trimmed) else it
                        },
                    )
                }
            }
            onBack()
        }
    }

    LunoScreenLazy(
        title = stringResource(R.string.float_ball_preset_editor_title),
        onBack = { leave() },
        dialogs = {
            if (deleting) {
                ConfirmDialog(
                    title = stringResource(R.string.float_ball_preset_delete_confirm_title),
                    text = stringResource(
                        R.string.float_ball_preset_delete_confirm_text,
                        current.displayNameString(context),
                    ),
                    destructive = true,
                    onDismissRequest = { deleting = false },
                    onConfirm = {
                        deleting = false
                        leaving = true
                        scope.launch {
                            editor.commitLibrary { lib ->
                                lib.copy(presets = lib.presets.filterNot { it.id == current.id })
                            }
                            if (editor.alert.presetId == current.id) {
                                editor.commitAlert { it.copy(presetId = null) }
                            }
                            onBack()
                        }
                    },
                )
            }
        },
    ) {
        item(key = "name") {
            OutlinedTextField(
                value = nameDraft,
                onValueChange = { nameDraft = it },
                label = { Text(stringResource(R.string.float_ball_preset_name)) },
                singleLine = true,
                shape = LargeShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .onFocusChanged { if (!it.isFocused) commitName() },
            )
        }
        item(key = "preview") {
            LunoGroup {
                SegmentedRows(
                    segmentedRows {
                        item { shape ->
                            LunoSettingsRow(
                                title = stringResource(R.string.float_ball_preset_preview),
                                subtitle = stringResource(R.string.float_ball_preset_preview_hint),
                                icon = Icons.Default.PlayArrow,
                                shape = shape,
                                onClick = {
                                    commitName()
                                    facades.system.previewFloatBallAlertPreset(context, current)
                                },
                            )
                        }
                    },
                )
            }
        }
        item(key = "palette") {
            LunoGroup(
                title = stringResource(R.string.float_ball_preset_palette),
                subtitle = stringResource(R.string.float_ball_preset_palette_hint),
            ) {
                PaletteChips(
                    library = editor.library,
                    selected = current.paletteId,
                    onSelect = { id -> update { it.copy(paletteId = id) } },
                )
            }
        }
        item(key = "layers") {
            LunoGroup(
                title = stringResource(R.string.float_ball_preset_layers_title),
                subtitle = stringResource(
                    R.string.float_ball_preset_layers_hint,
                    current.enabledLayerCount,
                    MaxEnabledLayers,
                ),
            ) {
                val rows = segmentedRows {
                    if (current.layers.isEmpty()) {
                        item { shape ->
                            LunoSettingsRow(
                                title = stringResource(R.string.float_ball_preset_no_layer),
                                subtitle = stringResource(R.string.float_ball_preset_no_layer_hint),
                                shape = shape,
                            )
                        }
                    }
                    current.layers.forEach { layer ->
                        item { shape ->
                            LunoSwitchRow(
                                title = layer.displayName(),
                                subtitle = layer.patternLabel().orEmpty(),
                                checked = layer.enabled,
                                shape = shape,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        val blocked = current.addBlockedBy(layer.kind, addingStored = false)
                                        if (blocked != null) {
                                            // 超上限时保持关闭，由上方「还能加几层」的提示解释原因。
                                            return@LunoSwitchRow
                                        }
                                    }
                                    update { p ->
                                        p.withReplacedLayer(layer.copy(enabled = checked))
                                    }
                                },
                                onClick = { onEditLayer(layer.id) },
                            )
                        }
                    }
                    item { shape ->
                        LunoSettingsRow(
                            title = stringResource(R.string.float_ball_preset_add_layer),
                            subtitle = stringResource(R.string.float_ball_preset_add_layer_hint),
                            icon = Icons.Default.Add,
                            shape = shape,
                            onClick = onAddLayer,
                        )
                    }
                }
                SegmentedRows(rows)
            }
        }
        item(key = "delete") {
            LunoGroup {
                SegmentedRows(
                    segmentedRows {
                        item { shape ->
                            LunoSettingsRow(
                                title = stringResource(R.string.float_ball_preset_delete),
                                subtitle = stringResource(R.string.float_ball_preset_delete_subtitle),
                                icon = Icons.Default.Delete,
                                shape = shape,
                                onClick = { deleting = true },
                            )
                        }
                    },
                )
            }
        }
        item(key = "save") {
            LunoGroup(subtitle = stringResource(R.string.float_ball_preset_autosave_hint)) {
                SegmentedRows(
                    segmentedRows {
                        item { shape ->
                            LunoSettingsRow(
                                title = stringResource(R.string.float_ball_preset_done),
                                shape = shape,
                                onClick = { leave() },
                            )
                        }
                    },
                )
            }
        }
    }
}

/** 组合级调色板选择：跟随各层 / 素材库里的每一套。 */
@Composable
internal fun PaletteChips(
    library: hunoia.luno.config.model.AlertLibrary,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PressFilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.float_ball_preset_palette_follow)) },
        )
        library.paletteChoices().forEach { palette ->
            PressFilterChip(
                selected = selected == palette.id,
                onClick = { onSelect(palette.id) },
                label = { Text(palette.name) },
            )
        }
    }
}
