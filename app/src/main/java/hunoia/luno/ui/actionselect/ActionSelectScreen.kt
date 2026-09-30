package hunoia.luno.ui.actionselect

import android.app.Activity
import android.content.Intent
import android.content.Intent.ShortcutIconResource
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.blur.layerBackdrop
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aaron.compose.component.UDFComponent
import com.aaron.compose.component.UiBaseEvent
import hunoia.luno.R
import hunoia.luno.config.model.Action
import hunoia.luno.ui.component.TopBar
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.LauncherInfo
import hunoia.luno.ui.navigation.ActionSelect
import hunoia.luno.bridge.feedback.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import hunoia.luno.ui.permission.rememberGetInstalledAppsPermissionState
import hunoia.luno.ui.theme.*


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSelectContent(
    onDismiss: () -> Unit,
    actionSelect: ActionSelect,
    vm: ActionSelectVM = viewModel(
        key = "action_select_${actionSelect.gestureButtonId}_${actionSelect.direction}_${actionSelect.triggerType}_${actionSelect.subGestureId}",
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ActionSelectVM(actionSelect) as T
            }
        }
    )
) {
    var isExpanded by remember { mutableStateOf(false) }
    var reorderMode by remember { mutableStateOf(false) }

    UDFComponent(
        component = vm.udfComponent,
        onEvent = { },
        onBaseEvent = { baseEvent ->
            when (baseEvent) {
                is UiBaseEvent.Finish -> { onDismiss(); true }
                is UiBaseEvent.ResToast -> { showToast(baseEvent.res); true }
                is UiBaseEvent.StringToast -> { showToast(baseEvent.text); true }
                else -> false
            }
        }
    ) { uiState ->
        val coroutineScope = rememberCoroutineScope()

        Box(modifier = Modifier.fillMaxSize()) {
            val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
            val topBarBackdrop = liquidGlassBackdrop()
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentWindowInsets = WindowInsets(),
                topBar = {
                    TopBar(
                        onBack = onDismiss,
                        title = uiState.title,
                        scrollBehavior = scrollBehavior,
                        backdrop = topBarBackdrop,
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .then(Modifier.layerBackdrop(topBarBackdrop))
                ) {
                    val permissionState = rememberGetInstalledAppsPermissionState { granted ->
                        if (granted) {
                            vm.updateAppInfos()
                            vm.updateShortcutInfos()
                        }
                    }
                    LaunchedEffect(Unit) {
                        if (permissionState.isGranted) {
                            vm.updateAppInfos()
                            vm.updateShortcutInfos()
                        }
                    }
                    val context = LocalContext.current
                    var currentLauncherInfo: LauncherInfo? by remember { mutableStateOf(null) }
                    val shortcutLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                        coroutineScope.launch {
                            val launcherInfo = currentLauncherInfo
                            if (result.resultCode == Activity.RESULT_OK && launcherInfo != null) {
                                val bitmap = result.data?.getParcelableExtra(shortcutIconExtraKey(), Bitmap::class.java)
                                val shortcutIconRes = result.data?.getParcelableExtra(shortcutIconResourceExtraKey(), ShortcutIconResource::class.java)
                                val intent = result.data?.getParcelableExtra(shortcutIntentExtraKey(), Intent::class.java)?.toUri(Intent.URI_INTENT_SCHEME)
                                val label = result.data?.getStringExtra(shortcutNameExtraKey()).orEmpty()
                                val iconRes = if (shortcutIconRes != null) {
                                    withContext(Dispatchers.IO) {
                                        QuickLaunchFacade.resolveShortcutIconResourceId(context, shortcutIconRes)
                                    }
                                } else 0
                                val shortcutInfo = LauncherInfo.ShortcutInfo(
                                    packageName = launcherInfo.packageName, className = launcherInfo.className,
                                    intents = intent?.let { listOf(it) } ?: emptyList(), label = label,
                                    iconRes = iconRes, iconPath = null, iconBitmap = bitmap
                                )
                                vm.addNewShortcut(launcherInfo, shortcutInfo)
                                if (uiState.longPressTargetIndex != null) {
                                    vm.selectLongPressAction(shortcutInfo)
                                } else if (uiState.selectedRecord.size < uiState.maxSelectCount) {
                                    vm.select(shortcutInfo, true)
                                }
                            }
                            currentLauncherInfo = null
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        ActionPage(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = ContentBottom),
                            actions = uiState.actions,
                            actionLibraryEntries = uiState.actionLibraryEntries,
                            subGestures = uiState.subGestures,
                            appInfos = uiState.apps,
                            createShortcuts = uiState.createShortcuts,
                            launchShortcuts = uiState.launchShortcuts,
                            selectedRecord = uiState.selectedRecord,
                            maxSelectCount = uiState.maxSelectCount,
                             longPressTargetIndex = uiState.longPressTargetIndex,
                            permissionState = permissionState,
                            onSelect = { action, selected -> vm.select(action, selected) },
                            onSelectLibraryEntry = { entry, selected -> vm.select(entry, selected) },
                            onSelectLongPress = { obj -> vm.selectLongPressAction(obj) },
                            onSetLongPress = { index -> vm.startSetLongPressAction(index) },
                            onCancelLongPress = { vm.cancelSetLongPressAction() },
                            onMoveSelected = { from, to -> vm.moveSelectedAction(from, to) },
                            onSelectApp = { appInfo, selected -> vm.select(appInfo, selected) },
                            onSelectShortcut = { shortcutInfo, selected -> vm.select(shortcutInfo, selected) },
                            onAppLongClick = { appInfo -> vm.toggleMiniWindow(appInfo) },
                            onShortcutClick = { launcherInfo ->
                                try {
                                    currentLauncherInfo = launcherInfo
                                    shortcutLauncher.launch(Intent().apply { setClassName(launcherInfo.packageName, launcherInfo.className) })
                                } catch (ignored: Exception) { currentLauncherInfo = null }
                            }
                        )
                    }

                    AnimatedVisibility(
                        visible = uiState.selectedRecord.size > 0 && isExpanded,
                        enter = expandVertically(animationSpec = tween(AnimMedium.toInt())) +
                                fadeIn(animationSpec = tween(AnimMedium.toInt())),
                        exit = shrinkVertically(animationSpec = tween(AnimMedium.toInt())) +
                               fadeOut(animationSpec = tween(AnimMedium.toInt())),
                    ) {
                        SelectedActionSettings(
                            selectedItems = uiState.selectedRecord.list,
                            longPressTargetIndex = uiState.longPressTargetIndex,
                            itemLabel = { context.selectedItemLabel(it, uiState.subGestures, uiState.actionLibraryEntries) },
                            reorderMode = reorderMode,
                            onReorderModeToggle = { reorderMode = !reorderMode },
                            onSetLongPress = { index -> vm.startSetLongPressAction(index) },
                            onCancelLongPress = { vm.cancelSetLongPressAction() },
                            onMoveSelected = { from, to -> vm.moveSelectedAction(from, to) },
                            onRemoveItem = { item ->
                                when (item) {
                                    is Action -> vm.select(item, false)
                                    is AppInfo -> vm.select(item, false)
                                    is LauncherInfo.ShortcutInfo -> vm.select(item, false)
                                }
                            },
                            onClearAll = {
                                uiState.selectedRecord.list.toList().forEach { item ->
                                    when (item) {
                                        is Action -> vm.select(item, false)
                                        is AppInfo -> vm.select(item, false)
                                        is LauncherInfo.ShortcutInfo -> vm.select(item, false)
                                    }
                                }
                            }
                        )
                    }

                    if (uiState.selectedRecord.size > 0) {
                        val inLongPressMode = uiState.longPressTargetIndex != null
                        SelectedBottomBar(
                            count = uiState.selectedRecord.size,
                            expanded = isExpanded,
                            inLongPressMode = inLongPressMode,
                            onToggleExpand = { if (!inLongPressMode) isExpanded = !isExpanded },
                            onDone = { if (!inLongPressMode) vm.done() },
                            onCancel = { vm.cancelSetLongPressAction() },
                        )
                    }
                }
            }

        }
    }
}


