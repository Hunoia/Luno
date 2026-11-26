package hunoia.luno.gesture.settings.global

import androidx.annotation.Keep
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.config.model.GlobalGestureKind
import hunoia.luno.config.model.GlobalGestureTrigger
import hunoia.luno.config.model.GlobalGestureZone
import hunoia.luno.config.model.ZoneBounds
import hunoia.luno.config.model.allowsFingers
import hunoia.luno.config.model.isArmedDrag
import hunoia.luno.config.model.isDirectional
import hunoia.luno.config.defaults.SettingsUiDefaults.MaxDoubleTapTriggerDelayMs
import hunoia.luno.config.defaults.SettingsUiDefaults.MinDoubleTapTriggerDelayMs
import hunoia.luno.gesture.R
import hunoia.luno.gesture.settings.GestureActionTarget
import hunoia.luno.gesture.settings.subgesture.GestureButtonAngleContent
import hunoia.luno.gesture.settings.style.SideGestureSettings
import hunoia.luno.gesture.touchsource.triggerType
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.ui.component.DirectionLabels
import hunoia.luno.ui.component.PressTextButton
import hunoia.luno.ui.component.actionTextCompose
import hunoia.luno.ui.component.directionLabel
import hunoia.luno.ui.component.feedback.LunoIconButton
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.page.LunoPage
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSelectableRow
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.row.group
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.component.sheet.OptimizedScrollState
import hunoia.luno.ui.theme.DisabledAlpha
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.MinInteractiveSize
import hunoia.luno.ui.theme.PageGutter
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 识别区编辑页路由参数。 */
@Keep
@kotlinx.serialization.Serializable
data class GlobalGestureZoneEditor(
    val zoneId: String,
)

/** 触发设置页路由参数。 */
@Keep
@kotlinx.serialization.Serializable
data class GlobalGestureTriggerEditor(
    val zoneId: String,
    val triggerId: String,
)

/**
 * 识别区编辑：边界（屏幕比例）+ 触发列表。
 *
 * 触发的形态与手指数在创建时确定，之后只改动作与阈值；要换形态就删掉重建，
 * 避免改形态后旧动作落在不存在的扇区里成为静默死配置。
 *
 * 触发列表只做概览（启用开关 + 进设置页），具体设置各触发独立一页
 * [GlobalGestureTriggerEditorScreen]，不再用展开列（设置项多，展开列拥挤）。
 */
