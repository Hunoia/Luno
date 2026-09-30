package hunoia.luno.ui.settings.gesture.button
import hunoia.luno.ui.theme.*

import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aaron.compose.component.UDFComponent
import hunoia.luno.R
import hunoia.luno.config.defaults.SettingsUiDefaults.GestureButtonColorAlpha
import hunoia.luno.ui.navigation.ActionSelect
import hunoia.luno.config.model.GestureButton
import hunoia.luno.ui.component.OptimizedBottomSheet
import hunoia.luno.ui.settings.gesture.button.GestureButtonSettingsUiEvent
import hunoia.luno.ui.settings.gesture.button.GestureButtonSettingsUiState
import hunoia.luno.config.model.GestureDirection
import hunoia.luno.gesture.GestureFacade
import hunoia.luno.gesture.mirroredButton
import hunoia.luno.ui.settings.gesture.style.ActionPanelStyleConfigContent
import hunoia.luno.ui.settings.gesture.style.ActionPanelStyleSelectContent
import hunoia.luno.ui.settings.gesture.subgesture.GestureButtonAngleContent
import hunoia.luno.ui.theme.MarkColorSize
import hunoia.luno.ui.component.MyAlertDialog
import hunoia.luno.ui.component.SegmentedGroup

import androidx.compose.foundation.layout.Arrangement
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import hunoia.luno.ui.component.TopBar


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
    var showStyleSelectFor by remember { mutableStateOf<GestureDirection?>(null) }
    var showStyleConfigFor by remember { mutableStateOf<GestureDirection?>(null) }
    var expandedActionGroup by remember { mutableStateOf<GestureActionGroup?>(null) }
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    UDFComponent<GestureButtonSettingsUiState, GestureButtonSettingsUiEvent>(component = vm.udfComponent, onEvent = { }) { uiState ->
        if (uiState.showDeleteWarningDialog) {
            MyAlertDialog(
                onDismissRequest = { vm.showDeleteWarningDialog(false) },
                title = stringResource(id = R.string.delete_gesture_button_warning),
                text = stringResource(id = R.string.delete_gesture_button_warning_desc),
                onConfirmClick = { vm.deleteGestureButton() }
            )
        }
        Box {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentWindowInsets = WindowInsets(),
                topBar = {
                    TopBar(
                        title = uiState.gestureButton?.name?.ifEmpty { stringResource(id = R.string.gesture_button) }
                            ?: stringResource(id = R.string.gesture_button),
                        onBack = onBack,
                        backdrop = backdrop,
                        postfixTitle = {
                            if (uiState.gestureButton != null) {
                                Box(
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .size(MarkColorSize)
                                        .background(
                                            color = when (uiState.gestureButton.color == android.graphics.Color.TRANSPARENT) {
                                                true -> MaterialTheme.colorScheme.primary.copy(alpha = GestureButtonColorAlpha)
                                                else -> Color(uiState.gestureButton.color).copy(alpha = GestureButtonColorAlpha)
                                            },
                                            shape = CircleShape
                                        )
                                )
                            }
                        },
                        actions = {
                            if (uiState.gestureButton != null && !uiState.gestureButton.isDefault) {
                                IconButton(onClick = { vm.showDeleteWarningDialog(true) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                    )
                                }
                            }
                        },
                        scrollBehavior = scrollBehavior,
                    )
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(Modifier.layerBackdrop(backdrop)),
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        state = listState,
                        contentPadding = PaddingValues(
                            start = PageGutter,
                            top = paddingValues.calculateTopPadding(),
                            end = PageGutter,
                            bottom = ContentBottom,
                        ),
                        verticalArrangement = Arrangement.spacedBy(ListSpacing),
                    ) {
                        val gestureButton = uiState.gestureButton
                        if (gestureButton != null) {
                            fun setExpandedGroup(group: GestureActionGroup, expanded: Boolean) {
                                expandedActionGroup = if (expanded) group else null
                            }
                            item {
                                SegmentedGroup(
                                    title = stringResource(id = R.string.trigger_actions),
                                    subtitle = stringResource(id = R.string.trigger_actions_subtitle),
                                    contentSpacing = SegmentedGap,
                                ) {
                                    GestureButtonTapActionsCard(
                                        gestureButton = gestureButton,
                                        onNavToActionSelect = onNavToActionSelect,
                                        expanded = expandedActionGroup == GestureActionGroup.Tap,
                                        onExpandedChange = { setExpandedGroup(GestureActionGroup.Tap, it) },
                                    )
                                    GestureButtonSlideActionsCard(
                                        gestureButton = gestureButton,
                                        onNavToActionSelect = onNavToActionSelect,
                                        expanded = expandedActionGroup == GestureActionGroup.Slide,
                                        onExpandedChange = { setExpandedGroup(GestureActionGroup.Slide, it) },
                                    )
                                    GestureButtonSlideHoldActionsCard(
                                        gestureButton = gestureButton,
                                        onNavToActionSelect = onNavToActionSelect,
                                        expanded = expandedActionGroup == GestureActionGroup.SlideHold,
                                        onExpandedChange = { setExpandedGroup(GestureActionGroup.SlideHold, it) },
                                    )
                                    GestureButtonLongSlideActionsCard(
                                        gestureButton = gestureButton,
                                        onNavToActionSelect = onNavToActionSelect,
                                        onStyleSelect = { showStyleSelectFor = it },
                                        expanded = expandedActionGroup == GestureActionGroup.LongSlide,
                                        onExpandedChange = { setExpandedGroup(GestureActionGroup.LongSlide, it) },
                                    )
                                    GestureButtonLongSlideHoldActionsCard(
                                        gestureButton = gestureButton,
                                        onNavToActionSelect = onNavToActionSelect,
                                        onStyleSelect = { showStyleSelectFor = it },
                                        expanded = expandedActionGroup == GestureActionGroup.LongSlideHold,
                                        onExpandedChange = { setExpandedGroup(GestureActionGroup.LongSlideHold, it) },
                                    )
                                }
                            }
                            item {
                                GestureButtonPhysicalParamsCard(
                                    gestureButton = gestureButton,
                                    mirrorHorizontal = uiState.mirrorHorizontal,
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

            val colorScheme = MaterialTheme.colorScheme
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        uiState.gestureButtons.fastForEach { button ->
                            fun drawTarget(targetButton: GestureButton) {
                                val bounds = GestureFacade.bounds(targetButton)
                                val color = when (targetButton.color == android.graphics.Color.TRANSPARENT) {
                                    true -> colorScheme.primary
                                    else -> Color(
                                        red = targetButton.color.red,
                                        green = targetButton.color.green,
                                        blue = targetButton.color.blue
                                    )
                                }
                                val highlight = uiState.isGestureButtonAdjusting &&
                                        button.id == uiState.gestureButton?.id
                                drawRect(
                                    color = when (highlight) {
                                        true -> color
                                        else -> color.copy(alpha = GestureButtonColorAlpha)
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

        if (showVibrationSettings) {
            val button = uiState.gestureButton ?: return@UDFComponent
            OptimizedBottomSheet(
                onDismissRequest = { showVibrationSettings = false }
            ) {
                GestureButtonVibrationContent(
                    button = button,
                    vm = vm
                )
            }
        }

        if (showGestureAngles) {
            val button = uiState.gestureButton ?: return@UDFComponent
            OptimizedBottomSheet(
                onDismissRequest = { showGestureAngles = false }
            ) {
                GestureButtonAngleContent(
                    angle = button.angle,
                    onDismiss = { showGestureAngles = false },
                    onSave = {
                        vm.updateGestureButtonAngle(it)
                        showGestureAngles = false
                    }
                )
            }
        }

        if (showTriggerDistanceSettings) {
            val button = uiState.gestureButton ?: return@UDFComponent
            OptimizedBottomSheet(
                onDismissRequest = { showTriggerDistanceSettings = false }
            ) {
                GestureButtonTriggerDistanceContent(
                    button = button,
                    vm = vm
                )
            }
        }

        showStyleSelectFor?.let { direction ->
            val gestureButton = uiState.gestureButton ?: return@let
            val currentStyle = GestureFacade.styleBy(gestureButton.longSlideActionPanelStyles, direction)
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
            val gestureButton = uiState.gestureButton ?: return@let
            val currentStyle = GestureFacade.styleBy(gestureButton.longSlideActionPanelStyles, direction)
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

    }
}
