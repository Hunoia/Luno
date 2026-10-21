package hunoia.luno.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aaron.compose.component.UDFComponent
import hunoia.luno.R
import hunoia.luno.ui.theme.BottomBarIconSize
import hunoia.luno.ui.theme.BottomBarPadding
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.bridge.system.gotoAccessibilitySettings
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.SubGesture
import hunoia.luno.config.model.ThemeColorKey
import hunoia.luno.ui.component.sheet.ColorPickerBottomSheet
import hunoia.luno.ui.component.sheet.ColorSelection
import hunoia.luno.ui.actionlibrary.NewActionLibraryScreen
import hunoia.luno.ui.automation.AutomationRulesTabContent
import hunoia.luno.ui.component.FloatingBottomBar
import hunoia.luno.ui.component.FloatingBottomBarDefaults
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.navigation.NEW_ACTION_LIBRARY_ENTRY_ID
import hunoia.luno.ui.navigation.NEW_AUTOMATION_RULE_ID
import hunoia.luno.ui.theme.resolveColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.LibraryBooks
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.twotone.Home
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.component.screen.HomeShell

enum class MainTab(val label: String, val icon: ImageVector) {
    Home("主页", Icons.TwoTone.Home),
    ActionLibrary("动作库", Icons.AutoMirrored.TwoTone.LibraryBooks),
    Automation("规则", Icons.Filled.AccountTree),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavToGestureButtonSettings: (GestureButton) -> Unit,
    onNavToSubGestureEditor: (String) -> Unit,
    onNavToActionSettings: () -> Unit = {},
    onNavToAutomationEdit: (String) -> Unit = {},
    onNavToActionLibraryEdit: (String, String?) -> Unit = { _, _ -> },
    vm: HomeVM = viewModel()
) {
    val homeListState = rememberLazyListState()
    val libraryListState = rememberLazyListState()
    val automationListState = rememberLazyListState()
    val navigationWindowInsets = WindowInsets.navigationBars.only(
        WindowInsetsSides.Bottom,
    )
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
    val context = LocalContext.current

    DisposableEffect(Unit) {
        onDispose { vm.collapseAll() }
    }

    UDFComponent(
        component = vm.udfComponent,
        onEvent = { event ->
            when (event) {
                is UiEvent.ScrollToBottom -> {
                    val totalItems = homeListState.layoutInfo.totalItemsCount
                    if (totalItems > 0) {
                        homeListState.animateScrollToItem(
                            index = totalItems - 1,
                            scrollOffset = Int.MAX_VALUE,
                        )
                    }
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

        HomeShell(
            title = when (mainTab) {
                MainTab.Home -> stringResource(id = R.string.home_title)
                MainTab.ActionLibrary -> stringResource(id = R.string.action_library)
                MainTab.Automation -> stringResource(id = R.string.automation_settings)
            },
            sheets = {
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
            },
            dialogs = {
                RenameDialog(
                    target = uiState.renameDialogTarget,
                    onDismissRequest = { vm.hideRenameDialog() },
                    onConfirm = { target, name -> vm.doRename(target, name) },
                )
                // 组合期捕获：确认时 onDismissRequest 已先清掉 pendingRestoreUri。
                val restoreUri = pendingRestoreUri
                if (showRestoreConfirm && restoreUri != null) {
                    ConfirmDialog(
                        title = stringResource(R.string.restore_confirm_title),
                        text = stringResource(R.string.restore_confirm_desc),
                        confirmLabel = stringResource(R.string.confirm_restore),
                        onDismissRequest = {
                            showRestoreConfirm = false
                            pendingRestoreUri = null
                        },
                        onConfirm = { vm.restore(context, restoreUri) },
                    )
                }
                if (showResetConfirm) {
                    ConfirmDialog(
                        title = stringResource(R.string.reset_default_settings_warning),
                        text = stringResource(R.string.reset_default_settings_warning_desc),
                        confirmLabel = stringResource(R.string.confirm_reset),
                        destructive = true,
                        onDismissRequest = { showResetConfirm = false },
                        onConfirm = { vm.reset() },
                    )
                }
            },
            bottomBar = { hazeState ->
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = BottomBarPadding)
                        .windowInsetsPadding(navigationWindowInsets),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val showAdd = mainTab == MainTab.ActionLibrary || mainTab == MainTab.Automation
                    FloatingBottomBar(
                        items = MainTab.entries.toList(),
                        selectedIndex = { mainTab.ordinal },
                        onSelected = { index -> mainTab = MainTab.entries[index] },
                        hazeState = hazeState,
                        colors = FloatingBottomBarDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            activeContentColor = MaterialTheme.colorScheme.primary,
                        ),
                        showAdd = showAdd,
                        onAddClick = {
                            if (mainTab == MainTab.Automation) {
                                onNavToAutomationEdit(NEW_AUTOMATION_RULE_ID)
                            } else {
                                onNavToActionLibraryEdit(NEW_ACTION_LIBRARY_ENTRY_ID, null)
                            }
                        },
                        addContentDescription = if (mainTab == MainTab.Automation) {
                            stringResource(R.string.automation_new)
                        } else {
                            stringResource(R.string.action_library_add)
                        },
                    ) { tab, _ ->
                        // tint 用底栏的 LocalContentColor（选中 primary/未选中 onSurfaceVariant），不显式传。
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            modifier = Modifier.size(BottomBarIconSize),
                        )
                    }
                }
            },
        ) { topPadding, _ ->
            when (mainTab) {
                        MainTab.Home -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = homeListState,
                            contentPadding = PaddingValues(
                                start = PageGutter,
                                top = topPadding,
                                end = PageGutter,
                                bottom = FloatingContentBottom,
                            ),
                            verticalArrangement = Arrangement.spacedBy(ListSpacing),
                        ) {
                            item(key = "runtime_status") {
                                HomeRuntimeStatusCard(
                                    runtimeStatus = uiState.runtimeStatus,
                                    isGestureSwitchEnabled = uiState.isGestureSwitchEnabled,
                                    onGestureSwitchEnabledChange = { enabled ->
                                        vm.onGestureSwitchChange(enabled) { context.gotoAccessibilitySettings() }
                                    },
                                )
                            }

                            item(key = "settings_group") {
                                HomeSettingsGroup(
                                    onActionSettingsClick = onNavToActionSettings,
                                )
                            }

                            item(key = "gesture_panel") {
                                GesturePanel(
                                    gestureButtons = uiState.gestureButtons,
                                    subGestures = uiState.subGestures,
                                    expandedSection = expandedGestureSection,
                                    onExpandedChange = { expandedGestureSection = it },
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

                            item(key = "data_transfer") {
                                DataTransferGroup(
                                    onBackupClick = {
                                        val appName = context.getString(context.applicationInfo.labelRes)
                                        val date = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                                        createFileLauncher.launch("${appName}_$date.zip")
                                    },
                                    onRestoreClick = { getFileLauncher.launch("*/*") },
                                    onResetClick = { showResetConfirm = true },
                                )
                            }
                        }

                        MainTab.ActionLibrary -> NewActionLibraryScreen(
                            listState = libraryListState,
                            onNavToEdit = onNavToActionLibraryEdit,
                            contentPadding = PaddingValues(top = topPadding),
                        )
                        MainTab.Automation -> AutomationRulesTabContent(
                            listState = automationListState,
                            onNavToEdit = onNavToAutomationEdit,
                            contentPadding = PaddingValues(
                                start = PageGutter,
                                top = topPadding,
                                end = PageGutter,
                                bottom = FloatingContentBottom,
                            ),
                        )
                    }
        }
    }
    }
