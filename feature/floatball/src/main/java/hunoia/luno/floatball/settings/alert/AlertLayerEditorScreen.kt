package hunoia.luno.floatball.settings.alert

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.config.model.AlertFamily
import hunoia.luno.config.model.AlertLayer
import hunoia.luno.config.model.AlertParticleShape
import hunoia.luno.config.model.AlertPreset
import hunoia.luno.config.model.ParticleArea
import hunoia.luno.config.model.ParticleHaloStyle
import hunoia.luno.config.model.ParticleLook
import hunoia.luno.config.model.ParticlePath
import hunoia.luno.config.model.RevealDirection
import hunoia.luno.config.model.RevealPattern
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.LunoFab
import hunoia.luno.ui.component.PressFilterChip
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
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * 单层编辑器：外观 / 运动 / 时序 / 高级，**全部字段都暴露**。
 *
 * 改动立即落盘；「试播这一层」走服务侧同一条渲染路径，不写配置。
 * 结构操作（上移 / 下移 / 复制 / 删除）也在这里，顺序 = 绘制顺序。
 */
/**
 * 单层编辑器：基础 / 时序 / 形态参数 / 高级，**全部字段可及**。
 *
 * v5.1 起所有分组固定全展开（不可收起），从上到下就是基础 / 参数 / 时序 / 高级；参数改动立即落盘。
 * 试播走右下 FAB（只播这一层）；上移 / 下移 / 复制 / 删除在本页顶栏。
 */
@Composable
fun AlertLayerEditorScreen(
    presetId: String,
    layerId: String,
    onBack: () -> Unit,
    onGone: () -> Unit,
) {
    val editor = rememberAlertEditor()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val facades = LocalFacades.current
    var leaving by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var nameDraft by remember(layerId) { mutableStateOf("") }
    val currentPreset = editor.library.presetById(presetId)
    val layer = currentPreset?.layers?.firstOrNull { it.id == layerId }

    LaunchedEffect(presetId, layerId, currentPreset, leaving) {
        if ((currentPreset == null || layer == null) && !leaving) onGone()
    }
    if (currentPreset == null || layer == null) return
    LaunchedEffect(layer.id) { nameDraft = layer.name }


    fun updatePreset(transform: (AlertPreset) -> AlertPreset) {
        scope.launch {
            editor.commitLibrary { lib ->
                lib.copy(
                    presets = lib.presets.map {
                        if (it.id == currentPreset.id) transform(it) else it
                    },
                )
            }
        }
    }

    fun update(transform: (AlertLayer) -> AlertLayer) {
        updatePreset { it.withReplacedLayer(transform(layer)) }
    }

    val pushLayer: (AlertLayer) -> Unit = { updated -> update { updated } }

    fun leave() {
        if (leaving) return
        val trimmed = nameDraft.trim()
        leaving = true
        if (trimmed == layer.name) {
            onBack()
            return
        }
        scope.launch {
            editor.commitLibrary { lib ->
                lib.copy(
                    presets = lib.presets.map {
                        if (it.id == currentPreset.id) {
                            it.withReplacedLayer(layer.copy(name = trimmed))
                        } else {
                            it
                        }
                    },
                )
            }
            onBack()
        }
    }

    fun previewLayer() {
        facades.system.previewFloatBallAlertPreset(
            context,
            currentPreset.withReplacedLayer(layer),
            layer.id,
        )
    }

    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    val playable = layer.enabled

    LunoScreenLazy(
        title = stringResource(R.string.float_ball_layer_title),
        onBack = { leave() },
        bottomContentPadding = FloatingContentBottom,
        floatingContent = {
            LunoFab(
                onClick = {
                    if (playable) {
                        previewLayer()
                    } else {
                        toast(context.getString(R.string.float_ball_layer_preview_disabled))
                    }
                },
                icon = Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.float_ball_layer_preview),
                containerColor = if (playable) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
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
                    text = stringResource(R.string.float_ball_layer_move_up),
                    leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) },
                    enabled = currentPreset.layers.indexOfFirst { it.id == layer.id } > 0,
                    onClick = {
                        menuOpen = false
                        updatePreset { it.withMovedLayer(layer.id, -1) }
                    },
                )
                LunoDropdownMenuItem(
                    text = stringResource(R.string.float_ball_layer_move_down),
                    leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) },
                    enabled = currentPreset.layers.indexOfFirst { it.id == layer.id } <
                        currentPreset.layers.lastIndex,
                    onClick = {
                        menuOpen = false
                        updatePreset { it.withMovedLayer(layer.id, 1) }
                    },
                )
                LunoDropdownMenuItem(
                    text = stringResource(R.string.float_ball_layer_duplicate),
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    enabled = currentPreset.addBlockedBy(layer.family, addingStored = true) == null,
                    onClick = {
                        menuOpen = false
                        val blocked = currentPreset.addBlockedBy(layer.family, addingStored = true)
                        if (blocked != null) {
                            toast(
                                context.getString(
                                    R.string.float_ball_preset_toggle_blocked,
                                    context.getString(blocked.reasonRes()),
                                ),
                            )
                            return@LunoDropdownMenuItem
                        }
                        updatePreset { it.withAddedLayer(layer.duplicated()) }
                    },
                )
                LunoDropdownMenuItem(
                    text = stringResource(R.string.float_ball_layer_delete),
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
                    title = stringResource(R.string.float_ball_layer_delete_confirm_title),
                    text = stringResource(R.string.float_ball_layer_delete_confirm_text),
                    destructive = true,
                    onDismissRequest = { confirmDelete = false },
                    onConfirm = {
                        confirmDelete = false
                        leaving = true
                        scope.launch {
                            val next = currentPreset.withoutLayer(layer.id)
                            editor.commitLibrary { lib ->
                                lib.copy(
                                    presets = lib.presets.map {
                                        if (it.id == next.id) next else it
                                    },
                                )
                            }
                            onBack()
                        }
                    },
                )
            }
        },
    ) {
        item(key = "base") {
            LunoGroup(
                title = stringResource(R.string.float_ball_layer_group_base),
                subtitle = stringResource(R.string.float_ball_layer_group_base_hint),
            ) {
                val rows = segmentedRows {
                    item { shape ->
                        LunoSwitchRow(
                            title = stringResource(R.string.float_ball_layer_enabled),
                            subtitle = stringResource(R.string.float_ball_layer_enabled_subtitle),
                            checked = layer.enabled,
                            shape = shape,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    val blocked = currentPreset.addBlockedBy(
                                        layer.family,
                                        addingStored = false,
                                    )
                                    if (blocked != null) {
                                        toast(
                                            context.getString(
                                                R.string.float_ball_preset_toggle_blocked,
                                                context.getString(blocked.reasonRes()),
                                            ),
                                        )
                                        return@LunoSwitchRow
                                    }
                                }
                                update { it.copy(enabled = checked) }
                            },
                        )
                    }
                    item { shape ->
                        LunoInputRow(
                            label = stringResource(R.string.float_ball_layer_name),
                            value = nameDraft,
                            onValueChange = { nameDraft = it.take(24) },
                            placeholder = stringResource(layer.family.labelRes()),
                            shape = shape,
                        )
                    }
                    item { shape ->
                        AlertPaletteRow(
                            title = stringResource(R.string.float_ball_layer_color),
                            library = editor.library,
                            selectedId = layer.color.paletteId,
                            followLabel = stringResource(R.string.float_ball_layer_color_follow),
                            shape = shape,
                            onSelect = { id ->
                                update { it.copy(color = layer.color.copy(paletteId = id)) }
                            },
                        )
                    }
                }
                SegmentedRows(rows)
            }
        }
        item(key = "params") {
            LayerParams(
                layer = layer,
                onUpdate = pushLayer,
            )
        }
        item(key = "timing") {
            LunoGroup(
                title = stringResource(R.string.float_ball_layer_group_timing),
            ) {
                val rows = segmentedRows {
                    item { shape ->
                        LayerIntSlider(
                            label = stringResource(R.string.float_ball_layer_base_millis),
                            value = layer.timing.baseMillis,
                            range = 200..4000,
                            suffix = " ms",
                            shape = shape,
                            onCommit = { value ->
                                update { it.copy(timing = it.timing.copy(baseMillis = value)) }
                            },
                        )
                    }
                    if (layer.family != hunoia.luno.config.model.AlertFamily.Particles) {
                        item { shape ->
                            LayerIntSlider(
                                label = stringResource(R.string.float_ball_layer_cycles),
                                value = layer.timing.cycles,
                                range = 1..10,
                                shape = shape,
                                onCommit = { value ->
                                    update { it.copy(timing = it.timing.copy(cycles = value)) }
                                },
                            )
                        }
                    }
                    item { shape ->
                        LayerIntSlider(
                            label = stringResource(R.string.float_ball_layer_layer_speed),
                            value = layer.timing.speedPercent,
                            range = 25..300,
                            suffix = "%",
                            shape = shape,
                            onCommit = { value ->
                                update {
                                    it.copy(timing = it.timing.copy(speedPercent = value))
                                }
                            },
                        )
                    }
                }
                SegmentedRows(rows)
            }
        }
        item(key = "advanced") {
            LayerAdvancedParams(
                layer = layer,
                onUpdate = pushLayer,
            )
        }
    }
}

