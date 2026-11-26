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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import hunoia.luno.config.model.AlertFamily
import hunoia.luno.config.model.AlertLayer
import hunoia.luno.config.model.AlertLimits
import hunoia.luno.config.model.AlertParticleShape
import hunoia.luno.config.model.AlertPreset
import hunoia.luno.config.model.AlertRenderBackend
import hunoia.luno.config.model.BandPath
import hunoia.luno.config.model.CapsuleParams
import hunoia.luno.config.model.CapsuleShape
import hunoia.luno.config.model.CoverMask
import hunoia.luno.config.model.CoverParams
import hunoia.luno.config.model.EdgeParams
import hunoia.luno.config.model.EdgeShape
import hunoia.luno.config.model.ParticleArea
import hunoia.luno.config.model.ParticleCluster
import hunoia.luno.config.model.ParticleDirection
import hunoia.luno.config.model.ParticleEmission
import hunoia.luno.config.model.ParticleHaloStyle
import hunoia.luno.config.model.ParticleLook
import hunoia.luno.config.model.ParticleMotionMode
import hunoia.luno.config.model.ParticleParams
import hunoia.luno.config.model.ParticlePath
import hunoia.luno.config.model.RevealDirection
import hunoia.luno.config.model.RevealPattern
import hunoia.luno.config.model.RingParams
import hunoia.luno.config.model.RingStyle
import hunoia.luno.config.model.WashParams
import hunoia.luno.config.model.WashShape
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.LunoActionBar
import hunoia.luno.ui.component.LunoActionBarItem
import hunoia.luno.ui.component.PressIconButton
import hunoia.luno.ui.component.menu.LunoDropdownMenu
import hunoia.luno.ui.component.menu.LunoDropdownMenuItem
import hunoia.luno.ui.component.page.LunoPage
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoInputRow
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.SegmentedRows
import hunoia.luno.ui.component.row.segmentedRows
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.facade.LocalFacades
import hunoia.luno.ui.theme.trackSurfaceColor
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
    val resources = LocalResources.current
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

    LunoPage(title = stringResource(R.string.float_ball_layer_title),
onBack = { leave() },
bottomBar = { hazeState ->
    LunoActionBar(hazeState) {
        LunoActionBarItem(
            onClick = {
                if (playable) {
                    previewLayer()
                } else {
                    toast(resources.getString(R.string.float_ball_layer_preview_disabled))
                }
            },
            icon = Icons.Default.PlayArrow,
            contentDescription = stringResource(R.string.float_ball_layer_preview),
            containerColor = if (playable) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                trackSurfaceColor()
            },
        )
    }
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
                                resources.getString(
                                    R.string.float_ball_preset_toggle_blocked,
                                    resources.getString(blocked.reasonRes()),
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
        }) {
    list() {
        lazy {

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
                                            resources.getString(
                                                R.string.float_ball_preset_toggle_blocked,
                                                resources.getString(blocked.reasonRes()),
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
                            subtitle = stringResource(R.string.float_ball_layer_name_hint),
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
                    item { shape ->
                        LayerIntSlider(
                            label = stringResource(R.string.float_ball_layer_delay),
                            value = layer.timing.delayMillis,
                            range = 0..AlertLimits.MaxDelayMillis,
                            suffix = " ms",
                            shape = shape,
                            onCommit = { value ->
                                update { it.copy(timing = it.timing.copy(delayMillis = value)) }
                            },
                        )
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
                    display = { percentDisplay(it) },
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
                    // 路径模式接管位置：自由积分的吸引 / 反弹不再生效，不摆出来。
                    if (params.motion.mode == ParticleMotionMode.Free) {
                        item { shape ->
                            LayerFloatSlider(
                                label = stringResource(R.string.float_ball_layer_motion_bounce),
                                value = params.motion.free.bounce,
                                range = 0f..1f,
                                decimals = 2,
                                shape = shape,
                                onCommit = { value ->
                                    onUpdate(
                                        layer.withMotion(
                                            params.motion.copy(
                                                free = params.motion.free.copy(bounce = value),
                                            ),
                                        ),
                                    )
                                },
                            )
                        }
                        item { shape ->
                            LayerFloatSlider(
                                label = stringResource(R.string.float_ball_layer_motion_attract),
                                value = params.motion.free.attract,
                                range = 0f..1f,
                                decimals = 2,
                                shape = shape,
                                onCommit = { value ->
                                    onUpdate(
                                        layer.withMotion(
                                            params.motion.copy(
                                                free = params.motion.free.copy(attract = value),
                                            ),
                                        ),
                                    )
                                },
                            )
                        }
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

/** 族级 + 形态级两层：族参数所有形态共用，形态参数只渲染当前那一份。 */
@Composable
private fun EdgeParamsBlock(
    layer: AlertLayer,
    onUpdate: (AlertLayer) -> Unit,
) {
    val params = layer.edge ?: return
    fun set(transform: (EdgeParams) -> EdgeParams) = onUpdate(layer.copy(edge = transform(params)))

    LunoGroup(title = stringResource(R.string.float_ball_layer_group_family)) {
        val rows = segmentedRows {
            item { shape ->
                LayerDropdownRow(
                    title = stringResource(R.string.float_ball_layer_form),
                    options = EdgeShape.entries,
                    selected = params.shape,
                    shape = shape,
                    optionLabel = { stringResource(it.labelRes()) },
                    onSelect = { value -> set { it.copy(shape = value) } },
                )
            }
            // 雷电是纯 CPU 折线，没有着色器路径：后端旋钮对它无意义，不摆出来。
            if (params.shape != EdgeShape.Lightning) {
                item { shape ->
                    LayerDropdownRow(
                        title = stringResource(R.string.float_ball_layer_backend),
                        options = AlertRenderBackend.entries,
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
        }
        SegmentedRows(rows)
    }

    LunoGroup(
        title = stringResource(R.string.float_ball_layer_group_form_params),
        subtitle = stringResource(R.string.float_ball_layer_group_form_hint),
    ) {
        val rows = segmentedRows {
            when (params.shape) {
                EdgeShape.Breathing -> {
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_depth),
                            display = { dpDisplay(it) },
                            value = params.breathing.depthDp,
                            range = 2f..64f,
                            decimals = 1,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(breathing = it.breathing.copy(depthDp = value)) }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_hardness),
                            hints = stringResource(R.string.float_ball_layer_hint_hardness_low) to
                                stringResource(R.string.float_ball_layer_hint_hardness_high),
                            value = params.breathing.hardness,
                            range = 1f..4f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(breathing = it.breathing.copy(hardness = value)) }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_breath_amount),
                            hints = stringResource(R.string.float_ball_layer_hint_breath_amount_low) to
                                stringResource(R.string.float_ball_layer_hint_breath_amount_high),
                            value = params.breathing.breathAmount,
                            range = 0f..1f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(breathing = it.breathing.copy(breathAmount = value)) }
                            },
                        )
                    }
                }
                EdgeShape.Sweep -> {
                    item { shape ->
                        LayerDropdownRow(
                            title = stringResource(R.string.float_ball_layer_path),
                            options = BandPath.entries,
                            selected = params.sweep.path,
                            shape = shape,
                            optionLabel = { stringResource(it.labelRes()) },
                            onSelect = { path ->
                                set { it.copy(sweep = it.sweep.copy(path = path)) }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_travel),
                            display = { percentDisplay(it) },
                            hints = stringResource(R.string.float_ball_layer_hint_travel_low) to
                                stringResource(R.string.float_ball_layer_hint_travel_high),
                            value = params.sweep.travel,
                            range = 0f..1f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(sweep = it.sweep.copy(travel = value)) }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_head_width),
                            display = { dpDisplay(it) },
                            value = params.sweep.headWidthDp,
                            range = 2f..48f,
                            decimals = 1,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(sweep = it.sweep.copy(headWidthDp = value)) }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_depth),
                            display = { dpDisplay(it) },
                            value = params.sweep.depthDp,
                            range = 2f..64f,
                            decimals = 1,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(sweep = it.sweep.copy(depthDp = value)) }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_hardness),
                            hints = stringResource(R.string.float_ball_layer_hint_hardness_low) to
                                stringResource(R.string.float_ball_layer_hint_hardness_high),
                            value = params.sweep.hardness,
                            range = 1f..4f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(sweep = it.sweep.copy(hardness = value)) }
                            },
                        )
                    }
                    item { shape ->
                        LayerIntSlider(
                            label = stringResource(R.string.float_ball_layer_tail_length),
                            hints = stringResource(R.string.float_ball_layer_hint_tail_length_low) to
                                stringResource(R.string.float_ball_layer_hint_tail_length_high),
                            value = params.sweep.tailPercent,
                            range = 0..60,
                            suffix = "%",
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(sweep = it.sweep.copy(tailPercent = value)) }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_tail_falloff),
                            hints = stringResource(R.string.float_ball_layer_hint_tail_falloff_low) to
                                stringResource(R.string.float_ball_layer_hint_tail_falloff_high),
                            value = params.sweep.tailFalloff,
                            range = 0.5f..4f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(sweep = it.sweep.copy(tailFalloff = value)) }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_tail_alpha),
                            display = { percentDisplay(it) },
                            value = params.sweep.tailAlpha,
                            range = 0f..1f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(sweep = it.sweep.copy(tailAlpha = value)) }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_head_boost),
                            display = { percentDisplay(it) },
                            value = params.sweep.headBoost,
                            range = 0f..1f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(sweep = it.sweep.copy(headBoost = value)) }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_noise),
                            value = params.sweep.noise,
                            range = 0f..1f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(sweep = it.sweep.copy(noise = value)) }
                            },
                        )
                    }
                    item { shape ->
                        LunoSwitchRow(
                            title = stringResource(R.string.float_ball_layer_dual),
                            checked = params.sweep.dual,
                            shape = shape,
                            onCheckedChange = { checked ->
                                set { it.copy(sweep = it.sweep.copy(dual = checked)) }
                            },
                        )
                    }
                    item { shape ->
                        LunoSwitchRow(
                            title = stringResource(R.string.float_ball_layer_reverse),
                            checked = params.sweep.reverse,
                            shape = shape,
                            onCheckedChange = { checked ->
                                set { it.copy(sweep = it.sweep.copy(reverse = checked)) }
                            },
                        )
                    }
                }
                EdgeShape.Wave -> {
                    item { shape ->
                        LayerIntSlider(
                            label = stringResource(R.string.float_ball_layer_crests),
                            value = params.wave.crests,
                            range = 2..6,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(wave = it.wave.copy(crests = value)) }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_wave_width),
                            display = { dpDisplay(it) },
                            value = params.wave.waveWidthDp,
                            range = 2f..64f,
                            decimals = 1,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(wave = it.wave.copy(waveWidthDp = value)) }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_travel),
                            display = { percentDisplay(it) },
                            hints = stringResource(R.string.float_ball_layer_hint_travel_low) to
                                stringResource(R.string.float_ball_layer_hint_travel_high),
                            value = params.wave.travel,
                            range = 0f..1f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(wave = it.wave.copy(travel = value)) }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_depth),
                            display = { dpDisplay(it) },
                            value = params.wave.depthDp,
                            range = 2f..64f,
                            decimals = 1,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(wave = it.wave.copy(depthDp = value)) }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_hardness),
                            hints = stringResource(R.string.float_ball_layer_hint_hardness_low) to
                                stringResource(R.string.float_ball_layer_hint_hardness_high),
                            value = params.wave.hardness,
                            range = 1f..4f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(wave = it.wave.copy(hardness = value)) }
                            },
                        )
                    }
                }
                EdgeShape.Lightning -> {
                    item { shape ->
                        LayerIntSlider(
                            label = stringResource(R.string.float_ball_layer_bolt_flashes),
                            value = params.lightning.flashes,
                            range = 1..8,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(lightning = it.lightning.copy(flashes = value)) }
                            },
                        )
                    }
                    item { shape ->
                        LayerIntSlider(
                            label = stringResource(R.string.float_ball_layer_bolts),
                            value = params.lightning.bolts,
                            range = 1..8,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(lightning = it.lightning.copy(bolts = value)) }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_bolt_window),
                            display = { percentDisplay(it) },
                            value = params.lightning.window,
                            range = 0.05f..0.9f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(lightning = it.lightning.copy(window = value)) }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_stroke),
                            display = { dpDisplay(it) },
                            value = params.lightning.widthDp,
                            range = 0.5f..6f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(lightning = it.lightning.copy(widthDp = value)) }
                            },
                        )
                    }
                    item { shape ->
                        LayerIntSlider(
                            label = stringResource(R.string.float_ball_layer_bolt_reach),
                            value = params.lightning.reachPercent,
                            range = 10..90,
                            suffix = "%",
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(lightning = it.lightning.copy(reachPercent = value)) }
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
private fun ParticleParamsBlock(
    layer: AlertLayer,
    onUpdate: (AlertLayer) -> Unit,
) {
    val params = layer.particle ?: return
    fun set(transform: (ParticleParams) -> ParticleParams) =
        onUpdate(layer.copy(particle = transform(params)))

    LunoGroup(
        title = stringResource(R.string.float_ball_layer_quick_shapes),
        subtitle = stringResource(R.string.float_ball_layer_quick_shapes_hint),
    ) {
        val rows = segmentedRows {
            item { shape ->
                ParticleQuickShapeRow(shape = shape) { picked, timing ->
                    onUpdate(
                        layer.copy(
                            particle = picked,
                            timing = layer.timing.copy(
                                baseMillis = timing.baseMillis ?: layer.timing.baseMillis,
                                cycles = timing.cycles ?: layer.timing.cycles,
                            ),
                        ),
                    )
                }
            }
        }
        SegmentedRows(rows)
    }
    LunoGroup(
        title = stringResource(R.string.float_ball_layer_group_emitter),
        subtitle = stringResource(R.string.float_ball_layer_group_emitter_hint),
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
                LayerDropdownRow(
                    title = stringResource(R.string.float_ball_layer_emission),
                    options = ParticleEmission.entries,
                    selected = params.emitter.emission,
                    shape = shape,
                    optionLabel = { stringResource(it.labelRes()) },
                    onSelect = { emission ->
                        set { it.copy(emitter = it.emitter.copy(emission = emission)) }
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
                LayerDropdownRow(
                    title = stringResource(R.string.float_ball_layer_direction_mode),
                    options = ParticleDirection.entries,
                    selected = params.emitter.direction,
                    shape = shape,
                    optionLabel = { stringResource(it.labelRes()) },
                    onSelect = { mode ->
                        set { it.copy(emitter = it.emitter.copy(direction = mode)) }
                    },
                )
            }
            if (params.emitter.direction == ParticleDirection.Fixed) {
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
            if (params.emitter.direction != ParticleDirection.Radial) {
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
                    display = { value -> "%.2f 屏高/秒".format(value) },
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
                    display = { percentDisplay(it) },
                    value = params.emitter.speedJitter,
                    range = 0f..1f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value ->
                        set { it.copy(emitter = it.emitter.copy(speedJitter = value)) }
                    },
                )
            }
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_delay_spread),
                    display = { percentDisplay(it) },
                    hints = stringResource(R.string.float_ball_layer_hint_delay_spread_low) to
                        stringResource(R.string.float_ball_layer_hint_delay_spread_high),
                    value = params.emitter.delaySpread,
                    range = 0f..1f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value ->
                        set { it.copy(emitter = it.emitter.copy(delaySpread = value)) }
                    },
                )
            }
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_life_share),
                    display = { percentDisplay(it) },
                    hints = stringResource(R.string.float_ball_layer_hint_life_share_low) to
                        stringResource(R.string.float_ball_layer_hint_life_share_high),
                    value = params.emitter.lifeShare,
                    range = 0.1f..1f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value ->
                        set { it.copy(emitter = it.emitter.copy(lifeShare = value)) }
                    },
                )
            }
        }
        SegmentedRows(rows)
    }
    // 锚点簇：关闭时整组参数不存在（不是「调了没用」的死值）。
    LunoGroup(
        title = stringResource(R.string.float_ball_layer_cluster),
        subtitle = stringResource(R.string.float_ball_layer_cluster_hint),
    ) {
        val rows = segmentedRows {
            item { shape ->
                LunoSwitchRow(
                    title = stringResource(R.string.float_ball_layer_cluster_on),
                    checked = params.cluster != null,
                    shape = shape,
                    onCheckedChange = { checked ->
                        set {
                            it.copy(
                                cluster = if (checked) ParticleCluster() else null,
                            )
                        }
                    },
                )
            }
            val cluster = params.cluster
            if (cluster != null) {
                item { shape ->
                    LayerIntSlider(
                        label = stringResource(R.string.float_ball_layer_anchor_count),
                        value = cluster.anchors,
                        range = 1..8,
                        shape = shape,
                        onCommit = { value ->
                            set { it.copy(cluster = it.cluster?.copy(anchors = value)) }
                        },
                    )
                }
                item { shape ->
                    LayerFloatSlider(
                        label = stringResource(R.string.float_ball_layer_launch_height),
                        display = { value -> "${(value * 100).toInt()}% 屏高" },
                        value = cluster.launchHeight,
                        range = 0f..0.9f,
                        decimals = 2,
                        shape = shape,
                        onCommit = { value ->
                            set { it.copy(cluster = it.cluster?.copy(launchHeight = value)) }
                        },
                    )
                }
                if (cluster.launchHeight > 0.001f) {
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_launch_share),
                            display = { percentDisplay(it) },
                            value = cluster.launchShare,
                            range = 0.1f..0.9f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(cluster = it.cluster?.copy(launchShare = value)) }
                            },
                        )
                    }
                }
            }
        }
        SegmentedRows(rows)
    }
    LunoGroup(
        title = stringResource(R.string.float_ball_layer_group_motion),
        subtitle = stringResource(R.string.float_ball_layer_group_motion_hint),
    ) {
        val rows = segmentedRows {
            item { shape ->
                LayerDropdownRow(
                    title = stringResource(R.string.float_ball_layer_motion_path),
                    options = ParticleMotionMode.entries,
                    selected = params.motion.mode,
                    shape = shape,
                    optionLabel = { mode -> stringResource(mode.labelRes()) },
                    onSelect = { mode ->
                        set { it.copy(motion = it.motion.copy(mode = mode)) }
                    },
                )
            }
            // 自转 / 翻面两种模式都生效，始终可见。
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_motion_spin),
                    value = params.motion.spin,
                    range = 0f..2f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value ->
                        set { it.copy(motion = it.motion.copy(spin = value)) }
                    },
                )
            }
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_motion_tilt),
                    value = params.motion.tilt,
                    range = 0f..1f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value ->
                        set { it.copy(motion = it.motion.copy(tilt = value)) }
                    },
                )
            }
            when (params.motion.mode) {
                ParticleMotionMode.Free -> {
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_motion_gravity),
                            value = params.motion.free.gravity,
                            range = -3f..3f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set {
                                    it.copy(
                                        motion = it.motion.copy(
                                            free = it.motion.free.copy(gravity = value),
                                        ),
                                    )
                                }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_motion_drag),
                            hints = stringResource(R.string.float_ball_layer_hint_motion_drag_low) to
                                stringResource(R.string.float_ball_layer_hint_motion_drag_high),
                            value = params.motion.free.drag,
                            range = 0f..1f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set {
                                    it.copy(
                                        motion = it.motion.copy(
                                            free = it.motion.free.copy(drag = value),
                                        ),
                                    )
                                }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_motion_wind),
                            value = params.motion.free.wind,
                            range = -1f..1f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set {
                                    it.copy(
                                        motion = it.motion.copy(
                                            free = it.motion.free.copy(wind = value),
                                        ),
                                    )
                                }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_motion_turbulence),
                            value = params.motion.free.turbulence,
                            range = 0f..1f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set {
                                    it.copy(
                                        motion = it.motion.copy(
                                            free = it.motion.free.copy(turbulence = value),
                                        ),
                                    )
                                }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_motion_brownian),
                            value = params.motion.free.brownian,
                            range = 0f..1f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set {
                                    it.copy(
                                        motion = it.motion.copy(
                                            free = it.motion.free.copy(brownian = value),
                                        ),
                                    )
                                }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_motion_orbit),
                            value = params.motion.free.orbit,
                            range = 0f..1f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set {
                                    it.copy(
                                        motion = it.motion.copy(
                                            free = it.motion.free.copy(orbit = value),
                                        ),
                                    )
                                }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_motion_spiral),
                            value = params.motion.free.spiral,
                            range = 0f..1f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set {
                                    it.copy(
                                        motion = it.motion.copy(
                                            free = it.motion.free.copy(spiral = value),
                                        ),
                                    )
                                }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_motion_sway),
                            value = params.motion.free.sway,
                            range = 0f..1f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set {
                                    it.copy(
                                        motion = it.motion.copy(
                                            free = it.motion.free.copy(sway = value),
                                        ),
                                    )
                                }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_motion_sway_cycles),
                            display = { "%.1f 次".format(it) },
                            value = params.motion.free.swayCycles,
                            range = 0.5f..4f,
                            decimals = 1,
                            shape = shape,
                            onCommit = { value ->
                                set {
                                    it.copy(
                                        motion = it.motion.copy(
                                            free = it.motion.free.copy(swayCycles = value),
                                        ),
                                    )
                                }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_motion_attract),
                            value = params.motion.free.attract,
                            range = 0f..1f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set {
                                    it.copy(
                                        motion = it.motion.copy(
                                            free = it.motion.free.copy(attract = value),
                                        ),
                                    )
                                }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_motion_bounce),
                            value = params.motion.free.bounce,
                            range = 0f..1f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set {
                                    it.copy(
                                        motion = it.motion.copy(
                                            free = it.motion.free.copy(bounce = value),
                                        ),
                                    )
                                }
                            },
                        )
                    }
                }
                ParticleMotionMode.Path -> {
                    item { shape ->
                        LayerDropdownRow(
                            title = stringResource(R.string.float_ball_layer_path_form),
                            options = ParticlePath.entries,
                            selected = params.motion.path.shape,
                            shape = shape,
                            optionLabel = { stringResource(it.labelRes()) },
                            onSelect = { path ->
                                set {
                                    it.copy(
                                        motion = it.motion.copy(
                                            path = it.motion.path.copy(shape = path),
                                        ),
                                    )
                                }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_path_travel),
                            value = params.motion.path.travelScale,
                            range = 0.2f..2.5f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set {
                                    it.copy(
                                        motion = it.motion.copy(
                                            path = it.motion.path.copy(travelScale = value),
                                        ),
                                    )
                                }
                            },
                        )
                    }
                }
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
                    display = { percentDisplay(it) },
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
                    display = { percentDisplay(it) },
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
                    display = { percentDisplay(it) },
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
                    display = { percentDisplay(it) },
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
                    hints = stringResource(R.string.float_ball_layer_hint_fade_exp_low) to
                        stringResource(R.string.float_ball_layer_hint_fade_exp_high),
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
                        display = { percentDisplay(it) },
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
                        display = { percentDisplay(it) },
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
                        display = { "%.1f 次".format(it) },
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
    fun set(transform: (RingParams) -> RingParams) = onUpdate(layer.copy(ring = transform(params)))

    LunoGroup(title = stringResource(R.string.float_ball_layer_group_family)) {
        val rows = segmentedRows {
            item { shape ->
                LayerDropdownRow(
                    title = stringResource(R.string.float_ball_layer_form),
                    options = RingStyle.entries,
                    selected = params.style,
                    shape = shape,
                    optionLabel = { stringResource(it.labelRes()) },
                    onSelect = { style -> set { it.copy(style = style) } },
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
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_stroke),
                    display = { dpDisplay(it) },
                    value = params.strokeWidthDp,
                    range = 1f..12f,
                    decimals = 1,
                    shape = shape,
                    onCommit = { value -> set { it.copy(strokeWidthDp = value) } },
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
                    label = stringResource(R.string.float_ball_layer_travel_percent),
                    hints = stringResource(R.string.float_ball_layer_hint_travel_percent_low) to
                        stringResource(R.string.float_ball_layer_hint_travel_percent_high),
                    value = params.travelPercent,
                    range = 0..300,
                    suffix = "%",
                    shape = shape,
                    onCommit = { value -> set { it.copy(travelPercent = value) } },
                )
            }
        }
        SegmentedRows(rows)
    }
    // 三层描边没有额外参数：形态参数组只在有参数时出现，不留空组。
    if (params.style != RingStyle.ThreeStroke) {
        LunoGroup(
            title = stringResource(R.string.float_ball_layer_group_form_params),
            subtitle = stringResource(R.string.float_ball_layer_group_form_hint),
        ) {
            val rows = segmentedRows {
                item { shape ->
                    val halo = if (params.style == RingStyle.Halo) {
                        params.halo.haloScale
                    } else {
                        params.nested.haloScale
                    }
                    LayerFloatSlider(
                        label = stringResource(R.string.float_ball_layer_halo_scale),
                        value = halo,
                        range = 0f..3f,
                        decimals = 2,
                        shape = shape,
                        onCommit = { value ->
                            set {
                                if (it.style == RingStyle.Halo) {
                                    it.copy(halo = it.halo.copy(haloScale = value))
                                } else {
                                    it.copy(nested = it.nested.copy(haloScale = value))
                                }
                            }
                        },
                    )
                }
            }
            SegmentedRows(rows)
        }
    }
}