@Composable
fun GlobalGestureZoneEditorScreen(
    zoneId: String,
    onBack: () -> Unit,
    onNavToTriggerEditor: (String) -> Unit = {},
) {
    val settings by GestureConfig.globalGesture.collectAsStateWithLifecycle(
        initialValue = GestureConfig.latestGlobalGesture(),
    )
    val scope = rememberCoroutineScope()
    val zoneIndex = settings.zones.indexOfFirst { it.id == zoneId }
    val zone = settings.zones.getOrNull(zoneIndex)
    var showAddTrigger by remember { mutableStateOf(false) }
    var showDeleteZone by remember { mutableStateOf(false) }
    var adjustingBounds by remember { mutableStateOf(false) }

    fun updateZone(transform: (GlobalGestureZone) -> GlobalGestureZone) {
        scope.launch {
            GestureConfig.updateGlobalGesture { current ->
                current.copy(
                    zones = current.zones.map { if (it.id == zoneId) transform(it) else it },
                )
            }
        }
    }

    fun updateTrigger(triggerId: String, transform: (GlobalGestureTrigger) -> GlobalGestureTrigger) {
        updateZone { current ->
            current.copy(
                triggers = current.triggers.map { if (it.id == triggerId) transform(it) else it },
            )
        }
    }

    if (zone == null) {
        // 删除后配置流先到、导航还没回退：不渲染空壳。
        return
    }

    LunoPage(title = globalZoneLabel(zone, zoneIndex),
onBack = onBack,
actions = {
            LunoIconButton(
                icon = Icons.Default.Delete,
                contentDescription = stringResource(hunoia.luno.design.R.string.delete),
                onClick = { showDeleteZone = true },
            )
        },
floatingContent = {
            if (adjustingBounds) {
                ZoneBoundsDragOverlay(
                    bounds = zone.bounds,
                    onCommit = { bounds ->
                        updateZone { it.copy(bounds = bounds) }
                        adjustingBounds = false
                    },
                    onCancel = { adjustingBounds = false },
                )
            } else {
                ZoneBoundsOverlay(zones = settings.zones, activeId = zone.id)
            }
        },
sheets = {
            if (showAddTrigger) {
                AddTriggerSheet(
                    onDismissRequest = { showAddTrigger = false },
                    onCreate = { kind ->
                        updateZone { current ->
                            current.copy(
                                triggers = current.triggers + GlobalGestureTrigger.create(kind),
                            )
                        }
                        showAddTrigger = false
                    },
                )
            }
        }) {
    list() {
        lazy {

        item(key = "zone_bounds") {
            ZoneBoundsGroup(
                bounds = zone.bounds,
                adjusting = adjustingBounds,
                onToggleAdjust = { adjustingBounds = !adjustingBounds },
                onChange = { bounds -> updateZone { it.copy(bounds = bounds) } },
            )
        }
        item(key = "triggers") {
            LunoGroup(
                title = stringResource(R.string.gesture_global_trigger),
                titleTrailing = {
                    LunoIconButton(
                        icon = Icons.Default.Add,
                        contentDescription = stringResource(R.string.gesture_global_trigger_add),
                        onClick = { showAddTrigger = true },
                    )
                },
            ) {
                if (zone.triggers.isEmpty()) {
                    LunoSettingsRow(
                        title = stringResource(R.string.gesture_global_trigger_empty),
                        shape = segmentedShape(0, 1),
                    )
                } else {
                    zone.triggers.forEachIndexed { index, trigger ->
                        TriggerListRow(
                            trigger = trigger,
                            index = index,
                            count = zone.triggers.size,
                            onEnabledChange = { enabled -> updateTrigger(trigger.id) { it.copy(enabled = enabled) } },
                            onClick = { onNavToTriggerEditor(trigger.id) },
                        )
                    }
                }
            }
        }

        }
    }
}

    if (showDeleteZone) {
        ConfirmDialog(
            title = stringResource(R.string.gesture_global_zone_delete),
            text = stringResource(R.string.gesture_global_zone_delete_desc),
            destructive = true,
            onDismissRequest = { showDeleteZone = false },
            onConfirm = {
                showDeleteZone = false
                scope.launch {
                    GestureConfig.updateGlobalGesture { current ->
                        current.copy(zones = current.zones.filterNot { it.id == zone.id })
                    }
                    onBack()
                }
            },
        )
    }
}

@Composable
private fun TriggerListRow(
    trigger: GlobalGestureTrigger,
    index: Int,
    count: Int,
    onEnabledChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    LunoSwitchRow(
        title = triggerTitle(trigger),
        subtitle = triggerSummary(trigger),
        icon = kindIcon(trigger.kind),
        checked = trigger.enabled,
        onCheckedChange = onEnabledChange,
        onClick = onClick,
        shape = segmentedShape(index, count),
    )
}

@Composable
private fun triggerTitle(trigger: GlobalGestureTrigger): String = buildString {
    append(kindLabel(trigger.kind))
    append(" · ")
    append(stringResource(R.string.gesture_global_trigger_fingers_value, trigger.fingers))
}

/** 列表行动作摘要：无方向形态取单动作名，方向形态拼接已配置扇区的动作名。 */
@Composable
private fun triggerSummary(trigger: GlobalGestureTrigger): String {
    val directional = trigger.kind.isDirectional ||
        (trigger.kind.isArmedDrag && !trigger.fireOnHold)
    if (!directional) {
        return trigger.action.actionTextCompose()
    }
    return DirectionLabels.clockwiseSlots(trigger.angle.boundaries)
        .filterNot { it in trigger.angle.zeroWidthSlots() }
        .mapNotNull { slot -> trigger.directionActions.actionBy(slot) }
        .map { it.actionTextCompose() }
        .joinToString(" · ")
}

