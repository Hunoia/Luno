package hunoia.luno.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import hunoia.luno.ui.component.color.ColorPickerBottomSheet
import hunoia.luno.ui.component.color.ColorSelection
import hunoia.luno.ui.actionlibrary.NewActionLibraryScreen
import hunoia.luno.ui.component.FloatingBottomBar
import hunoia.luno.ui.component.FloatingBottomBarDefaults
import hunoia.luno.ui.component.FloatingBottomBarMode
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.navigation.NEW_ACTION_LIBRARY_ENTRY_ID
import hunoia.luno.ui.theme.resolveColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.LibraryBooks
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.twotone.Home
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import hunoia.luno.ui.library.liquid.InnerShadow
import hunoia.luno.ui.library.liquid.innerShadow
import hunoia.luno.ui.library.liquid.lens
import hunoia.luno.ui.library.liquid.vibrancy

enum class MainTab(val label: String, val icon: ImageVector) {
    Home("主页", Icons.TwoTone.Home),
    ActionLibrary("动作库", Icons.AutoMirrored.TwoTone.LibraryBooks),
}

private val capsuleHighlight: Highlight = Highlight(
    width = 1.dp,
    alpha = 1f,
    style = BloomStroke(
        color = Color.White.copy(alpha = 0.12f),
        innerBlurRadius = 2.0.dp,
        primaryLight = LightSource(
            position = LightPosition(0.5f, -0.3f, -0.05f),
            color = Color.White,
            intensity = 1f,
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.5f, 0.8f, -0.5f),
            color = Color.White,
            intensity = 0.4f,
        ),
        dualPeak = true,
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavToGestureButtonSettings: (GestureButton) -> Unit,
    onNavToSubGestureEditor: (String) -> Unit,
    onNavToCondition: () -> Unit = {},
    onNavToActionSettings: () -> Unit = {},
    onNavToActionLibraryEdit: (String, String?) -> Unit = { _, _ -> },
    vm: HomeVM = viewModel()
) {
    val homeListState = rememberLazyListState()
    val libraryListState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val isBlurSupported = isRenderEffectSupported()
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
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

            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentWindowInsets = WindowInsets(),
                topBar = {
                    MediumTopAppBar(
                        modifier = Modifier
                            .then(
                                if (isBlurSupported) Modifier.drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { RectangleShape },
                                    effects = { blur(25.dp.toPx(), 25.dp.toPx()) },
                                    onDrawSurface = {
                                        drawRect(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.White,
                                                    Color.White.copy(alpha = 0f),
                                                ),
                                                startY = 0f,
                                                endY = size.height,
                                            ),
                                            blendMode = BlendMode.DstIn,
                                        )
                                    },
                                ) else Modifier,
                            ),
                        title = {
                            Text(
                                text = when (mainTab) {
                                    MainTab.Home -> stringResource(id = R.string.home_title)
                                    MainTab.ActionLibrary -> stringResource(id = R.string.action_library)
                                },
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .graphicsLayer { alpha = 1f - scrollBehavior.state.collapsedFraction },
                            )
                        },
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = if (isBlurSupported) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
                            titleContentColor = MaterialTheme.colorScheme.onBackground,
                            scrolledContainerColor = if (isBlurSupported) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    )
                },
            ) { paddingValues ->
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(Modifier.layerBackdrop(backdrop)),
                    ) {
                        when (mainTab) {
                        MainTab.Home -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = homeListState,
                            contentPadding = PaddingValues(
                                start = PageGutter,
                                top = paddingValues.calculateTopPadding(),
                                end = PageGutter,
                                bottom = 120.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
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
                                    onConditionClick = onNavToCondition,
                                )
                            }

                            item(key = "gesture_panel") {
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
                            contentPadding = paddingValues,
                        )
                    }
                    }

                    // Bottom bar (overlaid — sibling of content, not child)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp)
                                .windowInsetsPadding(navigationWindowInsets),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FloatingBottomBar(
                                items = MainTab.entries.toList(),
                                selectedIndex = { mainTab.ordinal },
                                onSelected = { index -> mainTab = MainTab.entries[index] },
                                backdrop = backdrop,
                                mode = FloatingBottomBarMode.LiquidGlassBlur,
                                colors = FloatingBottomBarDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    activeContentColor = MaterialTheme.colorScheme.primary,
                                ),
                                iconContent = { tab, _ ->
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label,
                                        modifier = Modifier.size(26.dp),
                                    )
                                },
                            )
                            
                            if (mainTab == MainTab.ActionLibrary) {
                                Box(
                                    modifier = Modifier
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(28.dp))
                                        .then(
                                            if (isBlurSupported) Modifier.drawBackdrop(
                                                backdrop = backdrop,
                                                shape = { RoundedCornerShape(28.dp) },
                                                effects = {
                                                    vibrancy()
                                                    blur(25.dp.toPx(), 25.dp.toPx())
                                                    lens(
                                                        refractionHeight = 24.dp.toPx(),
                                                        refractionAmount = 24.dp.toPx(),
                                                    )
                                                },
                                                highlight = { capsuleHighlight.copy(alpha = 0.75f) },
                                            ) else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                        )
                                        .innerShadow(shape = RoundedCornerShape(28.dp)) {
                                            InnerShadow(
                                                radius = 4.dp,
                                                color = Color.Black.copy(alpha = 0.08f),
                                            )
                                        }
                                        .padding(horizontal = 20.dp)
                                        .clickable {
                                            onNavToActionLibraryEdit(NEW_ACTION_LIBRARY_ENTRY_ID, null)
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = stringResource(R.string.action_library_add),
                                        modifier = Modifier.size(26.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            }
                        }
                    }

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
                }
            }

            RenameDialog(
                target = uiState.renameDialogTarget,
                onDismissRequest = { vm.hideRenameDialog() },
                onConfirm = { target, name -> vm.doRename(target, name) },
            )
        }
    }