@Composable
private fun CapsuleParamsBlock(
    layer: AlertLayer,
    onUpdate: (AlertLayer) -> Unit,
) {
    val params = layer.capsule ?: return
    fun set(transform: (CapsuleParams) -> CapsuleParams) =
        onUpdate(layer.copy(capsule = transform(params)))

    LunoGroup(title = stringResource(R.string.float_ball_layer_group_family)) {
        val rows = segmentedRows {
            item { shape ->
                LayerDropdownRow(
                    title = stringResource(R.string.float_ball_layer_form),
                    options = CapsuleShape.entries,
                    selected = params.shape,
                    shape = shape,
                    optionLabel = { stringResource(it.labelRes()) },
                    onSelect = { value -> set { it.copy(shape = value) } },
                )
            }
        }
        SegmentedRows(rows)
    }
    LunoGroup(
        title = stringResource(R.string.float_ball_layer_group_form_params),
        subtitle = stringResource(R.string.float_ball_layer_group_form_hint),
    ) {
        val rows = segmentedRows {
            when (params.shape) {
                CapsuleShape.Sweep -> {
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_stroke),
                            display = { dpDisplay(it) },
                            value = params.sweep.strokeWidthDp,
                            range = 0.5f..6f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(sweep = it.sweep.copy(strokeWidthDp = value)) }
                            },
                        )
                    }
                    item { shape ->
                        LunoSwitchRow(
                            title = stringResource(R.string.float_ball_layer_capsule_fill),
                            checked = params.sweep.fill,
                            shape = shape,
                            onCheckedChange = { checked ->
                                set { it.copy(sweep = it.sweep.copy(fill = checked)) }
                            },
                        )
                    }
                    item { shape ->
                        LayerIntSlider(
                            label = stringResource(R.string.float_ball_layer_heads),
                            value = params.sweep.heads,
                            range = 1..2,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(sweep = it.sweep.copy(heads = value)) }
                            },
                        )
                    }
                    item { shape ->
                        LayerIntSlider(
                            label = stringResource(R.string.float_ball_layer_band),
                            value = params.sweep.bandPercent,
                            range = 5..90,
                            suffix = "%",
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(sweep = it.sweep.copy(bandPercent = value)) }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_travel),
                            display = { percentDisplay(it) },
                            hints = stringResource(R.string.float_ball_layer_hint_travel_low) to
                                stringResource(R.string.float_ball_layer_hint_travel_high),
                            value = params.sweep.travel,
                            range = 0f..1f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(sweep = it.sweep.copy(travel = value)) }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_trail_scale),
                            display = { "%.1f×".format(it) },
                            value = params.sweep.trailScale,
                            range = 0f..4f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(sweep = it.sweep.copy(trailScale = value)) }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_trail_alpha),
                            display = { percentDisplay(it) },
                            value = params.sweep.trailAlpha,
                            range = 0f..1f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(sweep = it.sweep.copy(trailAlpha = value)) }
                            },
                        )
                    }
                    item { shape ->
                        LunoSwitchRow(
                            title = stringResource(R.string.float_ball_layer_reverse),
                            checked = params.sweep.reverse,
                            shape = shape,
                            onCheckedChange = { checked ->
                                set { it.copy(sweep = it.sweep.copy(reverse = checked)) }
                            },
                        )
                    }
                }
                CapsuleShape.Pulse -> {
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_stroke),
                            display = { dpDisplay(it) },
                            value = params.pulse.strokeWidthDp,
                            range = 0.5f..6f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(pulse = it.pulse.copy(strokeWidthDp = value)) }
                            },
                        )
                    }
                    item { shape ->
                        LayerIntSlider(
                            label = stringResource(R.string.float_ball_layer_glow_layers),
                            value = params.pulse.glowLayers,
                            range = 0..2,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(pulse = it.pulse.copy(glowLayers = value)) }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_glow_scale),
                            value = params.pulse.glowScale,
                            range = 0f..2f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(pulse = it.pulse.copy(glowScale = value)) }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_breath_scale),
                            value = params.pulse.breatheScale,
                            range = 0f..1f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(pulse = it.pulse.copy(breatheScale = value)) }
                            },
                        )
                    }
                }
                CapsuleShape.Flip -> {
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_stroke),
                            display = { dpDisplay(it) },
                            value = params.flip.strokeWidthDp,
                            range = 0.5f..6f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(flip = it.flip.copy(strokeWidthDp = value)) }
                            },
                        )
                    }
                    item { shape ->
                        LayerIntSlider(
                            label = stringResource(R.string.float_ball_layer_flip_degrees),
                            value = params.flip.degrees,
                            range = 0..360,
                            suffix = "°",
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(flip = it.flip.copy(degrees = value)) }
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
private fun WashParamsBlock(
    layer: AlertLayer,
    onUpdate: (AlertLayer) -> Unit,
) {
    val params = layer.wash ?: return
    fun set(transform: (WashParams) -> WashParams) = onUpdate(layer.copy(wash = transform(params)))

    LunoGroup(title = stringResource(R.string.float_ball_layer_group_family)) {
        val rows = segmentedRows {
            item { shape ->
                LayerDropdownRow(
                    title = stringResource(R.string.float_ball_layer_form),
                    options = WashShape.entries,
                    selected = params.shape,
                    shape = shape,
                    optionLabel = { stringResource(it.labelRes()) },
                    onSelect = { value -> set { it.copy(shape = value) } },
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
            item { shape ->
                LunoSwitchRow(
                    title = stringResource(R.string.float_ball_layer_breath),
                    checked = params.breath,
                    shape = shape,
                    onCheckedChange = { checked -> set { it.copy(breath = checked) } },
                )
            }
        }
        SegmentedRows(rows)
    }
    if (params.shape != WashShape.Flat) {
        LunoGroup(
            title = stringResource(R.string.float_ball_layer_group_form_params),
            subtitle = stringResource(R.string.float_ball_layer_group_form_hint),
        ) {
            val rows = segmentedRows {
                if (params.shape == WashShape.Radial) {
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_softness),
                            value = params.radial.softness,
                            range = 0f..1f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(radial = it.radial.copy(softness = value)) }
                            },
                        )
                    }
                } else {
                    item { shape ->
                        LayerIntSlider(
                            label = stringResource(R.string.float_ball_layer_zoom),
                            value = params.ring.zoomPercent,
                            range = 0..40,
                            suffix = "%",
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(ring = it.ring.copy(zoomPercent = value)) }
                            },
                        )
                    }
                }
            }
            SegmentedRows(rows)
        }
    }
}