/**
 * 触发设置页：该触发一页装下全部设置（不再用展开列）。
 *
 * 「启用触发」在识别区列表行，不在此页；形态创建后固定，只改动作 / 手指数 / 阈值。
 */
@Composable
fun GlobalGestureTriggerEditorScreen(
    zoneId: String,
    triggerId: String,
    onBack: () -> Unit,
    onNavToActionSelect: (GestureActionTarget) -> Unit = {},
) {
    val settings by GestureConfig.globalGesture.collectAsStateWithLifecycle(
        initialValue = GestureConfig.latestGlobalGesture(),
    )
    val scope = rememberCoroutineScope()
    val zone = settings.zones.firstOrNull { it.id == zoneId }
    val trigger = zone?.triggers?.firstOrNull { it.id == triggerId }
    var showDelete by remember { mutableStateOf(false) }
    var showAngleEditor by remember { mutableStateOf(false) }
    var showFingers by remember { mutableStateOf(false) }

    fun updateTrigger(transform: (GlobalGestureTrigger) -> GlobalGestureTrigger) {
        scope.launch {
            GestureConfig.updateGlobalGesture { current ->
                current.copy(
                    zones = current.zones.map { z ->
                        if (z.id == zoneId) {
                            z.copy(
                                triggers = z.triggers.map { if (it.id == triggerId) transform(it) else it },
                            )
                        } else {
                            z
                        }
                    },
                )
            }
        }
    }

    if (zone == null || trigger == null) {
        // 删除后配置流先到、导航还没回退：不渲染空壳。
        return
    }

    LunoPage(title = triggerTitle(trigger),
onBack = onBack,
actions = {
            LunoIconButton(
                icon = Icons.Default.Delete,
                contentDescription = stringResource(R.string.gesture_global_trigger_delete),
                onClick = { showDelete = true },
            )
        },
sheets = {
            if (showAngleEditor) {
                LunoSheet(onDismissRequest = { showAngleEditor = false }, showDragHandle = true) {
                    GestureButtonAngleContent(
                        angle = trigger.angle,
                        onDismissRequest = { showAngleEditor = false },
                        onConfirm = { angle ->
                            updateTrigger { it.copy(angle = angle) }
                            showAngleEditor = false
                        },
                    )
                }
            }
            if (showFingers) {
                TriggerFingersSheet(
                    trigger = trigger,
                    onDismissRequest = { showFingers = false },
                    onSelect = { fingers ->
                        updateTrigger { it.copy(fingers = fingers) }
                        showFingers = false
                    },
                )
            }
        }) {
    list() {
        lazy {

        item(key = "behavior") {
            LunoGroup(title = stringResource(R.string.gesture_global_trigger_behavior)) {
                val count = behaviorRowCount(trigger)
                var row = 0
                LunoSwitchRow(
                    title = stringResource(R.string.gesture_global_trigger_takeover),
                    subtitle = stringResource(R.string.gesture_global_trigger_takeover_hint),
                    checked = trigger.consumeTouch,
                    onCheckedChange = { enabled -> updateTrigger { it.copy(consumeTouch = enabled) } },
                    shape = segmentedShape(row, count),
                )
                row++
                if (trigger.kind.isArmedDrag) {
                    LunoSwitchRow(
                        title = stringResource(R.string.gesture_global_trigger_fire_on_hold),
                        subtitle = stringResource(R.string.gesture_global_trigger_fire_on_hold_hint),
                        checked = trigger.fireOnHold,
                        onCheckedChange = { enabled ->
                            updateTrigger { current ->
                                if (enabled) {
                                    current.copy(
                                        fireOnHold = true,
                                        action = current.action ?: current.directionActions.actionBy(0),
                                    )
                                } else {
                                    current.copy(
                                        fireOnHold = false,
                                        directionActions = current.directionActions.withAction(
                                            0,
                                            current.directionActions.actionBy(0) ?: current.action,
                                        ),
                                    )
                                }
                            }
                        },
                        shape = segmentedShape(row, count),
                    )
                    row++
                }
                LunoSettingsRow(
                    title = stringResource(R.string.gesture_global_trigger_fingers),
                    subtitle = stringResource(R.string.gesture_global_trigger_fingers_value, trigger.fingers),
                    onClick = { showFingers = true },
                    shape = segmentedShape(row, count),
                )
                row++
                if (trigger.kind.allowsAngle() &&
                    !(trigger.kind.isArmedDrag && trigger.fireOnHold)
                ) {
                    LunoSettingsRow(
                        title = stringResource(R.string.gesture_angles),
                        subtitle = if (trigger.angle.count <= 1) {
                            stringResource(R.string.gesture_global_trigger_any_direction)
                        } else {
                            "${trigger.angle.count}"
                        },
                        onClick = { showAngleEditor = true },
                        shape = segmentedShape(row, count),
                    )
                }
            }
        }
        item(key = "actions") {
            LunoGroup {
                TriggerActionRows(
                    trigger = trigger,
                    onClick = { slot, label ->
                        onNavToActionSelect(
                            GestureActionTarget(
                                globalGestureTriggerId = trigger.id,
                                slot = slot,
                                directionLabel = label,
                                triggerType = trigger.kind.triggerType(),
                            ),
                        )
                    },
                )
            }
        }
        item(key = "thresholds") {
            LunoGroup(title = stringResource(R.string.gesture_global_trigger_threshold)) {
                if (trigger.kind.isArmedDrag) {
                    SliderRow(
                        title = stringResource(R.string.gesture_global_trigger_long_press_delay),
                        value = trigger.longPressDelayMs.toFloat(),
                        range = 100f..1_500f,
                        display = { "${it.roundToInt()} ms" },
                        onCommit = { v -> updateTrigger { it.copy(longPressDelayMs = v.roundToInt()) } },
                        shape = segmentedShape(0, 4),
                    )
                    SliderRow(
                        title = stringResource(R.string.gesture_global_trigger_drag_timeout),
                        value = trigger.dragStartTimeoutMs.toFloat(),
                        range = 0f..5_000f,
                        display = {
                            if (it.roundToInt() == 0) {
                                stringResource(R.string.gesture_global_trigger_drag_timeout_off)
                            } else {
                                "${it.roundToInt()} ms"
                            }
                        },
                        onCommit = { v -> updateTrigger { it.copy(dragStartTimeoutMs = v.roundToInt()) } },
                        shape = segmentedShape(1, 4),
                    )
                    SliderRow(
                        title = stringResource(R.string.gesture_global_trigger_slop),
                        value = trigger.slopDp,
                        range = 2f..40f,
                        display = { "${it.roundToInt()} dp" },
                        onCommit = { v -> updateTrigger { it.copy(slopDp = v.roundToInt().toFloat()) } },
                        shape = segmentedShape(2, 4),
                    )
                    SliderRow(
                        title = stringResource(R.string.gesture_global_trigger_cooldown),
                        value = trigger.triggerCooldownMs.toFloat(),
                        range = 0f..3_000f,
                        display = { "${it.roundToInt()} ms" },
                        onCommit = { v -> updateTrigger { it.copy(triggerCooldownMs = v.roundToInt()) } },
                        shape = segmentedShape(3, 4),
                    )
                } else if (trigger.kind.isDirectional) {
                    // 每种形态只用属于自己的那一档：短类用近档、长类用远档，不做两档互钳。
                    // 运行时对长类用 effectiveLongSlideTriggerDistanceDp（已兜底 long ≥ short）。
                    val isLong = trigger.kind == GlobalGestureKind.LongSlide ||
                        trigger.kind == GlobalGestureKind.LongSlideHold
                    val isHold = trigger.kind == GlobalGestureKind.SlideHold ||
                        trigger.kind == GlobalGestureKind.LongSlideHold
                    val count = 2 + (if (isHold) 1 else 0)
                    var row = 0
                    SliderRow(
                        title = stringResource(R.string.gesture_global_trigger_distance),
                        value = if (isLong) {
                            trigger.longSlideTriggerDistanceDp
                        } else {
                            trigger.slideTriggerDistanceDp
                        },
                        range = if (isLong) 48f..240f else 16f..80f,
                        display = { "${it.roundToInt()} dp" },
                        onCommit = { v ->
                            updateTrigger {
                                if (isLong) it.copy(longSlideTriggerDistanceDp = v.roundToInt().toFloat())
                                else it.copy(slideTriggerDistanceDp = v.roundToInt().toFloat())
                            }
                        },
                        shape = segmentedShape(row++, count),
                    )
                    if (isHold) {
                        SliderRow(
                            title = stringResource(R.string.gesture_global_trigger_hold_delay),
                            value = trigger.holdTriggerDelayMs.toFloat(),
                            range = 0f..1_000f,
                            display = { "${it.roundToInt()} ms" },
                            onCommit = { v -> updateTrigger { it.copy(holdTriggerDelayMs = v.roundToInt()) } },
                            shape = segmentedShape(row++, count),
                        )
                    }
                    SliderRow(
                        title = stringResource(R.string.gesture_global_trigger_cooldown),
                        value = trigger.triggerCooldownMs.toFloat(),
                        range = 0f..3_000f,
                        display = { "${it.roundToInt()} ms" },
                        onCommit = { v -> updateTrigger { it.copy(triggerCooldownMs = v.roundToInt()) } },
                        shape = segmentedShape(row, count),
                    )
                } else if (trigger.kind == GlobalGestureKind.DoubleTap) {
                    SliderRow(
                        title = stringResource(R.string.trigger_double_tap_delay),
                        value = trigger.doubleTapDelayMs.toFloat(),
                        range = MinDoubleTapTriggerDelayMs.toFloat()..MaxDoubleTapTriggerDelayMs.toFloat(),
                        display = { "${it.roundToInt()} ms" },
                        onCommit = { v -> updateTrigger { it.copy(doubleTapDelayMs = v.roundToInt()) } },
                        shape = segmentedShape(0, 2),
                    )
                    SliderRow(
                        title = stringResource(R.string.gesture_global_trigger_cooldown),
                        value = trigger.triggerCooldownMs.toFloat(),
                        range = 0f..3_000f,
                        display = { "${it.roundToInt()} ms" },
                        onCommit = { v -> updateTrigger { it.copy(triggerCooldownMs = v.roundToInt()) } },
                        shape = segmentedShape(1, 2),
                    )
                } else {
                    SliderRow(
                        title = stringResource(R.string.gesture_global_trigger_cooldown),
                        value = trigger.triggerCooldownMs.toFloat(),
                        range = 0f..3_000f,
                        display = { "${it.roundToInt()} ms" },
                        onCommit = { v -> updateTrigger { it.copy(triggerCooldownMs = v.roundToInt()) } },
                        shape = segmentedShape(0, 1),
                    )
                }
            }
        }
        item(key = "feedback") {
            group(title = stringResource(R.string.gesture_global_trigger_feedback)) {switchRow(
title = stringResource(R.string.gesture_global_trigger_vibrate),
checked = trigger.vibrate,
onCheckedChange = { enabled -> updateTrigger { it.copy(vibrate = enabled) } },

)
            }
        }

        }
    }
}

    if (showDelete) {
        ConfirmDialog(
            title = stringResource(R.string.gesture_global_trigger_delete),
            text = stringResource(R.string.gesture_global_trigger_delete_desc),
            destructive = true,
            onDismissRequest = { showDelete = false },
            onConfirm = {
                showDelete = false
                scope.launch {
                    GestureConfig.updateGlobalGesture { current ->
                        current.copy(
                            zones = current.zones.map { z ->
                                if (z.id == zoneId) {
                                    z.copy(triggers = z.triggers.filterNot { it.id == triggerId })
                                } else {
                                    z
                                }
                            },
                        )
                    }
                    onBack()
                }
            },
        )
    }
}

