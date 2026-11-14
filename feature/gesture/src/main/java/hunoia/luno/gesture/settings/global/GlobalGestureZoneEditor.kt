package hunoia.luno.gesture.settings.global

import androidx.annotation.Keep
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.config.model.GlobalGestureKind
import hunoia.luno.config.model.GlobalGestureSettings
import hunoia.luno.config.model.GlobalGestureTrigger
import hunoia.luno.config.model.GlobalGestureZone
import hunoia.luno.config.model.ZoneBounds
import hunoia.luno.config.model.allowsFingers
import hunoia.luno.config.model.isDirectional
import hunoia.luno.gesture.R
import hunoia.luno.gesture.settings.GestureActionTarget
import hunoia.luno.gesture.settings.button.ExpandableGestureActionCard
import hunoia.luno.gesture.settings.button.actionContentRowShape
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
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSelectableRow
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.theme.DisabledAlpha
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.MinInteractiveSize
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SegmentedGap
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 识别区编辑页路由参数。 */
@Keep
@kotlinx.serialization.Serializable
data class GlobalGestureZoneEditor(
    val zoneId: String,
)

/**
 * 识别区编辑：边界（屏幕比例）+ 触发列表。
 *
 * 触发的形态与手指数在创建时确定，之后只改动作与阈值；要换形态就删掉重建，
 * 避免改形态后旧动作落在不存在的扇区里成为静默死配置。
 */
