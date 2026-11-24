package hunoia.luno.floatball.settings.alert

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import hunoia.luno.config.model.AlertLayer
import hunoia.luno.config.model.AlertPlaybackPolicy
import hunoia.luno.config.model.AlertLimits
import hunoia.luno.config.model.AlertPreset
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.LunoFab
import hunoia.luno.ui.component.PressIconButton
import hunoia.luno.ui.component.menu.LunoDropdownMenu
import hunoia.luno.ui.component.menu.LunoDropdownMenuItem
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoInputRow
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.SegmentedRows
import hunoia.luno.ui.component.row.segmentedRows
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.facade.LocalFacades
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.MotionSubtle
import hunoia.luno.ui.theme.SegmentedGap
import kotlinx.coroutines.launch

/**
 * 预设编辑器：名称 + 组合调色板 + 层（可展开行）。
 *
 * 展开层只做结构（启用 / 编辑参数）；上移下移、复制、删除这一层在层编辑页顶栏，
 * 删除预设在本页顶栏。改动即时落盘；试播是右下 FAB（整份）。
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
    var leaving by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var expandedLayerId by remember { mutableStateOf<String?>(null) }
    var nameDraft by remember(presetId) {
        mutableStateOf(editor.library.presetById(presetId)?.name.orEmpty())
    }

    val current = editor.library.presetById(presetId)
    LaunchedEffect(presetId, current, leaving) {
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

    fun commitNameThen(next: () -> Unit) {
        val trimmed = nameDraft.trim()
        if (trimmed.isEmpty() || trimmed == current.name) {
            next()
            return
        }
        scope.launch {
            editor.commitLibrary { lib ->
                lib.copy(
                    presets = lib.presets.map {
                        if (it.id == current.id) it.copy(name = trimmed) else it
                    },
                )
            }
            next()
        }
    }

    fun leave() {
        if (leaving) return
        val trimmed = nameDraft.trim()
        leaving = true
        if (trimmed.isEmpty() || trimmed == current.name) {
            onBack()
            return
        }
        scope.launch {
            editor.commitLibrary { lib ->
                lib.copy(
                    presets = lib.presets.map {
                        if (it.id == current.id) it.copy(name = trimmed) else it
                    },
                )
            }
            onBack()
        }
    }

    fun toggleLayer(layer: AlertLayer, enabled: Boolean) {
        if (enabled) {
            val blocked = current.addBlockedBy(layer.kind, addingStored = false)
            if (blocked != null) {
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.float_ball_preset_toggle_blocked,
                        context.getString(blocked.reasonRes()),
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
                return
            }
        }
        update { it.withReplacedLayer(layer.copy(enabled = enabled)) }
    }

    LunoScreenLazy(
        title = stringResource(R.string.float_ball_preset_editor_title),
        onBack = { leave() },
        bottomContentPadding = FloatingContentBottom,
        floatingContent = {
            LunoFab(
                onClick = {
                    if (current.isPlayable()) {
                        facades.system.previewFloatBallAlertPreset(context, current)
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.float_ball_preset_empty_preview),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                icon = Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.float_ball_preset_preview),
            )
        },
        actions = {
            PressIconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.float_ball_alert_more),
                )
            }
            LunoDropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                LunoDropdownMenuItem(
                    text = stringResource(R.string.float_ball_preset_delete),
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    isError = true,
                    onClick = {
                        menuOpen = false
                        confirmDelete = true
                    },
                )
            }
        },
        dialogs = {
            if (confirmDelete) {
                ConfirmDialog(
                    title = stringResource(R.string.float_ball_preset_delete_confirm_title),
                    text = stringResource(
                        R.string.float_ball_preset_delete_confirm_text,
                        current.displayNameString(context),
                    ),
                    destructive = true,
                    onDismissRequest = { confirmDelete = false },
                    onConfirm = {
                        confirmDelete = false
                        leaving = true
                        scope.launch {
                            editor.commitLibrary { lib ->
                                lib.copy(presets = lib.presets.filterNot { it.id == current.id })
                            }
                            if (editor.alert.presetId == current.id) {
                                editor.commitAlert { it.copy(presetId = null) }
                            }
                            // 所有指向它的时刻绑定一并清掉：删了预设还留悬空引用只会静默不播。
                            val ball = TuneConfig.latestAction().floatBall
                            val referenced = current.id in ball.actionAlert.bindings.values ||
                                ball.lockAlert.presetId == current.id ||
                                current.id in ball.buttonAlert.bindings.values
                            if (referenced) {
                                TuneConfig.updateAction { config ->
                                    val floatBall = config.floatBall
                                    config.copy(
                                        floatBall = floatBall.copy(
                                            actionAlert = floatBall.actionAlert
                                                .withoutPreset(current.id),
                                            lockAlert = floatBall.lockAlert
                                                .withoutPreset(current.id),
                                            buttonAlert = floatBall.buttonAlert
                                                .withoutPreset(current.id),
                                        ),
                                    )
                                }
                                TuneConfig.syncActionSnapshot()
                            }
                            onBack()
                        }
                    },
                )
            }
        },
    ) {
        item(key = "name") {
            LunoGroup {
                SegmentedRows(
                    segmentedRows {
                        item { shape ->
                            LunoInputRow(
                                label = stringResource(R.string.float_ball_preset_name),
                                value = nameDraft,
                                onValueChange = { nameDraft = it.take(24) },
                                placeholder = stringResource(
                                    R.string.float_ball_preset_default_name,
                                ),
                                shape = shape,
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
                SegmentedRows(
                    segmentedRows {
                        item { shape ->
                            AlertPaletteRow(
                                title = stringResource(R.string.float_ball_preset_palette),
                                library = editor.library,
                                selectedId = current.paletteId,
                                followLabel = stringResource(
                                    R.string.float_ball_preset_palette_follow,
                                ),
                                shape = shape,
                                onSelect = { id -> update { it.copy(paletteId = id) } },
                            )
                        }
                    },
                )
            }
        }
        item(key = "policy") {
            LunoGroup(
                title = stringResource(R.string.float_ball_preset_policy),
                subtitle = stringResource(R.string.float_ball_preset_policy_hint),
            ) {
                SegmentedRows(
                    segmentedRows {
                        item { shape ->
                            LayerDropdownRow(
                                title = stringResource(R.string.float_ball_preset_policy),
                                options = AlertPlaybackPolicy.entries,
                                selected = current.playbackPolicy,
                                shape = shape,
                                optionLabel = { stringResource(it.labelRes()) },
                                onSelect = { policy ->
                                    update { it.copy(playbackPolicy = policy) }
                                },
                            )
                        }
                    },
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
                badgeText = "${current.enabledLayerCount} / $MaxEnabledLayers",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(SegmentedGap)) {
                    if (current.layers.isEmpty()) {
                        LunoSettingsRow(
                            title = stringResource(R.string.float_ball_preset_no_layer),
                            subtitle = stringResource(R.string.float_ball_preset_no_layer_hint),
                            shape = LargeShape,
                        )
                    }
                    current.layers.forEachIndexed { index, layer ->
                        val expanded = expandedLayerId == layer.id
                        LunoSettingsRow(
                            title = "#${index + 1}  ${layer.displayName()}",
                            subtitle = buildString {
                                append(layer.patternLabel().orEmpty())
                                if (!layer.enabled) {
                                    append("  ")
                                    append(context.getString(R.string.float_ball_preset_layer_off))
                                }
                            },
                            shape = LargeShape,
                            trailing = {
                                Icon(
                                    imageVector = Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.rotate(
                                        animateFloatAsState(
                                            targetValue = if (expanded) 180f else 0f,
                                            animationSpec = tween(MotionSubtle),
                                            label = "layerChevron",
                                        ).value,
                                    ),
                                )
                            },
                            onClick = {
                                expandedLayerId = if (expanded) null else layer.id
                            },
                        )
                        AnimatedVisibility(visible = expanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(SegmentedGap)) {
                                LunoSwitchRow(
                                    title = stringResource(R.string.float_ball_layer_enabled),
                                    subtitle = stringResource(
                                        R.string.float_ball_layer_enabled_subtitle,
                                    ),
                                    checked = layer.enabled,
                                    shape = LargeShape,
                                    onCheckedChange = { checked ->
                                        toggleLayer(layer, checked)
                                    },
                                )
                                LunoSettingsRow(
                                    title = stringResource(R.string.float_ball_layer_edit_params),
                                    subtitle = stringResource(
                                        R.string.float_ball_layer_edit_params_hint,
                                    ),
                                    shape = LargeShape,
                                    onClick = {
                                        commitNameThen { onEditLayer(layer.id) }
                                    },
                                )
                            }
                        }
                    }
                    val full = current.layers.size >= AlertLimits.MaxStoredLayers
                    LunoSettingsRow(
                        title = stringResource(R.string.float_ball_preset_add_layer),
                        subtitle = if (full) {
                            stringResource(R.string.float_ball_preset_add_full)
                        } else {
                            stringResource(R.string.float_ball_preset_add_layer_hint)
                        },
                        icon = Icons.Default.Add,
                        shape = LargeShape,
                        enabled = !full,
                        onClick = {
                            if (!full) commitNameThen { onAddLayer() }
                        },
                    )
                }
            }
        }
    }
}