/** 「触发行为」组的行数（接管触摸恒一行 + 手指数 + 按住即触发/角度按形态与状态增减）。 */
private fun behaviorRowCount(trigger: GlobalGestureTrigger): Int {
    val fireOnHold = if (trigger.kind.isArmedDrag) 1 else 0
    val angle = if (trigger.kind.allowsAngle() &&
        !(trigger.kind.isArmedDrag && trigger.fireOnHold)) 1 else 0
    return 2 + fireOnHold + angle
}

@Composable
private fun TriggerActionRows(
    trigger: GlobalGestureTrigger,
    onClick: (Int, String) -> Unit,
) {
    val directional = trigger.kind.isDirectional ||
        (trigger.kind.isArmedDrag && !trigger.fireOnHold)
    if (!directional) {
        LunoSettingsRow(
            title = stringResource(R.string.gesture_global_trigger_actions),
            subtitle = trigger.action.actionTextCompose(),
            onClick = { onClick(0, "") },
            shape = segmentedShape(0, 1),
        )
        return
    }
    val slots = DirectionLabels.clockwiseSlots(trigger.angle.boundaries)
        .filterNot { it in trigger.angle.zeroWidthSlots() }
    slots.fastForEach { slot ->
        val center = trigger.angle.slotCenterDegrees(slot)
        val label = directionLabel(center, trigger.angle.count)
        SideGestureSettings(
            onClick = { onClick(slot, label) },
            centerDegrees = center,
            count = trigger.angle.count,
            subtitle = trigger.directionActions.actionBy(slot).actionTextCompose(),
            shape = segmentedShape(slots.indexOf(slot), slots.size),
        )
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: @Composable (Float) -> String,
    onCommit: (Float) -> Unit,
    shape: Shape,
) {
    var draft by remember(value) { mutableStateOf(value) }
    LunoTextSlider(
        value = draft,
        onValueChange = { draft = it },
        onValueChangeFinished = { onCommit(draft) },
        text = title,
        valueDisplay = { display(it) },
        valueRange = range,
        shape = shape,
    )
}

@Composable
private fun ZoneBoundsGroup(
    bounds: ZoneBounds,
    adjusting: Boolean,
    onToggleAdjust: () -> Unit,
    onChange: (ZoneBounds) -> Unit,
) {
    LunoGroup(title = stringResource(R.string.gesture_global_zone_bounds)) {
        LunoSettingsRow(
            title = stringResource(R.string.gesture_global_zone_adjust),
            subtitle = stringResource(R.string.gesture_global_zone_adjust_hint),
            onClick = onToggleAdjust,
            shape = segmentedShape(0, 5),
        )
        // 拖动中只改本地草稿，松手才落盘；否则每一帧都写 DataStore 并回灌配置流。
        var draft by remember(bounds) { mutableStateOf(bounds) }
        val maxX = (1f - draft.width).coerceAtLeast(0f)
        val maxY = (1f - draft.height).coerceAtLeast(0f)
        LunoTextSlider(
            value = draft.x.coerceIn(0f, maxX),
            onValueChange = { draft = draft.copy(x = it) },
            onValueChangeFinished = { onChange(draft) },
            text = stringResource(R.string.gesture_global_zone_x),
            valueDisplay = { "${(it * 100).roundToInt()}%" },
            valueRange = 0f..maxX,
            shape = segmentedShape(1, 5),
            enabled = !adjusting,
        )
        LunoTextSlider(
            value = draft.y.coerceIn(0f, maxY),
            onValueChange = { draft = draft.copy(y = it) },
            onValueChangeFinished = { onChange(draft) },
            text = stringResource(R.string.gesture_global_zone_y),
            valueDisplay = { "${(it * 100).roundToInt()}%" },
            valueRange = 0f..maxY,
            shape = segmentedShape(2, 5),
            enabled = !adjusting,
        )
        LunoTextSlider(
            value = draft.width,
            onValueChange = { width ->
                val next = width.coerceIn(MinZoneSize, 1f)
                draft = draft.copy(
                    width = next,
                    x = draft.x.coerceAtMost((1f - next).coerceAtLeast(0f)),
                )
            },
            onValueChangeFinished = { onChange(draft) },
            text = stringResource(R.string.gesture_global_zone_width),
            valueDisplay = { "${(it * 100).roundToInt()}%" },
            valueRange = MinZoneSize..1f,
            shape = segmentedShape(3, 5),
            enabled = !adjusting,
        )
        LunoTextSlider(
            value = draft.height,
            onValueChange = { height ->
                val next = height.coerceIn(MinZoneSize, 1f)
                draft = draft.copy(
                    height = next,
                    y = draft.y.coerceAtMost((1f - next).coerceAtLeast(0f)),
                )
            },
            onValueChangeFinished = { onChange(draft) },
            text = stringResource(R.string.gesture_global_zone_height),
            valueDisplay = { "${(it * 100).roundToInt()}%" },
            valueRange = MinZoneSize..1f,
            shape = segmentedShape(4, 5),
            enabled = !adjusting,
        )
    }
}

private const val MinZoneSize = 0.02f

private enum class BoundsDragMode { None, Move, Resize }

/**
 * 拖动调边界：矩形内拖动 = 移动，右下角拖动 = 改大小；松手才落盘。
 *
 * 只在「拖动调整范围」开关打开时挂到 `floatingContent`：此时整层会接收触摸，
 * 页面滚动让位于调边界，完成后用顶部「完成」退出。
 */
@Composable
private fun ZoneBoundsDragOverlay(
    bounds: ZoneBounds,
    onCommit: (ZoneBounds) -> Unit,
    onCancel: () -> Unit,
) {
    val color = MaterialTheme.colorScheme.primary
    val currentBounds by rememberUpdatedState(bounds)
    val currentOnCommit by rememberUpdatedState(onCommit)
    var draft by remember { mutableStateOf(bounds) }
    var mode by remember { mutableStateOf(BoundsDragMode.None) }
    var dragging by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val rect = draft.toRect(size)
                drawRect(
                    color = color.copy(alpha = 0.14f),
                    topLeft = rect.topLeft,
                    size = rect.size,
                )
                drawRect(
                    color = color,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
                )
                if (dragging || mode == BoundsDragMode.Resize) {
                    drawCircle(
                        color = color,
                        radius = 10.dp.toPx(),
                        center = rect.bottomRight,
                    )
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { position ->
                        draft = currentBounds
                        val frame = Size(size.width.toFloat(), size.height.toFloat())
                        val rect = draft.toRect(frame)
                        val handle = MinInteractiveSize.toPx()
                        val nearCorner = position.x >= rect.right - handle &&
                            position.y >= rect.bottom - handle &&
                            position.x <= rect.right + handle &&
                            position.y <= rect.bottom + handle
                        mode = when {
                            nearCorner -> BoundsDragMode.Resize
                            rect.contains(position) -> BoundsDragMode.Move
                            else -> BoundsDragMode.None
                        }
                        if (mode != BoundsDragMode.None) dragging = true
                    },
                    onDrag = { change, amount ->
                        if (mode == BoundsDragMode.None) return@detectDragGestures
                        change.consume()
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        draft = when (mode) {
                            BoundsDragMode.Resize -> draft.copy(
                                width = (draft.width + amount.x / width)
                                    .coerceIn(MinZoneSize, (1f - draft.x).coerceAtLeast(MinZoneSize)),
                                height = (draft.height + amount.y / height)
                                    .coerceIn(MinZoneSize, (1f - draft.y).coerceAtLeast(MinZoneSize)),
                            )
                            BoundsDragMode.Move -> draft.copy(
                                x = (draft.x + amount.x / width)
                                    .coerceIn(0f, (1f - draft.width).coerceAtLeast(0f)),
                                y = (draft.y + amount.y / height)
                                    .coerceIn(0f, (1f - draft.height).coerceAtLeast(0f)),
                            )
                            BoundsDragMode.None -> draft
                        }
                    },
                    onDragEnd = {
                        if (mode != BoundsDragMode.None) currentOnCommit(draft)
                        mode = BoundsDragMode.None
                        dragging = false
                    },
                    onDragCancel = {
                        draft = currentBounds
                        mode = BoundsDragMode.None
                        dragging = false
                    },
                )
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PageGutter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.gesture_global_zone_adjust_tip),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PressTextButton(onClick = onCancel) {
                Text(stringResource(R.string.gesture_global_zone_adjust_done))
            }
        }
    }
}