/** 主参数区：按 kind 分派。 */
@Composable
private fun LayerParams(
    layer: AlertLayer,
    onUpdate: (AlertLayer) -> Unit,
) {
    when (layer.family) {
        hunoia.luno.config.model.AlertFamily.Edge -> EdgeParamsBlock(
            layer = layer,
            onUpdate = onUpdate,
        )
        hunoia.luno.config.model.AlertFamily.Ring -> RingParamsBlock(
            layer = layer,
            onUpdate = onUpdate,
        )
        hunoia.luno.config.model.AlertFamily.Capsule -> CapsuleParamsBlock(
            layer = layer,
            onUpdate = onUpdate,
        )
        hunoia.luno.config.model.AlertFamily.Wash -> WashParamsBlock(
            layer = layer,
            onUpdate = onUpdate,
        )
        hunoia.luno.config.model.AlertFamily.Cover -> CoverParamsBlock(
            layer = layer,
            onUpdate = onUpdate,
        )
        hunoia.luno.config.model.AlertFamily.Particles -> ParticleParamsBlock(
            layer = layer,
            onUpdate = onUpdate,
        )
        hunoia.luno.config.model.AlertFamily.Reveal -> RevealParamsBlock(
            layer = layer,
            onUpdate = onUpdate,
        )
    }
}

/** 高级区：冷门但真实存在的字段（混合模式 / 翻面 / 抖动 / 遮罩方向）。 */
@Composable
private fun LayerAdvancedParams(
    layer: AlertLayer,
    onUpdate: (AlertLayer) -> Unit,
) {
    LunoGroup(
        title = stringResource(R.string.float_ball_layer_group_advanced_fields),
    ) {
        val rows = segmentedRows {
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_opacity),
                    value = layer.opacity,
                    range = 0f..1f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value -> onUpdate(layer.copy(opacity = value)) },
                )
            }
            when (layer.family) {
                hunoia.luno.config.model.AlertFamily.Particles -> {
                    val params = layer.particle ?: return@segmentedRows
                    item { shape ->
                        LunoSwitchRow(
                            title = stringResource(R.string.float_ball_layer_blend_add),
                            subtitle = stringResource(R.string.float_ball_layer_blend_add_hint),
                            checked = params.look.blendAdd,
                            shape = shape,
                            onCheckedChange = { checked ->
                                onUpdate(
                                    layer.withLook(params.look.copy(blendAdd = checked)),
                                )
                            },
                        )
                    }
                    if (params.look.shape == AlertParticleShape.CustomImage) {
                        item { shape ->
                            LunoSwitchRow(
                                title = stringResource(R.string.float_ball_layer_tint_image),
                                subtitle = stringResource(R.string.float_ball_layer_tint_image_hint),
                                checked = params.look.tintImage,
                                shape = shape,
                                onCheckedChange = { checked ->
                                    onUpdate(
                                        layer.withLook(params.look.copy(tintImage = checked)),
                                    )
                                },
                            )
                        }
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_motion_bounce),
                            value = params.motion.bounce,
                            range = 0f..1f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                onUpdate(
                                    layer.withMotion(params.motion.copy(bounce = value)),
                                )
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_motion_attract),
                            value = params.motion.attract,
                            range = 0f..1f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                onUpdate(
                                    layer.withMotion(params.motion.copy(attract = value)),
                                )
                            },
                        )
                    }
                }
                hunoia.luno.config.model.AlertFamily.Edge,
                hunoia.luno.config.model.AlertFamily.Ring,
                hunoia.luno.config.model.AlertFamily.Capsule,
                hunoia.luno.config.model.AlertFamily.Wash,
                hunoia.luno.config.model.AlertFamily.Cover,
                -> Unit
                hunoia.luno.config.model.AlertFamily.Reveal -> {
                    val params = layer.reveal ?: return@segmentedRows
                    item { shape ->
                        LayerDropdownRow(
                            title = stringResource(R.string.float_ball_layer_reveal_direction),
                            options = RevealDirection.entries,
                            selected = params.maskDirection,
                            shape = shape,
                            optionLabel = { direction ->
                                when (direction) {
                                    RevealDirection.LeftToRight ->
                                        stringResource(R.string.float_ball_layer_direction_ltr)
                                    RevealDirection.RightToLeft ->
                                        stringResource(R.string.float_ball_layer_direction_rtl)
                                    RevealDirection.TopToBottom ->
                                        stringResource(R.string.float_ball_layer_direction_ttb)
                                    RevealDirection.BottomToTop ->
                                        stringResource(R.string.float_ball_layer_direction_btt)
                                }
                            },
                            onSelect = { direction ->
                                onUpdate(layer.withReveal(params.copy(maskDirection = direction)))
                            },
                        )
                    }
                }
            }
        }
        SegmentedRows(rows)
    }
}

