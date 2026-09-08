package hunoia.luno.ui.actionselect

import android.app.Activity
import android.content.Intent
import android.content.Intent.ShortcutIconResource
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.SnackbarHostState
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
import hunoia.luno.ui.component.OptimizedBottomSheet
import hunoia.luno.ui.component.TopBar
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.LauncherInfo
import hunoia.luno.ui.navigation.ActionSelect
import hunoia.luno.ui.settings.ActionSettingsDialogContent
import hunoia.luno.bridge.feedback.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.Build
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
        if (uiState.actionSettingsDialog.show) {
            ActionSettingsDialogContent(
                onDismissRequest = { vm.showDialog(false) },
                action = uiState.actionSettingsDialog.action,
                onActionDataChanged = { vm.select(uiState.actionSettingsDialog.action.copy(data = it), true) }
            )
        }

        val snackbarHostState = remember { SnackbarHostState() }
        val coroutineScope = rememberCoroutineScope()

        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(topBar = {
                TopBar(
                    onBack = onDismiss,
                    title = uiState.title,
                )
            }) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
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
                        if (uiState.selectedRecord.list.isEmpty()) {
                            vm.reloadData()
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
                            contentPadding = PaddingValues(bottom = ScrollBottomPadding),
                            actions = uiState.actions,
                            actionLibraryEntries = uiState.actionLibraryEntries,
                            subGestures = uiState.subGestures,
                            appInfos = uiState.apps,
                            createShortcuts = uiState.createShortcuts,
                            launchShortcuts = uiState.launchShortcuts,
                            selectedRecord = uiState.selectedRecord,
                            maxSelectCount = uiState.maxSelectCount,
                            longPressTargetIndex = uiState.longPressTargetIndex,
                            selectSingle = uiState.selectSingle,
                            snackbarHostState = snackbarHostState,
                            permissionState = permissionState,
                            onSelect = { action, selected -> vm.select(action, selected) },
                            onSelectLibraryEntry = { entry, selected -> vm.select(entry, selected) },
                            onSelectLongPress = { obj -> vm.selectLongPressAction(obj) },
                            onSetLongPress = { index -> vm.startSetLongPressAction(index) },
                            onClearLongPress = { index -> vm.clearLongPressAction(index) },
                            onCancelLongPress = { vm.cancelSetLongPressAction() },
                            onMoveSelected = { from, to -> vm.moveSelectedAction(from, to) },
                            onSettingsClick = { action -> vm.showDialog(true, action) },
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
                        visible = !uiState.selectSingle && uiState.selectedRecord.size > 0 && isExpanded,
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

                    if (!uiState.selectSingle && uiState.selectedRecord.size > 0) {
                        SelectedBottomBar(
                            count = uiState.selectedRecord.size,
                            expanded = isExpanded,
                            onToggleExpand = { isExpanded = !isExpanded },
                            onDone = { vm.done() },
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
    onToggleExpand: () -> Unit,
    onDone: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ContentPaddingHorizontal * 2, vertical = Spacing8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                    modifier = Modifier.size(Spacing20),
                )
            }
            Spacer(Modifier.width(Spacing4))
            FilledTonalButton(onClick = onDone) {
                Text(stringResource(R.string.done))
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
