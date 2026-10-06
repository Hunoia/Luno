package hunoia.luno.ui.settings.gesture.subgesture
import hunoia.luno.ui.theme.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aaron.compose.component.UDFComponent
import hunoia.luno.R
import hunoia.luno.config.defaults.SettingsUiDefaults.GestureButtonColorAlpha
import hunoia.luno.config.defaults.SettingsUiDefaults.MaxSubGestureLongSlideTriggerDistance
import hunoia.luno.config.defaults.SettingsUiDefaults.MaxSubGestureTimeoutMs
import hunoia.luno.config.defaults.SettingsUiDefaults.MaxSubGestureTriggerDistance
import hunoia.luno.config.defaults.SettingsUiDefaults.MinSubGestureLongSlideTriggerDistance
import hunoia.luno.config.defaults.SettingsUiDefaults.MinSubGestureTimeoutMs
import hunoia.luno.config.defaults.SettingsUiDefaults.MinSubGestureTriggerDistance
import hunoia.luno.config.model.GestureTriggerType
import hunoia.luno.config.model.SubGesture
import hunoia.luno.ui.component.actionTextCompose
import hunoia.luno.ui.navigation.ActionSelect
import hunoia.luno.ui.component.LunoColumn
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.component.RowIconBadge
import hunoia.luno.ui.component.input.LunoTextSlider
import androidx.compose.foundation.layout.Arrangement
import hunoia.luno.ui.settings.gesture.subgesture.SubGestureSettingsUiEvent
import hunoia.luno.ui.settings.gesture.subgesture.SubGestureSettingsUiState
import hunoia.luno.ui.settings.gesture.button.GestureActionGroup
import hunoia.luno.ui.settings.gesture.button.actionCardDirections
import hunoia.luno.ui.settings.gesture.button.GestureSlideTriggerDistanceContent
import hunoia.luno.ui.settings.gesture.button.ExpandableGestureActionCard
import hunoia.luno.ui.component.slideToLabel
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.component.feedback.LunoIconButton
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubGestureSettingsScreen(
    onBack: () -> Unit,
    onNavToActionSelect: (ActionSelect) -> Unit = {},
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
                    color = Color(gesture.color)
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
                                    color = Color(gesture.color).copy(alpha = GestureButtonColorAlpha),
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
                            contentDescription = stringResource(R.string.delete),
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
                            ) {
                                val directions = actionCardDirections.filterNot { it in gesture.angle.zeroWidthDirections() }
                                directions.forEachIndexed { index, direction ->
                                    SubGestureActionRow(
                                        title = direction.slideToLabel(),
                                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                                        onClick = {
                                            onNavToActionSelect(
                                                ActionSelect(
                                                    gestureButtonId = "",
                                                    direction = direction,
                                                    triggerType = GestureTriggerType.Slide,
                                                    subGestureId = gesture.id,
                                                )
                                            )
                                        },
                                        subtitle = gesture.slideActionsFor(direction).actionTextCompose().ifEmpty { stringResource(id = R.string.action_none) },
                                        shape = segmentedShape(index, directions.size),
                                    )
                                }
                            }
                            ExpandableGestureActionCard(
                                title = stringResource(id = R.string.slide_hold_action),
                                icon = Icons.Default.Swipe,
                                expanded = uiState.expandedActionGroup == GestureActionGroup.SlideHold,
                                onExpandedChange = { setExpandedGroup(GestureActionGroup.SlideHold, it) },
                            ) {
                                val directions = actionCardDirections.filterNot { it in gesture.angle.zeroWidthDirections() }
                                directions.forEachIndexed { index, direction ->
                                    SubGestureActionRow(
                                        title = direction.slideToLabel(),
                                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                                        onClick = {
                                            onNavToActionSelect(
                                                ActionSelect(
                                                    gestureButtonId = "",
                                                    direction = direction,
                                                    triggerType = GestureTriggerType.SlideHold,
                                                    subGestureId = gesture.id,
                                                )
                                            )
                                        },
                                        subtitle = gesture.slideHoldActionsFor(direction).actionTextCompose().ifEmpty { stringResource(id = R.string.action_none) },
                                        shape = segmentedShape(index, directions.size),
                                    )
                                }
                            }
                            ExpandableGestureActionCard(
                                title = stringResource(id = R.string.long_slide_action),
                                icon = Icons.Default.Gesture,
                                expanded = uiState.expandedActionGroup == GestureActionGroup.LongSlide,
                                onExpandedChange = { setExpandedGroup(GestureActionGroup.LongSlide, it) },
                            ) {
                                val directions = actionCardDirections.filterNot { it in gesture.angle.zeroWidthDirections() }
                                directions.forEachIndexed { index, direction ->
                                    SubGestureActionRow(
                                        title = direction.slideToLabel(),
                                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                                        onClick = {
                                            onNavToActionSelect(
                                                ActionSelect(
                                                    gestureButtonId = "",
                                                    direction = direction,
                                                    triggerType = GestureTriggerType.LongSlide,
                                                    subGestureId = gesture.id,
                                                )
                                            )
                                        },
                                        subtitle = gesture.longSlideActionsFor(direction).actionTextCompose().ifEmpty { stringResource(id = R.string.action_none) },
                                        shape = segmentedShape(index, directions.size),
                                    )
                                }
                            }
                            ExpandableGestureActionCard(
                                title = stringResource(id = R.string.long_slide_hold_action),
                                icon = Icons.Default.Gesture,
                                expanded = uiState.expandedActionGroup == GestureActionGroup.LongSlideHold,
                                onExpandedChange = { setExpandedGroup(GestureActionGroup.LongSlideHold, it) },
                            ) {
                                val directions = actionCardDirections.filterNot { it in gesture.angle.zeroWidthDirections() }
                                directions.forEachIndexed { index, direction ->
                                    SubGestureActionRow(
                                        title = direction.slideToLabel(),
                                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                                        onClick = {
                                            onNavToActionSelect(
                                                ActionSelect(
                                                    gestureButtonId = "",
                                                    direction = direction,
                                                    triggerType = GestureTriggerType.LongSlideHold,
                                                    subGestureId = gesture.id,
                                                )
                                            )
                                        },
                                        subtitle = gesture.longSlideHoldActionsFor(direction).actionTextCompose().ifEmpty { stringResource(id = R.string.action_none) },
                                        shape = segmentedShape(index, directions.size),
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
                                shape = segmentedShape(0, 3),
                                onClick = { showGestureAngles = true },
                            )
                            LunoSettingsRow(
                                title = stringResource(id = R.string.gesture_button_vibration),
                                icon = Icons.Default.Vibration,
                                shape = segmentedShape(1, 3),
                                onClick = { showSubVibrationSettings = true },
                            )
                            LunoSettingsRow(
                                title = stringResource(id = R.string.gesture_button_trigger_distance),
                                icon = Icons.Default.Tune,
                                shape = segmentedShape(2, 3),
                                onClick = { showSubTriggerDistanceSettings = true },
                            )
                        }
                    }
                
    }
}
}
@Composable
private fun SubGestureActionRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    subtitle: String,
    shape: Shape = RoundedCornerShape(ContainerRadius),
        modifier: Modifier = Modifier,
) {
    LunoSettingsRow(
        title = title,
        icon = icon,
        onClick = onClick,
        shape = shape,
        subtitle = subtitle,
        leading = { RowIconBadge(icon) },
    )
}
@Composable
private fun SubGestureVibrationContent(
    gesture: SubGesture,
    vm: SubGestureSettingsVM
) {
    LunoColumn(verticalArrangement = Arrangement.spacedBy(CardInnerSpacing)) {
        LunoSwitchRow(
            onCheckedChange = { vm.onSubSlideVibrateChange(it) },
            checked = gesture.slideVibrate,
            title = stringResource(R.string.vibration_slide),
        )
        LunoSwitchRow(
            onCheckedChange = { vm.onSubLongSlideVibrateChange(it) },
            checked = gesture.longSlideVibrate,
            title = stringResource(R.string.vibration_long_slide),
        )
        LunoSwitchRow(
            onCheckedChange = { vm.onSubSlideHoldVibrateChange(it) },
            checked = gesture.slideHoldVibrate,
            title = stringResource(R.string.vibration_slide_hold),
        )
        LunoSwitchRow(
            onCheckedChange = { vm.onSubLongSlideHoldVibrateChange(it) },
            checked = gesture.longSlideHoldVibrate,
            title = stringResource(R.string.vibration_long_slide_hold),
        )
    }
}
@Composable
private fun SubGestureTriggerDistanceContent(
    gesture: SubGesture,
    vm: SubGestureSettingsVM
) {
    LunoColumn {
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
        LunoTextSlider(
            value = gesture.timeoutMs.toFloat(),
            onValueChange = { vm.onSubTimeoutMsChange(it) },
            onValueChangeFinished = { vm.persistSubGesture() },
            text = stringResource(R.string.sub_gesture_timeout_label),
            valueDisplay = { stringResource(R.string.sub_gesture_timeout_value, it.toLong() / 1000) },
            valueRange = MinSubGestureTimeoutMs.toFloat()..MaxSubGestureTimeoutMs.toFloat()
        )
    }
}