/** 形态切换：一排单选 chip，选中的那个就是当前绘制形态（不叠加）。 */
@Composable
private fun <T> ShapeChips(
    options: List<T>,
    selected: T,
    label: (T) -> Int,
    onSelect: (T) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options.size) { index ->
            val option = options[index]
            PressFilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(stringResource(label(option))) },
            )
        }
    }
}

@Composable
private fun EdgeParamsBlock(
    layer: AlertLayer,
    onUpdate: (AlertLayer) -> Unit,
) {
    val params = layer.edge ?: return
    fun set(transform: (hunoia.luno.config.model.EdgeParams) -> hunoia.luno.config.model.EdgeParams) =
        onUpdate(layer.copy(edge = transform(params)))
    LunoGroup(
        title = stringResource(R.string.float_ball_layer_group_appearance),
        subtitle = stringResource(R.string.float_ball_layer_group_appearance_hint),
    ) {
        val rows = segmentedRows {
            item {
                ShapeChips(
                    options = hunoia.luno.config.model.EdgeShape.entries,
                    selected = params.shape,
                    label = { it.labelRes() },
                    onSelect = { value -> set { it.copy(shape = value) } },
                )
            }
            if (params.shape != hunoia.luno.config.model.EdgeShape.Lightning) {
                item { shape ->
                    LayerDropdownRow(
                        title = stringResource(R.string.float_ball_layer_backend),
                        options = hunoia.luno.config.model.AlertRenderBackend.entries,
                        selected = params.backend,
                        shape = shape,
                        optionLabel = { stringResource(it.labelRes()) },
                        onSelect = { backend -> set { it.copy(backend = backend) } },
                    )
                }
            }
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_corner_radius),
                    value = params.cornerRadiusDp.toFloat(),
                    range = 0f..96f,
                    decimals = 0,
                    shape = shape,
                    onCommit = { value -> set { it.copy(cornerRadiusDp = value.roundToInt()) } },
                )
            }
            when (params.shape) {
                hunoia.luno.config.model.EdgeShape.Breathing -> {
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_depth),
                            value = params.depthDp,
                            range = 2f..64f,
                            decimals = 1,
                            shape = shape,
                            onCommit = { value -> set { it.copy(depthDp = value) } },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_hardness),
                            value = params.hardness,
                            range = 1f..4f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value -> set { it.copy(hardness = value) } },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_breath_amount),
                            value = params.breathAmount,
                            range = 0f..1f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value -> set { it.copy(breathAmount = value) } },
                        )
                    }
                }
                hunoia.luno.config.model.EdgeShape.Sweep -> {
                    item { shape ->
                        LayerDropdownRow(
                            title = stringResource(R.string.float_ball_layer_path),
                            options = hunoia.luno.config.model.BandPath.entries,
                            selected = params.path,
                            shape = shape,
                            optionLabel = { stringResource(it.labelRes()) },
                            onSelect = { path -> set { it.copy(path = path) } },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_travel),
                            value = params.travel,
                            range = 0f..1f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value -> set { it.copy(travel = value) } },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_head_width),
                            value = params.headWidthDp,
                            range = 2f..48f,
                            decimals = 1,
                            shape = shape,
                            onCommit = { value -> set { it.copy(headWidthDp = value) } },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_depth),
                            value = params.depthDp,
                            range = 2f..64f,
                            decimals = 1,
                            shape = shape,
                            onCommit = { value -> set { it.copy(depthDp = value) } },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_hardness),
                            value = params.hardness,
                            range = 1f..4f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value -> set { it.copy(hardness = value) } },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_tail_length),
                            value = params.tailLength,
                            range = 0f..0.9f,
                            decimals = 2,
                            shape = shape,
                            display = { value -> "${(value * 100).roundToInt()}%" },
                            onCommit = { value -> set { it.copy(tailLength = value) } },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_tail_falloff),
                            value = params.tailFalloff,
                            range = 0.5f..4f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value -> set { it.copy(tailFalloff = value) } },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_tail_alpha),
                            value = params.tailAlpha,
                            range = 0f..1f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value -> set { it.copy(tailAlpha = value) } },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_head_boost),
                            value = params.headBoost,
                            range = 0f..1f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value -> set { it.copy(headBoost = value) } },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_noise),
                            value = params.noise,
                            range = 0f..1f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value -> set { it.copy(noise = value) } },
                        )
                    }
                    item { shape ->
                        LunoSwitchRow(
                            title = stringResource(R.string.float_ball_layer_dual),
                            checked = params.dual,
                            shape = shape,
                            onCheckedChange = { checked -> set { it.copy(dual = checked) } },
                        )
                    }
                    item { shape ->
                        LunoSwitchRow(
                            title = stringResource(R.string.float_ball_layer_reverse),
                            checked = params.reverse,
                            shape = shape,
                            onCheckedChange = { checked -> set { it.copy(reverse = checked) } },
                        )
                    }
                }
                hunoia.luno.config.model.EdgeShape.Wave -> {
                    item { shape ->
                        LayerIntSlider(
                            label = stringResource(R.string.float_ball_layer_crests),
                            value = params.crests,
                            range = 2..6,
                            shape = shape,
                            onCommit = { value -> set { it.copy(crests = value) } },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_depth),
                            value = params.depthDp,
                            range = 2f..64f,
                            decimals = 1,
                            shape = shape,
                            onCommit = { value -> set { it.copy(depthDp = value) } },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_wave_width),
                            value = params.waveWidthDp,
                            range = 2f..64f,
                            decimals = 1,
                            shape = shape,
                            onCommit = { value -> set { it.copy(waveWidthDp = value) } },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_hardness),
                            value = params.hardness,
                            range = 1f..4f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value -> set { it.copy(hardness = value) } },
                        )
                    }
                }
                hunoia.luno.config.model.EdgeShape.Lightning -> {
                    item { shape ->
                        LayerIntSlider(
                            label = stringResource(R.string.float_ball_layer_bolt_flashes),
                            value = params.boltFlashes,
                            range = 1..8,
                            shape = shape,
                            onCommit = { value -> set { it.copy(boltFlashes = value) } },
                        )
                    }
                    item { shape ->
                        LayerIntSlider(
                            label = stringResource(R.string.float_ball_layer_bolts),
                            value = params.bolts,
                            range = 1..8,
                            shape = shape,
                            onCommit = { value -> set { it.copy(bolts = value) } },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_bolt_window),
                            value = params.boltWindow,
                            range = 0.05f..0.9f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value -> set { it.copy(boltWindow = value) } },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_stroke),
                            value = params.boltWidthDp,
                            range = 0.5f..6f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value -> set { it.copy(boltWidthDp = value) } },
                        )
                    }
                    item { shape ->
                        LayerIntSlider(
                            label = stringResource(R.string.float_ball_layer_bolt_reach),
                            value = params.boltReachPercent,
                            range = 10..90,
                            suffix = "%",
                            shape = shape,
                            onCommit = { value -> set { it.copy(boltReachPercent = value) } },
                        )
                    }
                }
            }
        }
        SegmentedRows(rows)
    }
}