private fun ZoneBounds.toRect(size: Size): Rect = Rect(
    offset = Offset(x * size.width, y * size.height),
    size = Size(width * size.width, height * size.height),
)

/**
 * 所有识别区的边界预览：当前区满色，其余区半透明。
 * `drawBehind` 不消费触摸，滑杆照常可拖。
 */
@Composable
private fun ZoneBoundsOverlay(
    zones: List<GlobalGestureZone>,
    activeId: String,
) {    val scheme = MaterialTheme.colorScheme
    val color = scheme.primary
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                zones.fastForEach { zone ->
                    val bounds = zone.bounds
                    val active = zone.id == activeId
                    val stroke = if (active) 2f else 1f
                    val topLeft = Offset(bounds.x * size.width, bounds.y * size.height)
                    val rectSize = Size(bounds.width * size.width, bounds.height * size.height)
                    if (active) {
                        drawRect(
                            color = color.copy(alpha = 0.12f),
                            topLeft = topLeft,
                            size = rectSize,
                        )
                    }
                    drawRect(
                        color = if (active) color else color.copy(alpha = DisabledAlpha),
                        topLeft = topLeft,
                        size = rectSize,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                    )
                }
            },
    )
}

@Composable
private fun AddTriggerSheet(
    onDismissRequest: () -> Unit,
    onCreate: (GlobalGestureKind) -> Unit,
) {
    val scrollState = rememberScrollState()
    LunoSheet(
        onDismissRequest = onDismissRequest,
        scrollState = OptimizedScrollState.Scroll(scrollState),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(bottom = PageGutter),
            verticalArrangement = Arrangement.spacedBy(ListSpacing),
        ) {
            LunoGroup(title = stringResource(R.string.gesture_global_trigger_kind)) {
                GlobalGestureKind.entries.forEachIndexed { index, kind ->
                    LunoSelectableRow(
                        title = kindLabel(kind),
                        selected = false,
                        singleSelect = true,
                        // 点选即建：手指数用 create() 的形态默认值，编辑页「触发行为」里再改。
                        onSelectedChange = { onCreate(kind) },
                        shape = segmentedShape(index, GlobalGestureKind.entries.size),
                    )
                }
            }
        }
    }
}

