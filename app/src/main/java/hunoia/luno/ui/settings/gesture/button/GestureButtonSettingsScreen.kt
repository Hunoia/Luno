package hunoia.luno.ui.settings.gesture.button
import hunoia.luno.ui.theme.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aaron.compose.component.UDFComponent
import hunoia.luno.R
import hunoia.luno.config.model.GestureButton
import hunoia.luno.gesture.bounds
import hunoia.luno.gesture.mirroredButton
import hunoia.luno.ui.navigation.ActionSelect
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.settings.gesture.button.GestureButtonSettingsUiEvent
import hunoia.luno.ui.settings.gesture.button.GestureButtonSettingsUiState
import hunoia.luno.ui.settings.gesture.subgesture.GestureButtonAngleContent
import hunoia.luno.ui.theme.MarkColorSize
import hunoia.luno.ui.component.gestureButtonMarkColor
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.component.feedback.LunoIconButton
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureButtonSettingsScreen(
    onBack: () -> Unit,
    onNavToActionSelect: (ActionSelect) -> Unit = {},
    vm: GestureButtonSettingsVM = viewModel()
) {
    var showVibrationSettings by remember { mutableStateOf(false) }
    var showTriggerDistanceSettings by remember { mutableStateOf(false) }
    var showGestureAngles by remember { mutableStateOf(false) }
    val shizukuStatus by hunoia.luno.shizuku.ShizukuManager.statusFlow.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    UDFComponent<GestureButtonSettingsUiState, GestureButtonSettingsUiEvent>(component = vm.udfComponent, onEvent = { }) { uiState ->
        if (uiState.showDeleteWarningDialog) {
            ConfirmDialog(
                onDismissRequest = { vm.showDeleteWarningDialog(false) },
                title = stringResource(id = R.string.delete_gesture_button_warning),
                text = stringResource(id = R.string.delete_gesture_button_warning_desc),
                destructive = true,
                onConfirm = { vm.deleteGestureButton() }
            )
        }
    LunoScreenLazy(
        title = uiState.gestureButton?.name?.ifEmpty { stringResource(id = hunoia.luno.core.data.R.string.gesture_button) }
            ?: stringResource(id = hunoia.luno.core.data.R.string.gesture_button),
        onBack = onBack,
        postfixTitle = {
                            if (uiState.gestureButton != null) {
                                Box(
                                    modifier = Modifier
                                        .padding(start = CardInnerSpacing)
                                        .size(MarkColorSize)
                                        .background(
                                            color = gestureButtonMarkColor(uiState.gestureButton.color),
                                            shape = CircleShape
                                        )
                                )
                            }
                        },
        actions = {
                            if (uiState.gestureButton != null && !uiState.gestureButton.isDefault) {
                                LunoIconButton(
                                    icon = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                    onClick = { vm.showDeleteWarningDialog(true) },
                                )
                            }
                        },
        state = listState,
        floatingContent = {
            GestureButtonBoundsOverlay(uiState)
        },
        sheets = {
        val sheetButton = uiState.gestureButton
        if (showVibrationSettings && sheetButton != null) {
            LunoSheet(onDismissRequest = { showVibrationSettings = false }, showDragHandle = true) {
                GestureButtonVibrationContent(button = sheetButton, vm = vm)
            }
        }
        if (showGestureAngles && sheetButton != null) {
            LunoSheet(onDismissRequest = { showGestureAngles = false }, showDragHandle = true) {
                GestureButtonAngleContent(
                    angle = sheetButton.angle,
                    onDismissRequest = { showGestureAngles = false },
                    onConfirm = {
                        vm.updateGestureButtonAngle(it)
                        showGestureAngles = false
                    },
                )
            }
        }
        if (showTriggerDistanceSettings && sheetButton != null) {
            LunoSheet(onDismissRequest = { showTriggerDistanceSettings = false }, showDragHandle = true) {
                GestureButtonTriggerDistanceContent(button = sheetButton, vm = vm)
            }
        }
        },
    ) {

                        val gestureButton = uiState.gestureButton
                        if (gestureButton != null) {
                            fun setExpandedGroup(group: GestureActionGroup, expanded: Boolean) {
                                vm.setExpandedActionGroup(if (expanded) group else null)
                            }
                            item {
                                LunoGroup(
                                    title = stringResource(id = R.string.trigger_actions),
                                ) {
                                    GestureButtonTapActionsCard(
                                        gestureButton = gestureButton,
                                        onNavToActionSelect = onNavToActionSelect,
                                        expanded = uiState.expandedActionGroup == GestureActionGroup.Tap,
                                        onExpandedChange = { setExpandedGroup(GestureActionGroup.Tap, it) },
                                    )
                                    GestureButtonSlideActionsCard(
                                        gestureButton = gestureButton,
                                        onNavToActionSelect = onNavToActionSelect,
                                        expanded = uiState.expandedActionGroup == GestureActionGroup.Slide,
                                        onExpandedChange = { setExpandedGroup(GestureActionGroup.Slide, it) },
                                    )
                                    GestureButtonSlideHoldActionsCard(
                                        gestureButton = gestureButton,
                                        onNavToActionSelect = onNavToActionSelect,
                                        expanded = uiState.expandedActionGroup == GestureActionGroup.SlideHold,
                                        onExpandedChange = { setExpandedGroup(GestureActionGroup.SlideHold, it) },
                                    )
                                    GestureButtonLongSlideActionsCard(
                                        gestureButton = gestureButton,
                                        onNavToActionSelect = onNavToActionSelect,
                                        expanded = uiState.expandedActionGroup == GestureActionGroup.LongSlide,
                                        onExpandedChange = { setExpandedGroup(GestureActionGroup.LongSlide, it) },
                                    )
                                    GestureButtonLongSlideHoldActionsCard(
                                        gestureButton = gestureButton,
                                        onNavToActionSelect = onNavToActionSelect,
                                        expanded = uiState.expandedActionGroup == GestureActionGroup.LongSlideHold,
                                        onExpandedChange = { setExpandedGroup(GestureActionGroup.LongSlideHold, it) },
                                    )
                                }
                            }
                            item {
                                GestureButtonTouchInputGroup(
                                    touchInput = gestureButton.touchInput,
                                    shizukuReady = shizukuStatus.isReady,
                                    onModeChange = vm::onTouchInputModeChange,
                                    onInterceptWidthChange = vm::onInterceptWidthLiveChange,
                                    onCommit = vm::saveSettings,
                                )
                            }
                            item {
                                GestureButtonPhysicalParamsCard(
                                    gestureButton = gestureButton,
                                    mirrorHorizontal = uiState.mirrorHorizontal,
                                    shizukuReady = shizukuStatus.isReady,
                                    vm = vm,
                                    onAngleClick = { showGestureAngles = true },
                                    onVibrationClick = { showVibrationSettings = true },
                                    onTriggerDistanceClick = { showTriggerDistanceSettings = true },
                                )
                            }
                        }

    }
    }
}

/**
 * 触钮位置浮层：把当前所有触钮（含镜像）的热区按屏幕比例画在编辑页上，
 * 调 X/Y/宽/高时随滑动实时更新，正在调的那一个用满色高亮。
 *
 * 挂在模板的 `floatingContent`（Scaffold 之后的兄弟节点）上：既画在内容之上，
 * `drawBehind` 不消费触摸事件，滑杆照常可拖。
 */
@Composable
private fun GestureButtonBoundsOverlay(uiState: GestureButtonSettingsUiState) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                uiState.gestureButtons.fastForEach { button ->
                    fun drawTarget(targetButton: GestureButton) {
                        val bounds = targetButton.bounds()
                        val color = gestureButtonMarkColor(targetButton.color, primary)
                        val highlight = uiState.isGestureButtonAdjusting &&
                                button.id == uiState.gestureButton?.id
                        drawRect(
                            color = when (highlight) {
                                true -> color
                                else -> color.copy(alpha = DisabledAlpha)
                            },
                            topLeft = bounds.topLeft,
                            size = bounds.size
                        )
                    }
                    drawTarget(button)
                    if (button.mirrorHorizontal) {
                        button.mirroredButton()?.let { drawTarget(it) }
                    }
                }
            }
    )
}