@Composable
private fun ParticleParamsBlock(
    layer: AlertLayer,
    onUpdate: (AlertLayer) -> Unit,
) {
    val params = layer.particle ?: return
    fun set(transform: (hunoia.luno.config.model.ParticleParams) -> hunoia.luno.config.model.ParticleParams) =
        onUpdate(layer.copy(particle = transform(params)))
    LunoGroup(
        title = stringResource(R.string.float_ball_layer_quick_shapes),
        subtitle = stringResource(R.string.float_ball_layer_quick_shapes_hint),
    ) {
        ParticleQuickShapes { picked -> onUpdate(layer.copy(particle = picked)) }
    }
    LunoGroup(
        title = stringResource(R.string.float_ball_layer_group_emitter),
    ) {
        val rows = segmentedRows {
            item { shape ->
                LayerDropdownRow(
                    title = stringResource(R.string.float_ball_layer_area),
                    options = ParticleArea.entries,
                    selected = params.emitter.area,
                    shape = shape,
                    optionLabel = { stringResource(it.labelRes()) },
                    onSelect = { area ->
                        set { it.copy(emitter = it.emitter.copy(area = area)) }
                    },
                )
            }
            item { shape ->
                LunoSwitchRow(
                    title = stringResource(R.string.float_ball_layer_anchor_per_wave),
                    subtitle = stringResource(R.string.float_ball_layer_anchor_per_wave_hint),
                    checked = params.emitter.anchorPerWave,
                    shape = shape,
                    onCheckedChange = { checked ->
                        set { it.copy(emitter = it.emitter.copy(anchorPerWave = checked)) }
                    },
                )
            }
            if (params.emitter.anchorPerWave) {
                item { shape ->
                    LayerIntSlider(
                        label = stringResource(R.string.float_ball_layer_anchor_count),
                        value = params.emitter.anchorCount,
                        range = 1..8,
                        shape = shape,
                        onCommit = { value ->
                            set { it.copy(emitter = it.emitter.copy(anchorCount = value)) }
                        },
                    )
                }
            }
            item { shape ->
                LayerDropdownRow(
                    title = stringResource(R.string.float_ball_layer_direction_mode),
                    options = hunoia.luno.config.model.ParticleDirection.entries,
                    selected = params.emitter.directionMode,
                    shape = shape,
                    optionLabel = { stringResource(it.labelRes()) },
                    onSelect = { mode ->
                        set { it.copy(emitter = it.emitter.copy(directionMode = mode)) }
                    },
                )
            }
            item { shape ->
                LayerIntSlider(
                    label = stringResource(R.string.float_ball_layer_count),
                    value = params.emitter.count,
                    range = 1..200,
                    shape = shape,
                    onCommit = { value -> set { it.copy(emitter = it.emitter.copy(count = value)) } },
                )
            }
            item { shape ->
                LayerIntSlider(
                    label = stringResource(R.string.float_ball_layer_waves),
                    value = params.emitter.waves,
                    range = 1..6,
                    shape = shape,
                    onCommit = { value -> set { it.copy(emitter = it.emitter.copy(waves = value)) } },
                )
            }
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_launch_height),
                    value = params.emitter.launchHeight,
                    range = 0f..0.9f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value ->
                        set { it.copy(emitter = it.emitter.copy(launchHeight = value)) }
                    },
                )
            }
            if (params.emitter.launchHeight > 0.001f) {
                item { shape ->
                    LayerFloatSlider(
                        label = stringResource(R.string.float_ball_layer_launch_share),
                        value = params.emitter.launchShare,
                        range = 0.1f..0.9f,
                        decimals = 2,
                        shape = shape,
                        onCommit = { value ->
                            set { it.copy(emitter = it.emitter.copy(launchShare = value)) }
                        },
                    )
                }
            }
            if (params.emitter.directionMode == hunoia.luno.config.model.ParticleDirection.Fixed) {
                item { shape ->
                    LayerFloatSlider(
                        label = stringResource(R.string.float_ball_layer_direction),
                        value = params.emitter.directionDeg,
                        range = 0f..360f,
                        decimals = 0,
                        shape = shape,
                        display = { value -> "${value.roundToInt()}° ${directionArrow(value)}" },
                        onCommit = { value ->
                            set { it.copy(emitter = it.emitter.copy(directionDeg = value)) }
                        },
                    )
                }
            }
            if (params.emitter.directionMode != hunoia.luno.config.model.ParticleDirection.Radial) {
                item { shape ->
                    LayerFloatSlider(
                        label = stringResource(R.string.float_ball_layer_spread),
                        value = params.emitter.spreadDeg,
                        range = 0f..360f,
                        decimals = 0,
                        shape = shape,
                        onCommit = { value ->
                            set { it.copy(emitter = it.emitter.copy(spreadDeg = value)) }
                        },
                    )
                }
            }
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_speed),
                    value = params.emitter.speed,
                    range = 0.05f..2.5f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value -> set { it.copy(emitter = it.emitter.copy(speed = value)) } },
                )
            }
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_speed_jitter),
                    value = params.emitter.speedJitter,
                    range = 0f..1f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value -> set { it.copy(emitter = it.emitter.copy(speedJitter = value)) } },
                )
            }
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_delay_spread),
                    value = params.emitter.delaySpread,
                    range = 0f..1f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value -> set { it.copy(emitter = it.emitter.copy(delaySpread = value)) } },
                )
            }
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_life_share),
                    value = params.emitter.lifeShare,
                    range = 0.1f..1f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value -> set { it.copy(emitter = it.emitter.copy(lifeShare = value)) } },
                )
            }
        }
        SegmentedRows(rows)
    }
    LunoGroup(
        title = stringResource(R.string.float_ball_layer_group_motion),
    ) {
        val rows = segmentedRows {
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_motion_gravity),
                    value = params.motion.gravity,
                    range = -3f..3f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value -> set { it.copy(motion = it.motion.copy(gravity = value)) } },
                )
            }
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_motion_drag),
                    value = params.motion.drag,
                    range = 0f..1f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value -> set { it.copy(motion = it.motion.copy(drag = value)) } },
                )
            }
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_motion_wind),
                    value = params.motion.wind,
                    range = -1f..1f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value -> set { it.copy(motion = it.motion.copy(wind = value)) } },
                )
            }
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_motion_turbulence),
                    value = params.motion.turbulence,
                    range = 0f..1f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value -> set { it.copy(motion = it.motion.copy(turbulence = value)) } },
                )
            }
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_motion_brownian),
                    value = params.motion.brownian,
                    range = 0f..1f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value -> set { it.copy(motion = it.motion.copy(brownian = value)) } },
                )
            }
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_motion_orbit),
                    value = params.motion.orbit,
                    range = 0f..1f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value -> set { it.copy(motion = it.motion.copy(orbit = value)) } },
                )
            }
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_motion_spiral),
                    value = params.motion.spiral,
                    range = 0f..1f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value -> set { it.copy(motion = it.motion.copy(spiral = value)) } },
                )
            }
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_motion_sway),
                    value = params.motion.sway,
                    range = 0f..1f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value -> set { it.copy(motion = it.motion.copy(sway = value)) } },
                )
            }
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_motion_sway_cycles),
                    value = params.motion.swayCycles,
                    range = 0.5f..4f,
                    decimals = 1,
                    shape = shape,
                    onCommit = { value -> set { it.copy(motion = it.motion.copy(swayCycles = value)) } },
                )
            }
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_motion_spin),
                    value = params.motion.spin,
                    range = 0f..2f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value -> set { it.copy(motion = it.motion.copy(spin = value)) } },
                )
            }
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_motion_tilt),
                    value = params.motion.tilt,
                    range = 0f..1f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value -> set { it.copy(motion = it.motion.copy(tilt = value)) } },
                )
            }
            item { shape ->
                LayerDropdownRow(
                    title = stringResource(R.string.float_ball_layer_motion_path),
                    options = listOf<ParticlePath?>(null) + ParticlePath.entries,
                    selected = params.motion.path,
                    shape = shape,
                    optionLabel = { path ->
                        path?.labelRes()?.let { stringResource(it) }
                            ?: stringResource(R.string.float_ball_layer_path_none)
                    },
                    onSelect = { path ->
                        set { it.copy(motion = it.motion.copy(path = path)) }
                    },
                )
            }
        }
        SegmentedRows(rows)
    }
    LunoGroup(
        title = stringResource(R.string.float_ball_layer_group_look),
    ) {
        val rows = segmentedRows {
            item { rowShape ->
                LayerDropdownRow(
                    title = stringResource(R.string.float_ball_layer_shape),
                    options = AlertParticleShape.entries,
                    selected = params.look.shape,
                    shape = rowShape,
                    optionLabel = { stringResource(it.labelRes()) },
                    onSelect = { shape ->
                        set { it.copy(look = it.look.copy(shape = shape)) }
                    },
                )
            }
            if (params.look.shape == AlertParticleShape.CustomImage) {
                item { shape ->
                    ParticleImageRow(
                        look = params.look,
                        onLook = { look -> set { it.copy(look = look) } },
                        shape = shape,
                    )
                }
            }
            if (params.look.shape == AlertParticleShape.Emoji ||
                params.look.shape == AlertParticleShape.Text
            ) {
                item { shape ->
                    var text by remember(params.look.shape) {
                        mutableStateOf(params.look.text)
                    }
                    LunoInputRow(
                        label = stringResource(R.string.float_ball_layer_text),
                        value = text,
                        placeholder = stringResource(R.string.float_ball_layer_text_hint),
                        shape = shape,
                        onValueChange = {
                            text = it.take(16)
                            set { current ->
                                current.copy(look = current.look.copy(text = text))
                            }
                        },
                    )
                }
            }
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_size),
                    value = params.look.sizeDp,
                    range = 1f..24f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value -> set { it.copy(look = it.look.copy(sizeDp = value)) } },
                )
            }
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_size_jitter),
                    value = params.look.sizeJitter,
                    range = 0f..1f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value -> set { it.copy(look = it.look.copy(sizeJitter = value)) } },
                )
            }
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_alpha),
                    value = params.look.alpha,
                    range = 0.05f..1f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value -> set { it.copy(look = it.look.copy(alpha = value)) } },
                )
            }
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_alpha_jitter),
                    value = params.look.alphaJitter,
                    range = 0f..1f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value -> set { it.copy(look = it.look.copy(alphaJitter = value)) } },
                )
            }
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_size_shrink),
                    value = params.look.sizeShrink,
                    range = 0f..0.9f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value -> set { it.copy(look = it.look.copy(sizeShrink = value)) } },
                )
            }
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_fade_exp),
                    value = params.look.fadeExp,
                    range = 0.5f..4f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value -> set { it.copy(look = it.look.copy(fadeExp = value)) } },
                )
            }
            item { shape ->
                LunoSwitchRow(
                    title = stringResource(R.string.float_ball_layer_color_per_wave),
                    checked = params.look.colorPerWave,
                    shape = shape,
                    onCheckedChange = { checked ->
                        set { it.copy(look = it.look.copy(colorPerWave = checked)) }
                    },
                )
            }
            item { shape ->
                LayerDropdownRow(
                    title = stringResource(R.string.float_ball_layer_halo),
                    options = ParticleHaloStyle.entries,
                    selected = params.look.haloStyle,
                    shape = shape,
                    optionLabel = { stringResource(it.labelRes()) },
                    onSelect = { style ->
                        set { it.copy(look = it.look.copy(haloStyle = style)) }
                    },
                )
            }
            if (params.look.haloStyle != ParticleHaloStyle.Off) {
                item { shape ->
                    LayerFloatSlider(
                        label = stringResource(R.string.float_ball_layer_halo_alpha),
                        value = params.look.haloAlpha,
                        range = 0f..1f,
                        decimals = 2,
                        shape = shape,
                        onCommit = { value ->
                            set { it.copy(look = it.look.copy(haloAlpha = value)) }
                        },
                    )
                }
                item { shape ->
                    LayerFloatSlider(
                        label = stringResource(R.string.float_ball_layer_halo_scale),
                        value = params.look.haloScale,
                        range = 1f..4f,
                        decimals = 2,
                        shape = shape,
                        onCommit = { value ->
                            set { it.copy(look = it.look.copy(haloScale = value)) }
                        },
                    )
                }
            }
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_trail_length),
                    value = params.look.trailDp,
                    range = 0f..64f,
                    decimals = 0,
                    shape = shape,
                    onCommit = { value ->
                        set { it.copy(look = it.look.copy(trailDp = value)) }
                    },
                )
            }
            if (params.look.trailDp > 0.5f) {
                item { shape ->
                    LayerFloatSlider(
                        label = stringResource(R.string.float_ball_layer_trail_alpha),
                        value = params.look.trailAlpha,
                        range = 0f..1f,
                        decimals = 2,
                        shape = shape,
                        onCommit = { value ->
                            set { it.copy(look = it.look.copy(trailAlpha = value)) }
                        },
                    )
                }
            }
            item { shape ->
                LunoSwitchRow(
                    title = stringResource(R.string.float_ball_layer_blink),
                    checked = params.look.blink,
                    shape = shape,
                    onCheckedChange = { checked ->
                        set { it.copy(look = it.look.copy(blink = checked)) }
                    },
                )
            }
            if (params.look.blink) {
                item { shape ->
                    LayerFloatSlider(
                        label = stringResource(R.string.float_ball_layer_blink_cycles),
                        value = params.look.blinkCycles,
                        range = 0.5f..6f,
                        decimals = 1,
                        shape = shape,
                        onCommit = { value ->
                            set { it.copy(look = it.look.copy(blinkCycles = value)) }
                        },
                    )
                }
            }
        }
        SegmentedRows(rows)
    }
}