@Composable
fun GlobalGestureZoneEditorScreen(
    zoneId: String,
    onBack: () -> Unit,
    onNavToActionSelect: (GestureActionTarget) -> Unit = {},
) {
    val settings by GestureConfig.globalGesture.collectAsStateWithLifecycle(
        initialValue = GlobalGestureSettings(),
    )
    val scope = rememberCoroutineScope()
    val zoneIndex = settings.zones.indexOfFirst { it.id == zoneId }
    val zone = settings.zones.getOrNull(zoneIndex)
    var showAddTrigger by remember { mutableStateOf(false) }
    var showDeleteZone by remember { mutableStateOf(false) }
    var showAngleEditorFor by remember { mutableStateOf<String?>(null) }
    var showFingersFor by remember { mutableStateOf<String?>(null) }
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

    fun deleteTrigger(triggerId: String) {
        updateZone { current -> current.copy(triggers = current.triggers.filterNot { it.id == triggerId }) }
    }

    if (zone == null) {
        // 删除后配置流先到、导航还没回退：不渲染空壳。
        return
    }

    LunoScreenLazy(
        title = globalZoneLabel(zone, zoneIndex),
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
            val angleTriggerId = showAngleEditorFor
            if (angleTriggerId != null) {
                val trigger = zone.triggers.find { it.id == angleTriggerId }
                if (trigger != null) {
                    LunoSheet(onDismissRequest = { showAngleEditorFor = null }, showDragHandle = true) {
                        GestureButtonAngleContent(
                            angle = trigger.angle,
                            onDismissRequest = { showAngleEditorFor = null },
                            onConfirm = { angle ->
                                updateTrigger(trigger.id) { it.copy(angle = angle) }
                                showAngleEditorFor = null
                            },
                        )
                    }
                }
            }
            val fingersTriggerId = showFingersFor
            if (fingersTriggerId != null) {
                val trigger = zone.triggers.find { it.id == fingersTriggerId }
                if (trigger != null) {
                    TriggerFingersSheet(
                        trigger = trigger,
                        onDismissRequest = { showFingersFor = null },
                        onSelect = { fingers ->
                            updateTrigger(trigger.id) { it.copy(fingers = fingers) }
                            showFingersFor = null
                        },
                    )
                }
            }
            if (showAddTrigger) {
                AddTriggerSheet(
                    onDismissRequest = { showAddTrigger = false },
                    onCreate = { kind, fingers ->
                        updateZone { current ->
                            current.copy(
                                triggers = current.triggers + GlobalGestureTrigger.create(kind).copy(
                                    fingers = fingers,
                                ),
                            )
                        }
                        showAddTrigger = false
                    },
                )
            }
        },
    ) {
        item(key = "zone_bounds") {
            ZoneBoundsGroup(
                bounds = zone.bounds,
                adjusting = adjustingBounds,
                onToggleAdjust = { adjustingBounds = !adjustingBounds },
                onChange = { bounds -> updateZone { it.copy(bounds = bounds) } },
            )
        }
        item(key = "trigger_add") {
            LunoGroup {
                LunoSettingsRow(
                    title = stringResource(R.string.gesture_global_trigger_add),
                    icon = Icons.Default.Add,
                    onClick = { showAddTrigger = true },
                    shape = segmentedShape(0, 0),
                )
            }
        }
        if (zone.triggers.isNotEmpty()) {
            item(key = "triggers") {
                LunoGroup(title = stringResource(R.string.gesture_global_trigger)) {
                    zone.triggers.forEachIndexed { index, trigger ->
                        TriggerCard(
                            trigger = trigger,
                            index = index,
                            count = zone.triggers.size,
                            onAngleClick = { showAngleEditorFor = trigger.id },
                            onFingersClick = { showFingersFor = trigger.id },
                            onEnabledChange = { enabled -> updateTrigger(trigger.id) { it.copy(enabled = enabled) } },
                            onVibrateChange = { enabled -> updateTrigger(trigger.id) { it.copy(vibrate = enabled) } },
                            onFireOnHoldChange = { enabled ->
                                updateTrigger(trigger.id) { current ->
                                    if (enabled) {
                                        current.copy(
                                            fireOnHold = true,
                                            action = current.action
                                                ?: current.directionActions.actionBy(0),
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
                            onLongPressDelayChange = { ms -> updateTrigger(trigger.id) { it.copy(longPressDelayMs = ms) } },
                            onDragTimeoutChange = { ms -> updateTrigger(trigger.id) { it.copy(dragStartTimeoutMs = ms) } },
                            onSlopChange = { dp -> updateTrigger(trigger.id) { it.copy(slopDp = dp) } },
                            onCooldownChange = { ms -> updateTrigger(trigger.id) { it.copy(triggerCooldownMs = ms) } },
                            onActionClick = { slot, label ->
                                onNavToActionSelect(
                                    GestureActionTarget(
                                        globalGestureTriggerId = trigger.id,
                                        slot = slot,
                                        directionLabel = label,
                                        triggerType = trigger.kind.triggerType(),
                                    ),
                                )
                            },
                            onDelete = { deleteTrigger(trigger.id) },
                        )
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
private fun TriggerCard(
    trigger: GlobalGestureTrigger,
    index: Int,
    count: Int,
    onAngleClick: () -> Unit,
    onFingersClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onVibrateChange: (Boolean) -> Unit,
    onFireOnHoldChange: (Boolean) -> Unit,
    onLongPressDelayChange: (Int) -> Unit,
    onDragTimeoutChange: (Int) -> Unit,
    onSlopChange: (Float) -> Unit,
    onCooldownChange: (Int) -> Unit,
    onActionClick: (Int, String) -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember(trigger.id) { mutableStateOf(index == 0) }
    val title = buildString {
        append(kindLabel(trigger.kind))
        append(" · ")
        append(stringResource(R.string.gesture_global_trigger_fingers_value, trigger.fingers))
    }
    ExpandableGestureActionCard(
        icon = kindIcon(trigger.kind),
        title = title,
        expanded = expanded,
        onExpandedChange = { expanded = it },
        index = index,
        count = count,
    ) {
        LunoSwitchRow(
            title = stringResource(R.string.gesture_global_trigger_enabled),
            checked = trigger.enabled,
            onCheckedChange = onEnabledChange,
            shape = actionContentRowShape(0, 1),
        )
        if (trigger.kind == GlobalGestureKind.LongPress) {
            LunoSwitchRow(
                title = stringResource(R.string.gesture_global_trigger_fire_on_hold),
                subtitle = stringResource(R.string.gesture_global_trigger_fire_on_hold_hint),
                checked = trigger.fireOnHold,
                onCheckedChange = onFireOnHoldChange,
                shape = actionContentRowShape(0, 1),
            )
        }
        LunoSettingsRow(
            title = stringResource(R.string.gesture_global_trigger_fingers),
            subtitle = stringResource(R.string.gesture_global_trigger_fingers_value, trigger.fingers),
            onClick = onFingersClick,
            shape = actionContentRowShape(0, 1),
        )
        if (trigger.kind.allowsAngle() &&
            !(trigger.kind == GlobalGestureKind.LongPress && trigger.fireOnHold)
        ) {
            LunoSettingsRow(
                title = stringResource(R.string.gesture_angles),
                subtitle = if (trigger.angle.count <= 1) {
                    stringResource(R.string.gesture_global_trigger_any_direction)
                } else {
                    "${trigger.angle.count}"
                },
                onClick = onAngleClick,
                shape = actionContentRowShape(0, 1),
            )
        }
        TriggerActionRows(trigger = trigger, onClick = onActionClick)
        if (trigger.kind == GlobalGestureKind.LongPress) {
            SliderRow(
                title = stringResource(R.string.gesture_global_trigger_long_press_delay),
                value = trigger.longPressDelayMs.toFloat(),
                range = 100f..1_500f,
                display = { "${it.roundToInt()} ms" },
                onCommit = { onLongPressDelayChange(it.roundToInt()) },
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
                onCommit = { onDragTimeoutChange(it.roundToInt()) },
            )
            SliderRow(
                title = stringResource(R.string.gesture_global_trigger_slop),
                value = trigger.slopDp,
                range = 2f..40f,
                display = { "${it.roundToInt()} dp" },
                onCommit = { onSlopChange(it.roundToInt().toFloat()) },
            )
        }
        SliderRow(
            title = stringResource(R.string.gesture_global_trigger_cooldown),
            value = trigger.triggerCooldownMs.toFloat(),
            range = 0f..3_000f,
            display = { "${it.roundToInt()} ms" },
            onCommit = { onCooldownChange(it.roundToInt()) },
        )
        LunoSwitchRow(
            title = stringResource(R.string.gesture_global_trigger_vibrate),
            checked = trigger.vibrate,
            onCheckedChange = onVibrateChange,
            shape = actionContentRowShape(0, 1),
        )
        LunoSettingsRow(
            title = stringResource(R.string.gesture_global_trigger_delete),
            onClick = onDelete,
            shape = actionContentRowShape(0, 1),
        )
    }
}

@Composable
private fun TriggerActionRows(
    trigger: GlobalGestureTrigger,
    onClick: (Int, String) -> Unit,
) {
    val directional = trigger.kind.isDirectional ||
        (trigger.kind == GlobalGestureKind.LongPress && !trigger.fireOnHold)
    if (!directional) {
        LunoSettingsRow(
            title = stringResource(R.string.gesture_global_trigger_actions),
            subtitle = trigger.action.actionTextCompose(),
            onClick = { onClick(0, "") },
            shape = actionContentRowShape(0, 1),
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
            shape = actionContentRowShape(slots.indexOf(slot), slots.size),
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
) {
    var draft by remember(value) { mutableStateOf(value) }
    LunoTextSlider(
        value = draft,
        onValueChange = { draft = it },
        onValueChangeFinished = { onCommit(draft) },
        text = title,
        valueDisplay = { display(it) },
        valueRange = range,
        shape = actionContentRowShape(0, 1),
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
    onCreate: (GlobalGestureKind, Int) -> Unit,
) {
    var pickedKind by remember { mutableStateOf(GlobalGestureKind.LongPress) }
    val fingers = remember(pickedKind) {
        (1..3).filter { pickedKind.allowsFingers(it) }
    }
    LunoSheet(onDismissRequest = onDismissRequest) {
        Text(
            text = stringResource(R.string.gesture_global_trigger_add),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = hunoia.luno.ui.theme.PageGutter),
        )
        androidx.compose.foundation.lazy.LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                bottom = hunoia.luno.ui.theme.ContentBottom,
            ),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(SegmentedGap),
        ) {
            items(GlobalGestureKind.entries.size) { index ->
                val kind = GlobalGestureKind.entries[index]
                LunoSelectableRow(
                    title = kindLabel(kind),
                    selected = pickedKind == kind,
                    singleSelect = true,
                    onSelectedChange = { pickedKind = kind },
                )
            }
            item {
                Text(
                    text = stringResource(R.string.gesture_global_trigger_fingers),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = ListSpacing),
                )
            }
            items(fingers.size) { index ->
                val count = fingers[index]
                LunoSelectableRow(
                    title = stringResource(R.string.gesture_global_trigger_create_fingers, count),
                    selected = false,
                    onSelectedChange = { onCreate(pickedKind, count) },
                )
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
        Text(
            text = stringResource(R.string.gesture_global_trigger_fingers),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = hunoia.luno.ui.theme.PageGutter),
        )
        fingers.fastForEach { count ->
            LunoSelectableRow(
                title = stringResource(R.string.gesture_global_trigger_fingers_value, count),
                selected = trigger.fingers == count,
                singleSelect = true,
                onSelectedChange = { onSelect(count) },
            )
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

private fun GlobalGestureKind.allowsAngle(): Boolean =
    this == GlobalGestureKind.LongPress || isDirectional