@Composable
private fun SelectedBottomBar(
    count: Int,
    expanded: Boolean,
    inLongPressMode: Boolean,
    onToggleExpand: () -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val backdrop = liquidGlassBackdrop()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .padding(start = PageGutter, end = PageGutter, bottom = BottomBarPadding)
            .clip(RoundedCornerShape(BottomBarCapsuleRadius))
            .liquidGlassBlur(backdrop),
        color = glassSurfaceColor(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BottomBarHeight)
                .layerBackdrop(backdrop)
                .padding(horizontal = PageGutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (inLongPressMode) {
                Text(
                    text = stringResource(R.string.choose_long_press_action_hint),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel))
                }
            } else {
                Text(
                    text = stringResource(R.string.selected_count_no_limit, count),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onToggleExpand) {
                    Text(if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand))
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(ShapeExtraSmall))
                FilledTonalButton(onClick = onDone) {
                    Text(stringResource(R.string.done))
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun shortcutIconExtraKey(): String = Intent.EXTRA_SHORTCUT_ICON

@Suppress("DEPRECATION")
private fun shortcutIconResourceExtraKey(): String = Intent.EXTRA_SHORTCUT_ICON_RESOURCE

@Suppress("DEPRECATION")
private fun shortcutIntentExtraKey(): String = Intent.EXTRA_SHORTCUT_INTENT

@Suppress("DEPRECATION")
private fun shortcutNameExtraKey(): String = Intent.EXTRA_SHORTCUT_NAME