@Composable
private fun RingParamsBlock(
    layer: AlertLayer,
    onUpdate: (AlertLayer) -> Unit,
) {
    val params = layer.ring ?: return
    fun set(transform: (hunoia.luno.config.model.RingParams) -> hunoia.luno.config.model.RingParams) =
        onUpdate(layer.copy(ring = transform(params)))
    LunoGroup(
        title = stringResource(R.string.float_ball_layer_group_appearance),
        subtitle = stringResource(R.string.float_ball_layer_group_appearance_hint),
    ) {
        val rows = segmentedRows {
            item { shape ->
                LayerDropdownRow(
                    title = stringResource(R.string.float_ball_layer_ring_style),
                    options = hunoia.luno.config.model.RingStyle.entries,
                    selected = params.style,
                    shape = shape,
                    optionLabel = { stringResource(it.labelRes()) },
                    onSelect = { style -> set { it.copy(style = style) } },
                )
            }
            item { shape ->
                LayerIntSlider(
                    label = stringResource(R.string.float_ball_layer_rings),
                    value = params.ringsPerCycle,
                    range = 1..4,
                    shape = shape,
                    onCommit = { value -> set { it.copy(ringsPerCycle = value) } },
                )
            }
            item { shape ->
                LayerIntSlider(
                    label = stringResource(R.string.float_ball_layer_travel),
                    value = params.travelPercent,
                    range = 0..300,
                    suffix = "%",
                    shape = shape,
                    onCommit = { value -> set { it.copy(travelPercent = value) } },
                )
            }
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_stroke),
                    value = params.strokeWidthDp,
                    range = 1f..12f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value -> set { it.copy(strokeWidthDp = value) } },
                )
            }
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_halo_scale),
                    value = params.haloScale,
                    range = 0f..2f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value -> set { it.copy(haloScale = value) } },
                )
            }
            item { shape ->
                LunoSwitchRow(
                    title = stringResource(R.string.float_ball_layer_center_at_anchor),
                    checked = params.centerAtAnchor,
                    shape = shape,
                    onCheckedChange = { checked -> set { it.copy(centerAtAnchor = checked) } },
                )
            }
        }
        SegmentedRows(rows)
    }
}

