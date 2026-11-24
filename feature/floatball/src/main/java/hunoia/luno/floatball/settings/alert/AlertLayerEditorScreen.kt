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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.config.model.AlertKind
import hunoia.luno.config.model.AlertLayer
import hunoia.luno.config.model.AlertParticleShape
import hunoia.luno.config.model.AlertPreset
import hunoia.luno.config.model.BallPattern
import hunoia.luno.config.model.CapsulePattern
import hunoia.luno.config.model.EdgePattern
import hunoia.luno.config.model.ParticleArea
import hunoia.luno.config.model.ParticleHaloStyle
import hunoia.luno.config.model.ParticleLook
import hunoia.luno.config.model.ParticlePath
import hunoia.luno.config.model.RevealDirection
import hunoia.luno.config.model.RevealPattern
import hunoia.luno.config.model.ScreenPattern
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
 * 层编辑器的展开分组（单开）：基础 / 时序 / 形态参数 / 高级。
 *
 * 形态参数按 kind 不同，[Appearance] 是屏缘·球体·胶囊·全屏·揭示的公共外观组；
 * 粒子另用 [Emitter] / [Motion] / [Look]。
 */
internal enum class LayerSection {
    Base,
    Timing,
    Appearance,
    Emitter,
    Motion,
    Look,
    Advanced,
}

/**
 * 单层编辑器：基础 / 时序 / 形态参数 / 高级，**全部字段可及**。
 *
 * 分组可展开（单开），默认打开外观（粒子为“形状与光效”）；参数改动立即落盘。
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

    var openSection by remember(layerId) {
        mutableStateOf<LayerSection?>(
            if (layer.kind == AlertKind.Particle) LayerSection.Look else LayerSection.Appearance,
        )
    }

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
                    enabled = currentPreset.addBlockedBy(layer.kind, addingStored = true) == null,
                    onClick = {
                        menuOpen = false
                        val blocked = currentPreset.addBlockedBy(layer.kind, addingStored = true)
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
                collapsible = true,
                expanded = openSection == LayerSection.Base,
                onToggle = {
                    openSection = if (openSection == LayerSection.Base) null else LayerSection.Base
                },
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
                                        layer.kind,
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
                            placeholder = layer.patternLabel().orEmpty(),
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
        item(key = "timing") {
            LunoGroup(
                title = stringResource(R.string.float_ball_layer_group_timing),
                collapsible = true,
                expanded = openSection == LayerSection.Timing,
                onToggle = {
                    openSection = if (openSection == LayerSection.Timing) null else LayerSection.Timing
                },
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
                    if (layer.kind != AlertKind.Particle) {
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
        item(key = "params") {
            LayerParams(
                layer = layer,
                openSection = openSection,
                onSection = { openSection = it },
                onUpdate = pushLayer,
            )
        }
        if (layer.kind == AlertKind.Particle || layer.kind == AlertKind.Reveal) {
            item(key = "advanced") {
                LayerAdvancedParams(
                    layer = layer,
                    expanded = openSection == LayerSection.Advanced,
                    onToggle = {
                        openSection =
                            if (openSection == LayerSection.Advanced) null else LayerSection.Advanced
                    },
                    onUpdate = pushLayer,
                )
            }
        }
    }
}

/** 主参数区：按 kind 分派。 */
@Composable
private fun LayerParams(
    layer: AlertLayer,
    openSection: LayerSection?,
    onSection: (LayerSection) -> Unit,
    onUpdate: (AlertLayer) -> Unit,
) {
    when (layer.kind) {
        AlertKind.Edge -> EdgeParamsBlock(
            layer = layer,
            expanded = openSection == LayerSection.Appearance,
            onToggle = { onSection(LayerSection.Appearance) },
            onUpdate = onUpdate,
        )
        AlertKind.Particle -> ParticleParamsBlock(
            layer = layer,
            openSection = openSection,
            onSection = onSection,
            onUpdate = onUpdate,
        )
        AlertKind.Ball -> BallParamsBlock(
            layer = layer,
            expanded = openSection == LayerSection.Appearance,
            onToggle = { onSection(LayerSection.Appearance) },
            onUpdate = onUpdate,
        )
        AlertKind.Capsule -> CapsuleParamsBlock(
            layer = layer,
            expanded = openSection == LayerSection.Appearance,
            onToggle = { onSection(LayerSection.Appearance) },
            onUpdate = onUpdate,
        )
        AlertKind.Screen -> ScreenParamsBlock(
            layer = layer,
            expanded = openSection == LayerSection.Appearance,
            onToggle = { onSection(LayerSection.Appearance) },
            onUpdate = onUpdate,
        )
        AlertKind.Reveal -> RevealParamsBlock(
            layer = layer,
            expanded = openSection == LayerSection.Appearance,
            onToggle = { onSection(LayerSection.Appearance) },
            onUpdate = onUpdate,
        )
    }
}

