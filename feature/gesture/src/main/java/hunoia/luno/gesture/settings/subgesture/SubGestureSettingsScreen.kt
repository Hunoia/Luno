package hunoia.luno.gesture.settings.subgesture
import hunoia.luno.ui.theme.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aaron.compose.component.UDFComponent
import hunoia.luno.gesture.R
import hunoia.luno.config.defaults.SettingsUiDefaults.MaxSubGestureLongSlideTriggerDistance
import hunoia.luno.config.defaults.SettingsUiDefaults.MaxSubGestureTimeoutMs
import hunoia.luno.config.defaults.SettingsUiDefaults.MaxSubGestureTriggerDistance
import hunoia.luno.config.defaults.SettingsUiDefaults.MinSubGestureLongSlideTriggerDistance
import hunoia.luno.config.defaults.SettingsUiDefaults.MinSubGestureTimeoutMs
import hunoia.luno.config.defaults.SettingsUiDefaults.MinSubGestureTriggerDistance
import hunoia.luno.config.model.GestureTriggerType
import hunoia.luno.config.model.SubGesture
import hunoia.luno.ui.component.actionTextCompose
import hunoia.luno.ui.component.LunoColumn
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.component.input.LunoTextSlider
import androidx.compose.foundation.layout.Arrangement
import hunoia.luno.gesture.settings.subgesture.SubGestureSettingsUiEvent
import hunoia.luno.gesture.settings.subgesture.SubGestureSettingsUiState
import hunoia.luno.gesture.settings.button.GestureActionGroup
import hunoia.luno.gesture.settings.button.GestureSlideTriggerDistanceContent
import hunoia.luno.gesture.settings.button.ExpandableGestureActionCard
import hunoia.luno.gesture.settings.button.actionContentRowShape
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.DirectionLabels
import hunoia.luno.ui.component.directionLabel
import hunoia.luno.gesture.settings.style.SideGestureSettings
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.component.feedback.LunoIconButton
import hunoia.luno.gesture.settings.GestureActionTarget
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubGestureSettingsScreen(
    onBack: () -> Unit,
    onNavToActionSelect: (GestureActionTarget) -> Unit = {},
    vm: SubGestureSettingsVM = viewModel()
) {
    var showGestureAngles by remember { mutableStateOf(false) }
    var showSubVibrationSettings by remember { mutableStateOf(false) }
    var showSubTriggerDistanceSettings by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    UDFComponent<SubGestureSettingsUiState, SubGestureSettingsUiEvent>(component = vm.udfComponent, onEvent = { }) { uiState ->
        if (uiState.showDeleteWarningDialog) {
            ConfirmDialog(
                onDismissRequest = { vm.showDeleteWarningDialog(false) },
                title = stringResource(id = R.string.delete_sub_gesture_warning),
                text = stringResource(id = R.string.delete_sub_gesture_warning_desc),
                destructive = true,
                onConfirm = { vm.deleteSubGesture() }
            )
        }
        if (uiState.showMirrorCopyDialog) {
            ConfirmDialog(
                onDismissRequest = { vm.showMirrorCopyDialog(false) },
                title = stringResource(id = R.string.mirror_sub_gesture),
                text = stringResource(id = R.string.mirror_sub_gesture_desc),
                onConfirm = { vm.createMirroredCopy() }
            )
        }
        val gesture = uiState.subGesture ?: return@UDFComponent
        if (showGestureAngles) {
            LunoSheet(
                onDismissRequest = { showGestureAngles = false },
                showDragHandle = true
            ) {
                SubGestureAngleContent(
                    angle = gesture.angle,
                    onDismissRequest = { showGestureAngles = false },
                    onConfirm = { newAngle ->
                        vm.updateAngle(newAngle)
                        showGestureAngles = false
                    },
                    color = gesture.colorSpec.toColor()
                )
            }
        }
        if (showSubVibrationSettings) {
            LunoSheet(
                onDismissRequest = { showSubVibrationSettings = false },
                showDragHandle = true
            ) {
                SubGestureVibrationContent(
                    gesture = gesture,
                    vm = vm
                )
            }
        }
        if (showSubTriggerDistanceSettings) {
            LunoSheet(
                onDismissRequest = { showSubTriggerDistanceSettings = false },
                showDragHandle = true
            ) {
                SubGestureTriggerDistanceContent(
                    gesture = gesture,
                    vm = vm
                )
            }
        }
    LunoScreenLazy(
        title = uiState.subGesture.name.ifEmpty { stringResource(id = R.string.sub_gesture) },
        onBack = onBack,
        postfixTitle = {
                        Box(
                            modifier = Modifier
                                .padding(start = CardInnerSpacing)
                                .size(MarkColorSize)
                                .background(
                                    color = gesture.colorSpec.toMarkColor(),
                                    shape = CircleShape
                                )
                            )
                    },
        actions = {
                        LunoIconButton(
                            icon = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.mirror_sub_gesture),
                            onClick = { vm.showMirrorCopyDialog(true) },
                        )
                        LunoIconButton(
                            icon = Icons.Default.Delete,
                            contentDescription = stringResource(hunoia.luno.design.R.string.delete),
                            onClick = { vm.showDeleteWarningDialog(true) },
                        )                    },
        state = listState,
    ) {
        fun setExpandedGroup(group: GestureActionGroup, expanded: Boolean) {
            vm.setExpandedActionGroup(if (expanded) group else null)
        }

                    item {
                        LunoGroup(
                            title = stringResource(id = R.string.trigger_actions),
                        ) {
                            ExpandableGestureActionCard(
                                title = stringResource(id = R.string.slide_action),
                                icon = Icons.Default.Swipe,
                                expanded = uiState.expandedActionGroup == GestureActionGroup.Slide,
                                onExpandedChange = { setExpandedGroup(GestureActionGroup.Slide, it) },
                                index = 0,
                                count = 4,
                            ) {
                                val slots = DirectionLabels.clockwiseSlots(gesture.angle.boundaries)
                                    .filterNot { it in gesture.angle.zeroWidthSlots() }
                                slots.forEachIndexed { index, slot ->
                                    val center = gesture.angle.slotCenterDegrees(slot)
                                    val label = directionLabel(center, gesture.angle.count)
                                    SideGestureSettings(
                                        onClick = {
                                            onNavToActionSelect(
                                                GestureActionTarget(
                                                    gestureButtonId = "",
                                                    slot = slot,
                                                    directionLabel = label,
                                                    triggerType = GestureTriggerType.Slide,
                                                    subGestureId = gesture.id,
                                                )
                                            )
                                        },
                                        centerDegrees = center,
                                        count = gesture.angle.count,
                                        subtitle = gesture.slideActionFor(slot).actionTextCompose().ifEmpty { stringResource(id = hunoia.luno.actions.R.string.action_none) },
                                        shape = actionContentRowShape(index, slots.size),
                                    )
                                }
                            }
                            ExpandableGestureActionCard(
                                title = stringResource(id = R.string.slide_hold_action),
                                icon = Icons.Default.Swipe,
                                expanded = uiState.expandedActionGroup == GestureActionGroup.SlideHold,
                                onExpandedChange = { setExpandedGroup(GestureActionGroup.SlideHold, it) },
                                index = 1,
                                count = 4,
                            ) {
                                val slots = DirectionLabels.clockwiseSlots(gesture.angle.boundaries)
                                    .filterNot { it in gesture.angle.zeroWidthSlots() }
                                slots.forEachIndexed { index, slot ->
                                    val center = gesture.angle.slotCenterDegrees(slot)
                                    val label = directionLabel(center, gesture.angle.count)
                                    SideGestureSettings(
                                        onClick = {
                                            onNavToActionSelect(
                                                GestureActionTarget(
                                                    gestureButtonId = "",
                                                    slot = slot,
                                                    directionLabel = label,
                                                    triggerType = GestureTriggerType.SlideHold,
                                                    subGestureId = gesture.id,
                                                )
                                            )
                                        },
                                        centerDegrees = center,
                                        count = gesture.angle.count,
                                        subtitle = gesture.slideHoldActionFor(slot).actionTextCompose().ifEmpty { stringResource(id = hunoia.luno.actions.R.string.action_none) },
                                        shape = actionContentRowShape(index, slots.size),
                                    )
                                }
                            }
                            ExpandableGestureActionCard(
                                title = stringResource(id = R.string.long_slide_action),
                                icon = Icons.Default.Gesture,
                                expanded = uiState.expandedActionGroup == GestureActionGroup.LongSlide,
                                onExpandedChange = { setExpandedGroup(GestureActionGroup.LongSlide, it) },
                                index = 2,
                                count = 4,
                            ) {
                                val slots = DirectionLabels.clockwiseSlots(gesture.angle.boundaries)
                                    .filterNot { it in gesture.angle.zeroWidthSlots() }
                                slots.forEachIndexed { index, slot ->
                                    val center = gesture.angle.slotCenterDegrees(slot)
                                    val label = directionLabel(center, gesture.angle.count)
                                    SideGestureSettings(
                                        onClick = {
                                            onNavToActionSelect(
                                                GestureActionTarget(
                                                    gestureButtonId = "",
                                                    slot = slot,
                                                    directionLabel = label,
                                                    triggerType = GestureTriggerType.LongSlide,
                                                    subGestureId = gesture.id,
                                                )
                                            )
                                        },
                                        centerDegrees = center,
                                        count = gesture.angle.count,
                                        subtitle = gesture.longSlideActionFor(slot).actionTextCompose().ifEmpty { stringResource(id = hunoia.luno.actions.R.string.action_none) },
                                        shape = actionContentRowShape(index, slots.size),
                                    )
                                }
                            }
                            ExpandableGestureActionCard(
                                title = stringResource(id = R.string.long_slide_hold_action),
                                icon = Icons.Default.Gesture,
                                expanded = uiState.expandedActionGroup == GestureActionGroup.LongSlideHold,
                                onExpandedChange = { setExpandedGroup(GestureActionGroup.LongSlideHold, it) },
                                index = 3,
                                count = 4,
                            ) {
                                val slots = DirectionLabels.clockwiseSlots(gesture.angle.boundaries)
                                    .filterNot { it in gesture.angle.zeroWidthSlots() }
                                slots.forEachIndexed { index, slot ->
                                    val center = gesture.angle.slotCenterDegrees(slot)
                                    val label = directionLabel(center, gesture.angle.count)
                                    SideGestureSettings(
                                        onClick = {
                                            onNavToActionSelect(
                                                GestureActionTarget(
                                                    gestureButtonId = "",
                                                    slot = slot,
                                                    directionLabel = label,
                                                    triggerType = GestureTriggerType.LongSlideHold,
                                                    subGestureId = gesture.id,
                                                )
                                            )
                                        },
                                        centerDegrees = center,
                                        count = gesture.angle.count,
                                        subtitle = gesture.longSlideHoldActionFor(slot).actionTextCompose().ifEmpty { stringResource(id = hunoia.luno.actions.R.string.action_none) },
                                        shape = actionContentRowShape(index, slots.size),
                                    )
                                }
                            }
                        }
                    }
                    item {
                        LunoGroup(
                            title = stringResource(id = R.string.physical_params),
                        ) {
                            LunoSettingsRow(
                                title = stringResource(id = R.string.sub_gesture_angles),
                                icon = Icons.Default.Straighten,
                                shape = segmentedShape(0, 5),
                                onClick = { showGestureAngles = true },
                            )
                            LunoSettingsRow(
                                title = stringResource(id = R.string.gesture_button_vibration),
                                icon = Icons.Default.Vibration,
                                shape = segmentedShape(1, 5),
                                onClick = { showSubVibrationSettings = true },
                            )
                            LunoSettingsRow(
                                title = stringResource(id = R.string.gesture_button_trigger_distance),
                                icon = Icons.Default.Tune,
                                shape = segmentedShape(2, 5),
                                onClick = { showSubTriggerDistanceSettings = true },
                            )
                            LunoSwitchRow(
                                onCheckedChange = { vm.onSubShowAngleGuideChange(it) },
                                checked = gesture.showAngleGuide,
                                title = stringResource(id = R.string.sub_gesture_angle_guide),
                                shape = segmentedShape(3, 5),
                            )
                            LunoTextSlider(
                                value = gesture.timeoutMs.toFloat(),
                                onValueChange = { vm.onSubTimeoutMsChange(it) },
                                onValueChangeFinished = { vm.persistSubGesture() },
                                text = stringResource(R.string.sub_gesture_timeout_label),
                                valueDisplay = {
                                    stringResource(R.string.sub_gesture_timeout_value, it.toLong() / 1000)
                                },
                                valueRange = MinSubGestureTimeoutMs.toFloat()..MaxSubGestureTimeoutMs.toFloat(),
                                shape = segmentedShape(3, 4),
                            )
                        }
                    }
                
    }
}
}
@Composable
private fun SubGestureVibrationContent(
    gesture: SubGesture,
    vm: SubGestureSettingsVM
) {
    LunoColumn(contentPadding = 0.dp, verticalArrangement = Arrangement.spacedBy(ListSpacing)) {
        LunoGroup(title = stringResource(R.string.vibration_group_slide)) {
            LunoSwitchRow(
                onCheckedChange = { vm.onSubSlideVibrateChange(it) },
                checked = gesture.slideVibrate,
                title = stringResource(R.string.vibration_slide),
                shape = segmentedShape(0, 2),
            )
            LunoSwitchRow(
                onCheckedChange = { vm.onSubLongSlideVibrateChange(it) },
                checked = gesture.longSlideVibrate,
                title = stringResource(R.string.vibration_long_slide),
                shape = segmentedShape(1, 2),
            )
        }
        LunoGroup(title = stringResource(R.string.vibration_group_hold)) {
            LunoSwitchRow(
                onCheckedChange = { vm.onSubSlideHoldVibrateChange(it) },
                checked = gesture.slideHoldVibrate,
                title = stringResource(R.string.vibration_slide_hold),
                shape = segmentedShape(0, 2),
            )
            LunoSwitchRow(
                onCheckedChange = { vm.onSubLongSlideHoldVibrateChange(it) },
                checked = gesture.longSlideHoldVibrate,
                title = stringResource(R.string.vibration_long_slide_hold),
                shape = segmentedShape(1, 2),
            )
        }
        // 会话反馈：进入/按下/超时这类「不是某个方向」的节点，和上面四个方向振动分开成组。
        // 进入分两档：手指还按着（触钮停留/滑动进、面板项长按点火）与抬手/无手指（触摸窗接管）。
        LunoGroup(title = stringResource(R.string.vibration_group_session)) {
            LunoSwitchRow(
                onCheckedChange = { vm.onSubHoldFireVibrateChange(it) },
                checked = gesture.holdFireVibrate,
                title = stringResource(R.string.vibration_sub_hold_fire),
                shape = segmentedShape(0, 4),
            )
            LunoSwitchRow(
                onCheckedChange = { vm.onSubOverlayVibrateChange(it) },
                checked = gesture.overlayVibrate,
                title = stringResource(R.string.vibration_sub_overlay),
                shape = segmentedShape(1, 4),
            )
            LunoSwitchRow(
                onCheckedChange = { vm.onSubPressVibrateChange(it) },
                checked = gesture.pressVibrate,
                title = stringResource(R.string.vibration_sub_press),
                shape = segmentedShape(2, 4),
            )
            LunoSwitchRow(
                onCheckedChange = { vm.onSubTimeoutVibrateChange(it) },
                checked = gesture.timeoutVibrate,
                title = stringResource(R.string.vibration_sub_timeout),
                shape = segmentedShape(3, 4),
            )
        }
    }
}
@Composable
private fun SubGestureTriggerDistanceContent(
    gesture: SubGesture,
    vm: SubGestureSettingsVM
) {
    LunoColumn(contentPadding = 0.dp, verticalArrangement = Arrangement.spacedBy(ListSpacing)) {
        GestureSlideTriggerDistanceContent(
            slideTriggerDistance = gesture.triggerDistance,
            onSlideTriggerDistanceChange = vm::onSubTriggerDistanceChange,
            slideTriggerDistanceRange = MinSubGestureTriggerDistance.toFloat()..MaxSubGestureTriggerDistance.toFloat(),
            longSlideTriggerDistance = gesture.longSlideTriggerDistance,
            onLongSlideTriggerDistanceChange = vm::onSubLongSlideTriggerDistanceChange,
            longSlideTriggerDistanceRange = MinSubGestureLongSlideTriggerDistance.toFloat()..MaxSubGestureLongSlideTriggerDistance.toFloat(),
            slideHoldTriggerDelayMs = gesture.slideHoldTriggerDelayMs,
            onSlideHoldTriggerDelayMsChange = vm::onSubSlideHoldTriggerDelayMsChange,
            longSlideHoldTriggerDelayMs = gesture.longSlideHoldTriggerDelayMs,
            onLongSlideHoldTriggerDelayMsChange = vm::onSubLongSlideHoldTriggerDelayMsChange,
            onValueChangeFinished = vm::persistSubGesture,
        )
    }
}