@Composable
private fun CapsuleParamsBlock(
    layer: AlertLayer,
    onUpdate: (AlertLayer) -> Unit,
) {
    val params = layer.capsule ?: return
    fun set(
        transform: (hunoia.luno.config.model.CapsuleParams) -> hunoia.luno.config.model.CapsuleParams,
    ) = onUpdate(layer.copy(capsule = transform(params)))
    LunoGroup(
        title = stringResource(R.string.float_ball_layer_group_appearance),
        subtitle = stringResource(R.string.float_ball_layer_group_appearance_hint),
    ) {
        val rows = segmentedRows {
            item {
                ShapeChips(
                    options = hunoia.luno.config.model.CapsuleShape.entries,
                    selected = params.shape,
                    label = { it.labelRes() },
                    onSelect = { value -> set { it.copy(shape = value) } },
                )
            }
            if (params.shape != hunoia.luno.config.model.CapsuleShape.Sweep || !params.fill) {
                item { shape ->
                    LayerFloatSlider(
                        label = stringResource(R.string.float_ball_layer_stroke),
                        value = params.strokeWidthDp,
                        range = 0.5f..6f,
                        decimals = 2,
                        shape = shape,
                        onCommit = { value -> set { it.copy(strokeWidthDp = value) } },
                    )
                }
            }
            when (params.shape) {
                hunoia.luno.config.model.CapsuleShape.Sweep -> {
                    item { shape ->
                        LunoSwitchRow(
                            title = stringResource(R.string.float_ball_layer_fill),
                            checked = params.fill,
                            shape = shape,
                            onCheckedChange = { checked -> set { it.copy(fill = checked) } },
                        )
                    }
                    if (!params.fill) {
                        item { shape ->
                            LayerIntSlider(
                                label = stringResource(R.string.float_ball_layer_heads),
                                value = params.heads,
                                range = 1..2,
                                shape = shape,
                                onCommit = { value -> set { it.copy(heads = value) } },
                            )
                        }
                    }
                    item { shape ->
                        LayerIntSlider(
                            label = stringResource(R.string.float_ball_layer_band_width_percent),
                            value = params.bandPercent,
                            range = 5..90,
                            suffix = "%",
                            shape = shape,
                            onCommit = { value -> set { it.copy(bandPercent = value) } },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_travel),
                            value = params.travel,
                            range = 0f..1f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value -> set { it.copy(travel = value) } },
                        )
                    }
                    if (!params.fill) {
                        item { shape ->
                            LayerFloatSlider(
                                label = stringResource(R.string.float_ball_layer_trail_scale),
                                value = params.trailScale,
                                range = 0f..4f,
                                decimals = 2,
                                shape = shape,
                                onCommit = { value -> set { it.copy(trailScale = value) } },
                            )
                        }
                        item { shape ->
                            LayerFloatSlider(
                                label = stringResource(R.string.float_ball_layer_trail_alpha),
                                value = params.trailAlpha,
                                range = 0f..1f,
                                decimals = 2,
                                shape = shape,
                                onCommit = { value -> set { it.copy(trailAlpha = value) } },
                            )
                        }
                    }
                    item { shape ->
                        LunoSwitchRow(
                            title = stringResource(R.string.float_ball_layer_reverse),
                            checked = params.reverse,
                            shape = shape,
                            onCheckedChange = { checked -> set { it.copy(reverse = checked) } },
                        )
                    }
                }
                hunoia.luno.config.model.CapsuleShape.Pulse -> {
                    item { shape ->
                        LayerIntSlider(
                            label = stringResource(R.string.float_ball_layer_glow_layers),
                            value = params.glowLayers,
                            range = 0..2,
                            shape = shape,
                            onCommit = { value -> set { it.copy(glowLayers = value) } },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_glow_scale),
                            value = params.glowScale,
                            range = 0f..2f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value -> set { it.copy(glowScale = value) } },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_breathe),
                            value = params.breatheScale,
                            range = 0f..1f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value -> set { it.copy(breatheScale = value) } },
                        )
                    }
                }
                hunoia.luno.config.model.CapsuleShape.Flip -> {
                    item { shape ->
                        LayerIntSlider(
                            label = stringResource(R.string.float_ball_layer_flip_degrees),
                            value = params.flipDegrees,
                            range = 90..360,
                            suffix = "°",
                            shape = shape,
                            onCommit = { value -> set { it.copy(flipDegrees = value) } },
                        )
                    }
                }
            }
        }
        SegmentedRows(rows)
    }
}