@Composable
private fun CoverParamsBlock(
    layer: AlertLayer,
    onUpdate: (AlertLayer) -> Unit,
) {
    val params = layer.cover ?: return
    fun set(transform: (CoverParams) -> CoverParams) = onUpdate(layer.copy(cover = transform(params)))

    LunoGroup(title = stringResource(R.string.float_ball_layer_group_family)) {
        val rows = segmentedRows {
            item { shape ->
                LayerDropdownRow(
                    title = stringResource(R.string.float_ball_layer_form),
                    options = CoverMask.entries,
                    selected = params.mask,
                    shape = shape,
                    optionLabel = { stringResource(it.labelRes()) },
                    onSelect = { value -> set { it.copy(mask = value) } },
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
            item { shape ->
                LayerFloatSlider(
                    label = stringResource(R.string.float_ball_layer_curve),
                    hints = stringResource(R.string.float_ball_layer_hint_curve_low) to
                        stringResource(R.string.float_ball_layer_hint_curve_high),
                    value = params.curve,
                    range = 1f..4f,
                    decimals = 2,
                    shape = shape,
                    onCommit = { value -> set { it.copy(curve = value) } },
                )
            }
        }
        SegmentedRows(rows)
    }
    LunoGroup(
        title = stringResource(R.string.float_ball_layer_group_form_params),
        subtitle = stringResource(R.string.float_ball_layer_group_form_hint),
    ) {
        val rows = segmentedRows {
            when (params.mask) {
                CoverMask.Circle -> item { shape ->
                    LayerFloatSlider(
                        label = stringResource(R.string.float_ball_layer_softness),
                        value = params.circle.softness,
                        range = 0f..1f,
                        decimals = 2,
                        shape = shape,
                        onCommit = { value ->
                            set { it.copy(circle = it.circle.copy(softness = value)) }
                        },
                    )
                }
                CoverMask.Iris -> item { shape ->
                    LayerFloatSlider(
                        label = stringResource(R.string.float_ball_layer_rim),
                        value = params.iris.rimDp,
                        range = 0f..6f,
                        decimals = 1,
                        shape = shape,
                        onCommit = { value ->
                            set { it.copy(iris = it.iris.copy(rimDp = value)) }
                        },
                    )
                }
                CoverMask.Slit -> item { shape ->
                    LayerFloatSlider(
                        label = stringResource(R.string.float_ball_layer_slit),
                        value = params.slit.slitDp,
                        range = 2f..24f,
                        decimals = 1,
                        shape = shape,
                        onCommit = { value ->
                            set { it.copy(slit = it.slit.copy(slitDp = value)) }
                        },
                    )
                }
                CoverMask.Bands -> item { shape ->
                    LayerIntSlider(
                        label = stringResource(R.string.float_ball_layer_bands),
                        value = params.bands.count,
                        range = 3..15,
                        shape = shape,
                        onCommit = { value ->
                            set { it.copy(bands = it.bands.copy(count = value)) }
                        },
                    )
                }
                CoverMask.Shards -> {
                    item { shape ->
                        LayerIntSlider(
                            label = stringResource(R.string.float_ball_layer_shards),
                            value = params.shards.count,
                            range = 6..30,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(shards = it.shards.copy(count = value)) }
                            },
                        )
                    }
                    item { shape ->
                        LayerFloatSlider(
                            label = stringResource(R.string.float_ball_layer_softness),
                            value = params.shards.softness,
                            range = 0f..1f,
                            decimals = 2,
                            shape = shape,
                            onCommit = { value ->
                                set { it.copy(shards = it.shards.copy(softness = value)) }
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
                    hints = stringResource(R.string.float_ball_layer_hint_stagger_low) to
                        stringResource(R.string.float_ball_layer_hint_stagger_high),
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