@Composable
private fun TriggerFingersSheet(
    trigger: GlobalGestureTrigger,
    onDismissRequest: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val fingers = (1..3).filter { trigger.kind.allowsFingers(it) }
    LunoSheet(onDismissRequest = onDismissRequest) {
        LunoGroup(title = stringResource(R.string.gesture_global_trigger_fingers)) {
            fingers.forEachIndexed { index, count ->
                LunoSelectableRow(
                    title = stringResource(R.string.gesture_global_trigger_fingers_value, count),
                    selected = trigger.fingers == count,
                    singleSelect = true,
                    onSelectedChange = { onSelect(count) },
                    shape = segmentedShape(index, fingers.size),
                )
            }
        }
    }
}

@Composable
private fun kindLabel(kind: GlobalGestureKind): String = when (kind) {
    GlobalGestureKind.Tap -> stringResource(R.string.tap_action)
    GlobalGestureKind.DoubleTap -> stringResource(R.string.double_tap_action)
    GlobalGestureKind.LongPress -> stringResource(R.string.long_press)
    GlobalGestureKind.Slide -> stringResource(R.string.slide_action)
    GlobalGestureKind.LongSlide -> stringResource(R.string.long_slide_action)
    GlobalGestureKind.SlideHold -> stringResource(R.string.slide_hold_action)
    GlobalGestureKind.LongSlideHold -> stringResource(R.string.long_slide_hold_action)
}

private fun kindIcon(kind: GlobalGestureKind) = when (kind) {
    GlobalGestureKind.Tap -> Icons.Default.TouchApp
    GlobalGestureKind.DoubleTap -> Icons.Default.TouchApp
    GlobalGestureKind.LongPress -> Icons.Default.TouchApp
    GlobalGestureKind.Slide -> Icons.Default.Swipe
    GlobalGestureKind.LongSlide -> Icons.Default.Gesture
    GlobalGestureKind.SlideHold -> Icons.Default.Swipe
    GlobalGestureKind.LongSlideHold -> Icons.Default.Gesture
}

/** 长按同形：按到阈值后进入「等待拖动」窗口，扇区给动作。 */
private fun GlobalGestureKind.allowsAngle(): Boolean =
    this.isArmedDrag || isDirectional