@Composable
private fun WashParamsBlock(
    layer: AlertLayer,
    onUpdate: (AlertLayer) -> Unit,
) {
    val params = layer.wash ?: return
    fun set(transform: (hunoia.luno.config.model.WashParams) -> hunoia.luno.config.model.WashParams) =
        onUpdate(layer.copy(wash = transform(params)))
    LunoGroup(
        title = stringResource(R.string.float_ball_layer_group_appearance),
        subtitle = stringResource(R.string.float_ball_layer_group_appearance_hint),
    ) {
        val rows = segmentedRows {
            item { shape ->
                LayerDropdownRow(
                    title = stringResource(R.string.float_ball_layer_wash_shape),
                    options = hunoia.luno.config.model.WashShape.entries,
                    selected = params.shape,
                    shape = shape,
                    optionLabel = { stringResource(it.labelRes()) },
                    onSelect = { shapeValue -> set { it.copy(shape = shapeValue) } },
                )
            }
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_amplitude),
                    value = params.amplitude,
                    range = 0f..1f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value -> set { it.copy(amplitude = value) } },
                )
            }
            if (params.shape == hunoia.luno.config.model.WashShape.Ring) {
                item { shape ->
                    LayerIntSlider(
                        label = stringResource(R.string.float_ball_layer_zoom),
                        value = params.zoomPercent,
                        range = 0..40,
                        suffix = "%",
                        shape = shape,
                        onCommit = { value -> set { it.copy(zoomPercent = value) } },
                    )
                }
            }
            if (params.shape != hunoia.luno.config.model.WashShape.Flat) {
                item { shape ->
                    LayerFloatSlider(
                        label = stringResource(R.string.float_ball_layer_vignette_softness),
                        value = params.softness,
                        range = 0f..1f,
                        decimals = 2,
                        shape = shape,
                        onCommit = { value -> set { it.copy(softness = value) } },
                    )
                }
            }
            item { shape ->
                LunoSwitchRow(
                    title = stringResource(R.string.float_ball_layer_wash_breath),
                    checked = params.breath,
                    shape = shape,
                    onCheckedChange = { checked -> set { it.copy(breath = checked) } },
                )
            }
        }
        SegmentedRows(rows)
    }
}