/** 高级区：冷门但真实存在的字段（混合模式 / 翻面 / 抖动 / 遮罩方向）。 */
@Composable
private fun LayerAdvancedParams(
    layer: AlertLayer,
    expanded: Boolean,
    onToggle: () -> Unit,
    onUpdate: (AlertLayer) -> Unit,
) {
    LunoGroup(
        title = stringResource(R.string.float_ball_layer_group_advanced_fields),
        collapsible = true,
        expanded = expanded,
        onToggle = onToggle,
    ) {
        val rows = segmentedRows {
            when (layer.kind) {
                AlertKind.Particle -> {
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
                AlertKind.Ball -> Unit
                AlertKind.Capsule -> Unit
                AlertKind.Screen -> Unit
                AlertKind.Edge -> Unit
                AlertKind.Reveal -> {
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

@Composable
private fun EdgeParamsBlock(
    layer: AlertLayer,
    expanded: Boolean,
    onToggle: () -> Unit,
    onUpdate: (AlertLayer) -> Unit,
) {
    val params = layer.edge ?: return
    fun set(transform: (hunoia.luno.config.model.EdgeParams) -> hunoia.luno.config.model.EdgeParams) =
        onUpdate(layer.copy(edge = transform(params)))
    LunoGroup(
        title = stringResource(R.string.float_ball_layer_group_appearance),
        subtitle = stringResource(R.string.float_ball_layer_group_appearance_hint),
        collapsible = true,
        expanded = expanded,
        onToggle = onToggle,
    ) {
        val rows = segmentedRows {
            item { shape ->
                LayerDropdownRow(
                    title = stringResource(R.string.float_ball_layer_pattern),
                    options = EdgePattern.entries,
                    selected = params.pattern,
                    shape = shape,
                    optionLabel = { stringResource(it.labelRes()) },
                    onSelect = {pattern -> set { it.copy(pattern = pattern) } },
                )
            }
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_band_width),
                    value = params.bandWidthDp,
                    range = 2f..48f,
                    decimals = 1,
                    shape = shape,
                    onCommit = { value -> set { it.copy(bandWidthDp = value) } },
                )
            }
            if (params.pattern in setOf(
                    EdgePattern.Glow, EdgePattern.Ripple, EdgePattern.Comet,
                    EdgePattern.Ribbon, EdgePattern.Lightning,
                )
            ) {
                item { shape ->
                    LayerFloatSlider(
                        label = stringResource(R.string.float_ball_layer_corner_radius),
                        value = params.cornerRadiusDp.toFloat(),
                        range = 0f..80f,
                        decimals = 0,
                        shape = shape,
                        onCommit = { value -> set { it.copy(cornerRadiusDp = value.toInt()) } },
                    )
                }
            }
            if (params.pattern == EdgePattern.Glow || params.pattern == EdgePattern.Flow) {
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
            if (params.pattern == EdgePattern.Flow || params.pattern == EdgePattern.Comet ||
                params.pattern == EdgePattern.Ribbon
            ) {
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
            if (params.pattern == EdgePattern.Flow) {
                item { shape ->
                    LunoSwitchRow(
                        title = stringResource(R.string.float_ball_layer_dual_band),
                        checked = params.dualBand,
                        shape = shape,
                        onCheckedChange = { checked -> set { it.copy(dualBand = checked) } },
                    )
                }
            }
            if (params.pattern == EdgePattern.Ripple) {
                item { shape ->
                    LayerIntSlider(
                        label = stringResource(R.string.float_ball_layer_crests),
                        value = params.crests,
                        range = 2..6,
                        shape = shape,
                        onCommit = { value -> set { it.copy(crests = value) } },
                    )
                }
            }
            if (params.pattern == EdgePattern.Blade) {
                item { shape ->
                    LayerIntSlider(
                        label = stringResource(R.string.float_ball_layer_blade_height),
                        value = params.bladeHeightDp,
                        range = 40..360,
                        suffix = " dp",
                        shape = shape,
                        onCommit = { value -> set { it.copy(bladeHeightDp = value) } },
                    )
                }
            }
            if (params.pattern == EdgePattern.Fireworks ||
                params.pattern == EdgePattern.Lightning
            ) {
                item { shape ->
                    LayerIntSlider(
                        label = stringResource(R.string.float_ball_layer_strikes),
                        value = params.strikeCount,
                        range = 1..4,
                        shape = shape,
                        onCommit = { value -> set { it.copy(strikeCount = value) } },
                    )
                }
            }
        }
        SegmentedRows(rows)
    }
}

@Composable
private fun ParticleParamsBlock(
    layer: AlertLayer,
    openSection: LayerSection?,
    onSection: (LayerSection) -> Unit,
    onUpdate: (AlertLayer) -> Unit,
) {
    val params = layer.particle ?: return
    fun set(transform: (hunoia.luno.config.model.ParticleParams) -> hunoia.luno.config.model.ParticleParams) =
        onUpdate(layer.copy(particle = transform(params)))
    LunoGroup(
        title = stringResource(R.string.float_ball_layer_group_emitter),
        collapsible = true,
        expanded = openSection == LayerSection.Emitter,
        onToggle = { onSection(LayerSection.Emitter) },
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
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_spread),
                    value = params.emitter.spreadDeg,
                    range = 0f..360f,
                    decimals = 0,
                    shape = shape,
                    onCommit = { value -> set { it.copy(emitter = it.emitter.copy(spreadDeg = value)) } },
                )
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
        collapsible = true,
        expanded = openSection == LayerSection.Motion,
        onToggle = { onSection(LayerSection.Motion) },
    ) {
        val rows = segmentedRows {
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_motion_gravity),
                    value = params.motion.gravity,
                    range = 0f..3f,
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
                    range = 0f..1f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value -> set { it.copy(motion = it.motion.copy(spin = value)) } },
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
        collapsible = true,
        expanded = openSection == LayerSection.Look,
        onToggle = { onSection(LayerSection.Look) },
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
            // 拖尾只有「线段」形状读（见 drawEngineShape）；其它形状放出来是死旋钮。
            if (params.look.shape == AlertParticleShape.Line) {
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
                    title = stringResource(R.string.float_ball_layer_rotate),
                    checked = params.look.rotate,
                    shape = shape,
                    onCheckedChange = { checked ->
                        set { it.copy(look = it.look.copy(rotate = checked)) }
                    },
                )
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
private fun BallParamsBlock(
    layer: AlertLayer,
    expanded: Boolean,
    onToggle: () -> Unit,
    onUpdate: (AlertLayer) -> Unit,
) {
    val params = layer.ball ?: return
    fun set(transform: (hunoia.luno.config.model.BallParams) -> hunoia.luno.config.model.BallParams) =
        onUpdate(layer.copy(ball = transform(params)))
    LunoGroup(
        title = stringResource(R.string.float_ball_layer_group_appearance),
        subtitle = stringResource(R.string.float_ball_layer_group_appearance_hint),
        collapsible = true,
        expanded = expanded,
        onToggle = onToggle,
    ) {
        val rows = segmentedRows {
            item { shape ->
                LayerDropdownRow(
                    title = stringResource(R.string.float_ball_layer_pattern),
                    options = BallPattern.entries,
                    selected = params.pattern,
                    shape = shape,
                    optionLabel = { stringResource(it.labelRes()) },
                    onSelect = {pattern -> set { it.copy(pattern = pattern) } },
                )
            }
            item { shape ->
                LayerIntSlider(
                    label = stringResource(R.string.float_ball_layer_travel),
                    value = params.travelPercent,
                    range = 40..300,
                    suffix = "%",
                    shape = shape,
                    onCommit = { value -> set { it.copy(travelPercent = value) } },
                )
            }
            if (params.pattern == BallPattern.Ring || params.pattern == BallPattern.Ripple ||
                params.pattern == BallPattern.Shockwave
            ) {
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
            }
            if (params.pattern == BallPattern.Ring || params.pattern == BallPattern.Spiral) {
                item { shape ->
                    LayerIntSlider(
                        label = stringResource(R.string.float_ball_layer_rings),
                        value = params.ringsPerCycle,
                        range = 1..4,
                        shape = shape,
                        onCommit = { value -> set { it.copy(ringsPerCycle = value) } },
                    )
                }
            }
            if (params.pattern == BallPattern.Ring || params.pattern == BallPattern.Ripple ||
                params.pattern == BallPattern.Halo
            ) {
                item { shape ->
                    LayerFloatSlider(
                        label = stringResource(R.string.float_ball_layer_halo_scale),
                        value = params.haloScale,
                        range = 0.2f..2f,
                        decimals = 2,
                        shape = shape,
                        onCommit = { value -> set { it.copy(haloScale = value) } },
                    )
                }
            }
            if (params.pattern == BallPattern.Burst || params.pattern == BallPattern.Spiral ||
                params.pattern == BallPattern.Orbit3D
            ) {
                item { shape ->
                    LayerIntSlider(
                        label = stringResource(R.string.float_ball_layer_particle_count),
                        value = params.particleCount,
                        range = 8..80,
                        shape = shape,
                        onCommit = { value -> set { it.copy(particleCount = value) } },
                    )
                }
                item { shape ->
                    LayerFloatSlider(
                        label = stringResource(R.string.float_ball_layer_particle_size),
                        value = params.particleSizeDp,
                        range = 1f..8f,
                        decimals = 2,
                        shape = shape,
                        onCommit = { value -> set { it.copy(particleSizeDp = value) } },
                    )
                }
            }
            if (params.pattern == BallPattern.Ripple) {
                item { shape ->
                    LunoSwitchRow(
                        title = stringResource(R.string.float_ball_layer_center_at_ball),
                        checked = params.centerAtBall,
                        shape = shape,
                        onCheckedChange = { checked -> set { it.copy(centerAtBall = checked) } },
                    )
                }
            }
        }
        SegmentedRows(rows)
    }
}

@Composable
private fun CapsuleParamsBlock(
    layer: AlertLayer,
    expanded: Boolean,
    onToggle: () -> Unit,
    onUpdate: (AlertLayer) -> Unit,
) {
    val params = layer.capsule ?: return
    fun set(transform: (hunoia.luno.config.model.CapsuleParams) -> hunoia.luno.config.model.CapsuleParams) =
        onUpdate(layer.copy(capsule = transform(params)))
    LunoGroup(
        title = stringResource(R.string.float_ball_layer_group_appearance),
        subtitle = stringResource(R.string.float_ball_layer_group_appearance_hint),
        collapsible = true,
        expanded = expanded,
        onToggle = onToggle,
    ) {
        val rows = segmentedRows {
            item { shape ->
                LayerDropdownRow(
                    title = stringResource(R.string.float_ball_layer_pattern),
                    options = CapsulePattern.entries,
                    selected = params.pattern,
                    shape = shape,
                    optionLabel = { stringResource(it.labelRes()) },
                    onSelect = {pattern -> set { it.copy(pattern = pattern) } },
                )
            }
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
            if (params.pattern == CapsulePattern.Shimmer ||
                params.pattern == CapsulePattern.InnerSweep ||
                params.pattern == CapsulePattern.DualChase
            ) {
                item { shape ->
                    LayerIntSlider(
                        label = stringResource(R.string.float_ball_layer_band_width_percent),
                        value = params.bandWidthPercent,
                        range = 10..80,
                        suffix = "%",
                        shape = shape,
                        onCommit = { value -> set { it.copy(bandWidthPercent = value) } },
                    )
                }
            }
            if (params.pattern == CapsulePattern.Shimmer ||
                params.pattern == CapsulePattern.DualChase
            ) {
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
            if (params.pattern == CapsulePattern.Flip3D) {
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
            if (params.pattern == CapsulePattern.Shimmer ||
                params.pattern == CapsulePattern.InnerSweep
            ) {
                item { shape ->
                    LunoSwitchRow(
                        title = stringResource(R.string.float_ball_layer_reverse),
                        checked = params.reverse,
                        shape = shape,
                        onCheckedChange = { checked -> set { it.copy(reverse = checked) } },
                    )
                }
            }
        }
        SegmentedRows(rows)
    }
}

@Composable
private fun ScreenParamsBlock(
    layer: AlertLayer,
    expanded: Boolean,
    onToggle: () -> Unit,
    onUpdate: (AlertLayer) -> Unit,
) {
    val params = layer.screen ?: return
    fun set(transform: (hunoia.luno.config.model.ScreenParams) -> hunoia.luno.config.model.ScreenParams) =
        onUpdate(layer.copy(screen = transform(params)))
    LunoGroup(
        title = stringResource(R.string.float_ball_layer_group_appearance),
        subtitle = stringResource(R.string.float_ball_layer_group_appearance_hint),
        collapsible = true,
        expanded = expanded,
        onToggle = onToggle,
    ) {
        val rows = segmentedRows {
            item { shape ->
                LayerDropdownRow(
                    title = stringResource(R.string.float_ball_layer_pattern),
                    options = ScreenPattern.entries,
                    selected = params.pattern,
                    shape = shape,
                    optionLabel = { stringResource(it.labelRes()) },
                    onSelect = {pattern -> set { it.copy(pattern = pattern) } },
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
            if (params.pattern == ScreenPattern.ZoomPulse) {
                item { shape ->
                    LayerIntSlider(
                        label = stringResource(R.string.float_ball_layer_zoom),
                        value = params.zoomPercent,
                        range = 0..20,
                        suffix = "%",
                        shape = shape,
                        onCommit = { value -> set { it.copy(zoomPercent = value) } },
                    )
                }
            }
            if (params.pattern == ScreenPattern.Vignette) {
                item { shape ->
                    LayerFloatSlider(
                        label = stringResource(R.string.float_ball_layer_vignette_softness),
                        value = params.vignetteSoftness,
                        range = 0f..1f,
                        decimals = 2,
                        shape = shape,
                        onCommit = { value -> set { it.copy(vignetteSoftness = value) } },
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
    expanded: Boolean,
    onToggle: () -> Unit,
    onUpdate: (AlertLayer) -> Unit,
) {
    val params = layer.reveal ?: return
    fun set(transform: (hunoia.luno.config.model.RevealParams) -> hunoia.luno.config.model.RevealParams) =
        onUpdate(layer.copy(reveal = transform(params)))
    LunoGroup(
        title = stringResource(R.string.float_ball_layer_group_appearance),
        subtitle = stringResource(R.string.float_ball_layer_reveal_hint),
        collapsible = true,
        expanded = expanded,
        onToggle = onToggle,
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
