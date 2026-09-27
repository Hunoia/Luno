package hunoia.luno.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aaron.compose.component.UDFComponent
import hunoia.luno.R
import hunoia.luno.bridge.intent.gotoAccessibilitySettings
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.SubGesture
import hunoia.luno.config.model.ThemeColorKey
import hunoia.luno.ui.component.MyColumn
import hunoia.luno.ui.component.TopBar
import hunoia.luno.ui.component.color.ColorPickerBottomSheet
import hunoia.luno.ui.component.color.ColorSelection
import hunoia.luno.ui.actionlibrary.ActionLibraryScreen
import hunoia.luno.ui.theme.ExpressiveMotion
import hunoia.luno.ui.theme.resolveColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavToGestureButtonSettings: (GestureButton) -> Unit,
    onNavToSubGestureEditor: (String) -> Unit,
    onNavToCondition: () -> Unit = {},
    onNavToActionSettings: () -> Unit = {},
    vm: HomeVM = viewModel()
) {
    val scrollState = rememberScrollState()
    val libraryListState = rememberLazyListState()
    var mainTab by rememberSaveable { mutableStateOf(MainTab.Home) }
    var expandedGestureSection by rememberSaveable(
        stateSaver = Saver(
            save = { it?.name ?: "" },
            restore = { name -> if (name.isEmpty()) null else GesturePanelSection.valueOf(name) },
        )
    ) { mutableStateOf<GesturePanelSection?>(GesturePanelSection.TouchButton) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var colorPickerTarget by remember { mutableStateOf<Any?>(null) }
    var colorPickerColor by remember { mutableStateOf(Color.Transparent) }
    var myColumnWindowY by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    DisposableEffect(Unit) {
        onDispose { vm.collapseAll() }
    }

    UDFComponent(
        component = vm.udfComponent,
        onEvent = { event ->
            when (event) {
                is UiEvent.ScrollToBottom -> {
                    scrollState.animateScrollTo(
                        value = scrollState.maxValue,
                        animationSpec = ExpressiveMotion.slowSpatialSpec()
                    )
                }
            }
        }
    ) { uiState ->
        val createFileLauncher = rememberLauncherForActivityResult(
            contract = CreateDocument("*/*")
        ) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            vm.backup(context, uri)
        }
        val getFileLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            vm.precheckRestore(context, uri) {
                pendingRestoreUri = uri
                showRestoreConfirm = true
            }
        }
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(key1 = lifecycleOwner) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                vm.updatePermissionState()
                vm.refreshShizukuStatus()
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (colorPickerTarget != null) {
                val scheme = MaterialTheme.colorScheme
                val themeColorArgb = remember(scheme) {
                    ThemeColorKey.entries.associateWith { it.resolveColor(scheme).toArgb() }
                }
                ColorPickerBottomSheet(
                    onDismissRequest = { colorPickerTarget = null },
                    onColorSelected = { selection ->
                        when (selection) {
                            is ColorSelection.Custom -> {
                                when (val target = colorPickerTarget) {
                                    is GestureButton -> vm.updateGestureButtonColor(target, selection.color.toArgb())
                                    is SubGesture -> vm.updateSubGestureColor(target, selection.color.toArgb())
                                }
                            }
                            is ColorSelection.Theme -> {
                                themeColorArgb[selection.key]?.let { resolvedArgb ->
                                    when (val target = colorPickerTarget) {
                                        is GestureButton -> vm.updateGestureButtonColor(target, resolvedArgb)
                                        is SubGesture -> vm.updateSubGestureColor(target, resolvedArgb)
                                    }
                                }
                            }
                        }
                        colorPickerTarget = null
                    },
                    initialColor = colorPickerColor,
                )
            }

            if (showRestoreConfirm && pendingRestoreUri != null) {
                AlertDialog(
                    onDismissRequest = {
                        showRestoreConfirm = false
                        pendingRestoreUri = null
                    },
                    title = { Text(stringResource(id = R.string.restore_confirm_title)) },
                    text = { Text(stringResource(id = R.string.restore_confirm_desc)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val uri = pendingRestoreUri ?: return@TextButton
                                showRestoreConfirm = false
                                pendingRestoreUri = null
                                vm.restore(context, uri)
                            }
                        ) {
                            Text(stringResource(id = R.string.confirm_restore))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showRestoreConfirm = false
                                pendingRestoreUri = null
                            }
                        ) {
                            Text(stringResource(id = R.string.cancel))
                        }
                    },
                )
            }

            val libraryScrollPx = if (libraryListState.firstVisibleItemIndex == 0) {
                libraryListState.firstVisibleItemScrollOffset
            } else {
                160
            }
            val topBarAlpha by animateFloatAsState(
                targetValue = (when (mainTab) {
                    MainTab.Home -> scrollState.value
                    MainTab.ActionLibrary -> libraryScrollPx
                } / 160f).coerceIn(0f, 1f),
                animationSpec = tween(durationMillis = 240),
                label = "topBarGradientAlpha",
            )

            Scaffold(
                topBar = {
                    TopBar(
                        title = when (mainTab) {
                            MainTab.Home -> stringResource(id = R.string.home_title)
                            MainTab.ActionLibrary -> stringResource(id = R.string.action_library)
                        },
                        showBackIcon = false,
                        actions = {},
                        gradientAlpha = topBarAlpha,
                    )
                }
            ) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    when (mainTab) {
                        MainTab.Home -> {
                            MyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .onGloballyPositioned { coords ->
                                        myColumnWindowY = coords.positionInWindow().y.roundToInt()
                                    },
                                scrollState = scrollState,
                            ) {
                                HomeRuntimeStatusCard(
                                    isGestureSwitchEnabled = uiState.isGestureSwitchEnabled,
                                    onGestureSwitchEnabledChange = { enabled ->
                                        vm.onGestureSwitchChange(enabled) { context.gotoAccessibilitySettings() }
                                    },
                                )

                                Spacer(Modifier.height(24.dp))

                                Column {
                                    HomeActionSettingsCard(onClick = onNavToActionSettings)
                                    Spacer(Modifier.height(8.dp))
                                    HomeConditionCard(onClick = onNavToCondition)
                                    Spacer(Modifier.height(8.dp))
                                    GesturePanel(
                                        gestureButtons = uiState.gestureButtons,
                                        subGestures = uiState.subGestures,
                                        expandedSection = expandedGestureSection,
                                        onExpandedSectionChange = { expandedGestureSection = it },
                                        onGestureButtonClick = onNavToGestureButtonSettings,
                                        onSubGestureClick = onNavToSubGestureEditor,
                                        onGestureCheckedChange = { button, enabled -> vm.onGestureButtonEnabledChange(button, enabled) },
                                        onSubCheckedChange = { gesture, enabled -> vm.onSubGestureEnabledChange(gesture, enabled) },
                                        onAddGesture = { vm.addGestureButton() },
                                        onAddSub = {
                                            val id = java.util.UUID.randomUUID().toString()
                                            vm.addSubGesture(id)
                                        },
                                        onMarkColorClick = { target ->
                                            colorPickerTarget = target
                                            colorPickerColor = when (target) {
                                                is GestureButton -> Color(target.color)
                                                is SubGesture -> Color(target.color)
                                                else -> Color.Transparent
                                            }
                                        },
                                        onGestureButtonRename = { button ->
                                            vm.showRenameDialog(RenameTarget.GestureButton(button = button))
                                        },
                                        onSubGestureRename = { gesture ->
                                            vm.showRenameDialog(RenameTarget.SubGesture(gesture = gesture))
                                        },
                                    )
                                }
                            }
                        }

                        MainTab.ActionLibrary -> {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(bottom = 88.dp)
                            ) {
                                ActionLibraryScreen(listState = libraryListState)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    if (showResetConfirm) {
                        AlertDialog(
                            onDismissRequest = { showResetConfirm = false },
                            title = { Text(stringResource(id = R.string.reset_default_settings_warning)) },
                            text = { Text(stringResource(id = R.string.reset_default_settings_warning_desc)) },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        vm.reset()
                                        showResetConfirm = false
                                    }
                                ) {
                                    Text(stringResource(id = R.string.confirm_reset))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showResetConfirm = false }) {
                                    Text(stringResource(id = R.string.cancel))
                                }
                            },
                        )
                    }

                    MainTabBar(
                        selectedTab = mainTab,
                        onTabSelected = { tab -> mainTab = tab },
                    )

                    Box(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable { vm.showMoreMenu() },
                        contentAlignment = Alignment.Center,
                    ) {
                        MorePopupMenu(
                            expanded = uiState.moreMenuVisible,
                            onDismissRequest = { vm.hideMoreMenu() },
                            onBackupClick = {
                                val appName = context.getString(context.applicationInfo.labelRes)
                                val date = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                                createFileLauncher.launch("${appName}_$date.zip")
                            },
                            onRestoreClick = { getFileLauncher.launch("*/*") },
                            onResetClick = { showResetConfirm = true },
                        )

                        Icon(
                            imageVector = Icons.Filled.MoreHoriz,
                            contentDescription = stringResource(id = R.string.more),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            RenameDialog(
                target = uiState.renameDialogTarget,
                onDismissRequest = { vm.hideRenameDialog() },
                onConfirm = { target, name -> vm.doRename(target, name) },
            )
        }
    }
}