@Composable
private fun CoverParamsBlock(
    layer: AlertLayer,
    onUpdate: (AlertLayer) -> Unit,
) {
    val params = layer.cover ?: return
    fun set(transform: (hunoia.luno.config.model.CoverParams) -> hunoia.luno.config.model.CoverParams) =
        onUpdate(layer.copy(cover = transform(params)))
    LunoGroup(
        title = stringResource(R.string.float_ball_layer_group_appearance),
        subtitle = stringResource(R.string.float_ball_layer_group_appearance_hint),
    ) {
        val rows = segmentedRows {
            item { shape ->
                LayerDropdownRow(
                    title = stringResource(R.string.float_ball_layer_cover_mask),
                    options = hunoia.luno.config.model.CoverMask.entries,
                    selected = params.mask,
                    shape = shape,
                    optionLabel = { stringResource(it.labelRes()) },
                    onSelect = { mask -> set { it.copy(mask = mask) } },
                )
            }
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_amplitude),
                    value = params.amplitude,
                    range = 0f..1f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value -> set { it.copy(amplitude = value) } },
                )
            }
            if (params.mask == hunoia.luno.config.model.CoverMask.Bands) {
                item { shape ->
                    LayerIntSlider(
                        label = stringResource(R.string.float_ball_layer_bands),
                        value = params.bands,
                        range = 3..15,
                        shape = shape,
                        onCommit = { value -> set { it.copy(bands = value) } },
                    )
                }
            }
            if (params.mask == hunoia.luno.config.model.CoverMask.Shards) {
                item { shape ->
                    LayerIntSlider(
                        label = stringResource(R.string.float_ball_layer_shards),
                        value = params.shards,
                        range = 6..30,
                        shape = shape,
                        onCommit = { value -> set { it.copy(shards = value) } },
                    )
                }
            }
            if (params.mask == hunoia.luno.config.model.CoverMask.Slit) {
                item { shape ->
                    LayerFloatSlider(
                        label = stringResource(R.string.float_ball_layer_slit),
                        value = params.slitDp,
                        range = 2f..24f,
                        decimals = 1,
                        shape = shape,
                        onCommit = { value -> set { it.copy(slitDp = value) } },
                    )
                }
            }
            if (params.mask == hunoia.luno.config.model.CoverMask.Iris) {
                item { shape ->
                    LayerFloatSlider(
                        label = stringResource(R.string.float_ball_layer_rim),
                        value = params.rimDp,
                        range = 0f..6f,
                        decimals = 1,
                        shape = shape,
                        onCommit = { value -> set { it.copy(rimDp = value) } },
                    )
                }
            }
            if (params.mask == hunoia.luno.config.model.CoverMask.Circle ||
                params.mask == hunoia.luno.config.model.CoverMask.Shards
            ) {
                item { shape ->
                    LayerFloatSlider(
                        label = stringResource(R.string.float_ball_layer_vignette_softness),
                        value = params.softness,
                        range = 0f..1f,
                        decimals = 2,
                        shape = shape,
                        onCommit = { value -> set { it.copy(softness = value) } },
                    )
                }
            }
            if (params.mask != hunoia.luno.config.model.CoverMask.Shards) {
                item { shape ->
                    LayerFloatSlider(
                        label = stringResource(R.string.float_ball_layer_curve),
                        value = params.curve,
                        range = 1f..4f,
                        decimals = 2,
                        shape = shape,
                        onCommit = { value -> set { it.copy(curve = value) } },
                    )
                }
            }
        }
        SegmentedRows(rows)
    }
}
@Composable
private fun RevealParamsBlock(
    layer: AlertLayer,
    onUpdate: (AlertLayer) -> Unit,
) {
    val params = layer.reveal ?: return
    fun set(transform: (hunoia.luno.config.model.RevealParams) -> hunoia.luno.config.model.RevealParams) =
        onUpdate(layer.copy(reveal = transform(params)))
    LunoGroup(
        title = stringResource(R.string.float_ball_layer_group_appearance),
        subtitle = stringResource(R.string.float_ball_layer_reveal_hint),
    ) {
        val rows = segmentedRows {
            item { shape ->
                LayerDropdownRow(
                    title = stringResource(R.string.float_ball_layer_pattern),
                    options = RevealPattern.entries,
                    selected = params.pattern,
                    shape = shape,
                    optionLabel = { stringResource(it.labelRes()) },
                    onSelect = {pattern -> set { it.copy(pattern = pattern) } },
                )
            }
            item { shape ->
                LayerIntSlider(
                    label = stringResource(R.string.float_ball_layer_stagger),
                    value = params.staggerPercent,
                    range = 0..90,
                    suffix = "%",
                    shape = shape,
                    onCommit = { value -> set { it.copy(staggerPercent = value) } },
                )
            }
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_scale_from),
                    value = params.scaleFrom,
                    range = 0.2f..1f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value -> set { it.copy(scaleFrom = value) } },
                )
            }
        }
        SegmentedRows(rows)
    }
}

/** 自定义图片：选择后拷贝到 `filesDir/floatball_particle_shape`，只存文件名。 */
@Composable
private fun ParticleImageRow(
    look: ParticleLook,
    onLook: (ParticleLook) -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) {
            picking = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val name = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.filesDir, ParticleLook.ImageDirName).apply { mkdirs() }
                    val target = File(dir, "img_${UUID.randomUUID()}.png")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("no stream")
                    target.name
                }.getOrNull()
            }
            picking = false
            if (name != null) onLook(look.copy(customImage = name))
        }
    }
    LunoSettingsRow(
        title = stringResource(R.string.float_ball_layer_pick_image),
        subtitle = look.customImage.ifBlank {
            stringResource(R.string.float_ball_layer_pick_image_hint)
        },
        shape = shape,
        enabled = !picking,
        onClick = {
            picking = true
            picker.launch("image/*")
        },
    )
    if (look.customImage.isNotBlank()) {
        LunoSettingsRow(
            title = stringResource(R.string.float_ball_layer_remove_image),
            shape = shape,
            onClick = { onLook(look.copy(customImage = "")) },
        )
    }
}
