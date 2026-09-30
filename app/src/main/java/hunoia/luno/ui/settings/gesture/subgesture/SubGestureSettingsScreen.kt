package hunoia.luno.ui.settings.gesture.subgesture
import hunoia.luno.ui.theme.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureDirection
import hunoia.luno.config.model.GestureTriggerType
import hunoia.luno.config.model.SubGesture
import hunoia.luno.gesture.GestureFacade
import hunoia.luno.ui.component.actionTextCompose
import hunoia.luno.ui.navigation.ActionSelect
import hunoia.luno.ui.component.MyAlertDialog
import hunoia.luno.ui.component.MyColumn
import hunoia.luno.ui.component.OptimizedBottomSheet
import hunoia.luno.ui.component.TopBar
import hunoia.luno.ui.component.input.MyTextSlider
import hunoia.luno.ui.component.SegmentedGroup
import hunoia.luno.ui.component.segmentedShape
import hunoia.luno.ui.component.SegmentedSettingsRow
import hunoia.luno.ui.component.SegmentedSwitchRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import hunoia.luno.ui.settings.gesture.subgesture.SubGestureSettingsUiEvent
import hunoia.luno.ui.settings.gesture.subgesture.SubGestureSettingsUiState
import hunoia.luno.ui.settings.gesture.button.GestureActionGroup
import hunoia.luno.ui.settings.gesture.button.actionCardDirections
import hunoia.luno.ui.settings.gesture.button.GestureSlideTriggerDistanceContent
import hunoia.luno.ui.settings.gesture.style.StyleTrailingButton
import hunoia.luno.ui.settings.gesture.style.ActionPanelStyleConfigContent
import hunoia.luno.ui.settings.gesture.style.ActionPanelStyleSelectContent
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

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
    var showStyleSelectFor by remember { mutableStateOf<GestureDirection?>(null) }
    var showStyleConfigFor by remember { mutableStateOf<GestureDirection?>(null) }
    var expandedActionGroup by remember { mutableStateOf<GestureActionGroup?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    val listState = rememberLazyListState()
    UDFComponent<SubGestureSettingsUiState, SubGestureSettingsUiEvent>(component = vm.udfComponent, onEvent = { }) { uiState ->
        if (uiState.showDeleteWarningDialog) {
            MyAlertDialog(
                onDismissRequest = { vm.showDeleteWarningDialog(false) },
                title = stringResource(id = R.string.delete_sub_gesture_warning),
                text = stringResource(id = R.string.delete_sub_gesture_warning_desc),
                onConfirmClick = { vm.deleteSubGesture() }
            )
        }
        if (uiState.showMirrorCopyDialog) {
            MyAlertDialog(
                onDismissRequest = { vm.showMirrorCopyDialog(false) },
                title = stringResource(id = R.string.mirror_sub_gesture),
                text = stringResource(id = R.string.mirror_sub_gesture_desc),
                onConfirmClick = { vm.createMirroredCopy() }
            )
        }

        val gesture = uiState.subGesture ?: return@UDFComponent

        if (showGestureAngles) {
            OptimizedBottomSheet(
                onDismissRequest = { showGestureAngles = false }
            ) {
                SubGestureAngleContent(
                    angle = gesture.angle,
                    onDismiss = { showGestureAngles = false },
                    onSave = { newAngle ->
                        vm.updateAngle(newAngle)
                        showGestureAngles = false
                    },
                    color = Color(gesture.color)
                )
            }
        }

        if (showSubVibrationSettings) {
            OptimizedBottomSheet(
                onDismissRequest = { showSubVibrationSettings = false }
            ) {
                SubGestureVibrationContent(
                    gesture = gesture,
                    vm = vm
                )
            }
        }

        if (showSubTriggerDistanceSettings) {
            OptimizedBottomSheet(
                onDismissRequest = { showSubTriggerDistanceSettings = false }
            ) {
                SubGestureTriggerDistanceContent(
                    gesture = gesture,
                    vm = vm
                )
            }
        }

        showStyleSelectFor?.let { direction ->
            val currentStyle = GestureFacade.styleBy(gesture.longSlideActionPanelStyles, direction)
            OptimizedBottomSheet(
                onDismissRequest = { showStyleSelectFor = null }
            ) {
                ActionPanelStyleSelectContent(
                    currentStyle = currentStyle,
                    onStyleSelected = { style ->
                        vm.updateLongSlideActionPanelStyle(direction, style)
                    },
                    onConfigRequest = { _ ->
                        showStyleSelectFor = null
                        showStyleConfigFor = direction
                    }
                )
            }
        }

        showStyleConfigFor?.let { direction ->
            val currentStyle = GestureFacade.styleBy(gesture.longSlideActionPanelStyles, direction)
            OptimizedBottomSheet(
                onDismissRequest = { showStyleConfigFor = null }
            ) {
                ActionPanelStyleConfigContent(
                    currentStyle = currentStyle,
                    onStyleChanged = { style ->
                        vm.updateLongSlideActionPanelStyle(direction, style)
                    }
                )
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentWindowInsets = WindowInsets(),
            topBar = {
                TopBar(
                    title = uiState.subGesture.name.ifEmpty { stringResource(id = R.string.sub_gesture) },
                    onBack = onBack,
                    scrollBehavior = scrollBehavior,
                    backdrop = backdrop,
                    actions = {
                        IconButton(onClick = { vm.showMirrorCopyDialog(true) }) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                            )
                        }
                        IconButton(onClick = { vm.showDeleteWarningDialog(true) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                            )
                        }
                    },
                    postfixTitle = {
                        Box(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(MarkColorSize)
                                .background(
                                    color = Color(gesture.color).copy(alpha = GestureButtonColorAlpha),
                                    shape = CircleShape
                                )
                            )
                    },
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(Modifier.layerBackdrop(backdrop)),
            ) {
                val styleGestureButton = remember(gesture.id, gesture.color, gesture.longSlideActionPanelStyles) {
                    GestureButton(
                        id = gesture.id,
                        color = gesture.color,
                        longSlideActionPanelStyles = gesture.longSlideActionPanelStyles,
                    )
                }
                fun setExpandedGroup(group: GestureActionGroup, expanded: Boolean) {
                    expandedActionGroup = if (expanded) group else null
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(
                        start = PageGutter,
                        top = paddingValues.calculateTopPadding(),
                        end = PageGutter,
                        bottom = ContentBottom,
                    ),
                    verticalArrangement = Arrangement.spacedBy(ListSpacing),
                ) {
                    item {
                        SegmentedGroup(
                            title = stringResource(id = R.string.trigger_actions),
                            subtitle = stringResource(id = R.string.trigger_actions_subtitle),
                            contentSpacing = SegmentedGap,
                        ) {
                            SubGestureActionSection(
                                title = stringResource(id = R.string.slide_action),
                                icon = Icons.Default.Swipe,
                                expanded = expandedActionGroup == GestureActionGroup.Slide,
                                onExpandedChange = { setExpandedGroup(GestureActionGroup.Slide, it) },
                            ) {
                                val directions = actionCardDirections.filterNot { it in gesture.angle.zeroWidthDirections() }
                                directions.forEachIndexed { index, direction ->
                                    SubGestureActionRow(
                                        title = direction.label(),
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
                                        secondaryText = gesture.slideActionsFor(direction).actionTextCompose().ifEmpty { stringResource(id = R.string.action_none) },
                                        shape = segmentedShape(index, directions.size),
                                    )
                                }
                            }
                            SubGestureActionSection(
                                title = stringResource(id = R.string.slide_hold_action),
                                icon = Icons.Default.Swipe,
                                expanded = expandedActionGroup == GestureActionGroup.SlideHold,
                                onExpandedChange = { setExpandedGroup(GestureActionGroup.SlideHold, it) },
                            ) {
                                val directions = actionCardDirections.filterNot { it in gesture.angle.zeroWidthDirections() }
                                directions.forEachIndexed { index, direction ->
                                    SubGestureActionRow(
                                        title = direction.label(),
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
                                        secondaryText = gesture.slideHoldActionsFor(direction).actionTextCompose().ifEmpty { stringResource(id = R.string.action_none) },
                                        shape = segmentedShape(index, directions.size),
                                    )
                                }
                            }
                            SubGestureActionSection(
                                title = stringResource(id = R.string.long_slide_action),
                                icon = Icons.Default.Gesture,
                                expanded = expandedActionGroup == GestureActionGroup.LongSlide,
                                onExpandedChange = { setExpandedGroup(GestureActionGroup.LongSlide, it) },
                            ) {
                                val directions = actionCardDirections.filterNot { it in gesture.angle.zeroWidthDirections() }
                                directions.forEachIndexed { index, direction ->
                                    SubGestureActionRow(
                                        title = direction.label(),
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
                                        secondaryText = gesture.longSlideActionsFor(direction).actionTextCompose().ifEmpty { stringResource(id = R.string.action_none) },
                                        shape = segmentedShape(index, directions.size),
                                        trailing = {
                                            StyleTrailingButton(
                                                currentStyle = GestureFacade.styleBy(gesture.longSlideActionPanelStyles, direction),
                                                onClick = { showStyleSelectFor = direction }
                                            )
                                        }
                                    )
                                }
                            }
                            SubGestureActionSection(
                                title = stringResource(id = R.string.long_slide_hold_action),
                                icon = Icons.Default.Gesture,
                                expanded = expandedActionGroup == GestureActionGroup.LongSlideHold,
                                onExpandedChange = { setExpandedGroup(GestureActionGroup.LongSlideHold, it) },
                            ) {
                                val directions = actionCardDirections.filterNot { it in gesture.angle.zeroWidthDirections() }
                                directions.forEachIndexed { index, direction ->
                                    SubGestureActionRow(
                                        title = direction.label(),
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
                                        secondaryText = gesture.longSlideHoldActionsFor(direction).actionTextCompose().ifEmpty { stringResource(id = R.string.action_none) },
                                        shape = segmentedShape(index, directions.size),
                                        trailing = {
                                            StyleTrailingButton(
                                                currentStyle = GestureFacade.styleBy(gesture.longSlideActionPanelStyles, direction),
                                                onClick = { showStyleSelectFor = direction }
                                            )
                                        }
                                    )
                                }
                            }
                            SegmentedSettingsRow(
                                title = stringResource(id = R.string.sub_gesture_angles),
                                icon = Icons.Default.Straighten,
                                shape = segmentedShape(0, 3),
                                onClick = { showGestureAngles = true },
                            )
                            SegmentedSettingsRow(
                                title = stringResource(id = R.string.gesture_button_vibration),
                                icon = Icons.Default.Vibration,
                                shape = segmentedShape(1, 3),
                                onClick = { showSubVibrationSettings = true },
                            )
                            SegmentedSettingsRow(
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
    }
}

@Composable
private fun SubGestureActionSection(
    title: String,
    icon: ImageVector,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        label = "SubGestureSectionArrowRotation",
    )
    val headerBottomRadius by animateDpAsState(
        targetValue = if (expanded) ConnectionRadius else ContainerRadius,
        label = "SubGestureSectionHeaderBottomRadius",
    )
    val headerShape = RoundedCornerShape(
        topStart = ContainerRadius,
        topEnd = ContainerRadius,
        bottomStart = headerBottomRadius,
        bottomEnd = headerBottomRadius,
    )
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ContainerRadius)),
        verticalArrangement = Arrangement.spacedBy(SegmentedGap),
    ) {
        SegmentedSettingsRow(
            title = title,
            icon = icon,
            onClick = { onExpandedChange(!expanded) },
            shape = headerShape,
            trailingContent = {
                Icon(
                    modifier = Modifier
                        .size(RowIconSize)
                        .graphicsLayer { rotationZ = rotation },
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                )
            },
        )

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(
                modifier = Modifier.clip(segmentedShape(1, 2)),
                verticalArrangement = Arrangement.spacedBy(SegmentedGap),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SubGestureActionRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    secondaryText: String,
    shape: Shape = RoundedCornerShape(ContainerRadius),
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    SegmentedSettingsRow(
        title = title,
        icon = icon,
        onClick = onClick,
        shape = shape,
        subtitle = secondaryText,
        leadingContent = {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(ContainerRadius),
                color = colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        imageVector = icon,
                        contentDescription = null,
                        tint = colorScheme.onPrimaryContainer,
                    )
                }
            }
        },
        trailingContent = trailing,
    )
}

@Composable
private fun GestureDirection.label(): String = when (this) {
    GestureDirection.Left -> stringResource(R.string.slide_to_left)
    GestureDirection.UpLeft -> stringResource(R.string.slide_to_top_left)
    GestureDirection.Up -> stringResource(R.string.slide_to_top)
    GestureDirection.UpRight -> stringResource(R.string.slide_to_top_right)
    GestureDirection.Right -> stringResource(R.string.slide_to_right)
    GestureDirection.DownRight -> stringResource(R.string.slide_to_bottom_right)
    GestureDirection.Down -> stringResource(R.string.slide_to_bottom)
    GestureDirection.DownLeft -> stringResource(R.string.slide_to_bottom_left)
}

@Composable
private fun SubGestureVibrationContent(
    gesture: SubGesture,
    vm: SubGestureSettingsVM
) {
    MyColumn(verticalArrangement = Arrangement.spacedBy(CardInnerSpacing)) {
        SegmentedSwitchRow(
            onCheckedChange = { vm.onSubSlideVibrateChange(it) },
            checked = gesture.slideVibrate,
            title = stringResource(R.string.vibration_slide),
        )
        SegmentedSwitchRow(
            onCheckedChange = { vm.onSubLongSlideVibrateChange(it) },
            checked = gesture.longSlideVibrate,
            title = stringResource(R.string.vibration_long_slide),
        )
        SegmentedSwitchRow(
            onCheckedChange = { vm.onSubSlideHoldVibrateChange(it) },
            checked = gesture.slideHoldVibrate,
            title = stringResource(R.string.vibration_slide_hold),
        )
        SegmentedSwitchRow(
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
    MyColumn {
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
        )
        MyTextSlider(
            value = gesture.timeoutMs.toFloat(),
            onValueChange = { vm.onSubTimeoutMsChange(it) },
            text = stringResource(R.string.sub_gesture_timeout_label),
            valueDisplay = stringResource(R.string.sub_gesture_timeout_value, gesture.timeoutMs / 1000),
            valueRange = MinSubGestureTimeoutMs.toFloat()..MaxSubGestureTimeoutMs.toFloat()
        )
    }
}
