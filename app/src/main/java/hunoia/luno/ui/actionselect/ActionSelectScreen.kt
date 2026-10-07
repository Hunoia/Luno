package hunoia.luno.ui.actionselect

import android.app.Activity
import android.content.Intent
import android.content.Intent.ShortcutIconResource
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.HazeState
import com.aaron.compose.component.UDFComponent
import com.aaron.compose.component.UiBaseEvent
import hunoia.luno.R
import hunoia.luno.action.model.StoredAction
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.LauncherInfo
import hunoia.luno.ui.navigation.ActionSelect
import hunoia.luno.bridge.ui.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import hunoia.luno.ui.permission.rememberGetInstalledAppsPermissionState
import hunoia.luno.ui.theme.*
import hunoia.luno.ui.component.screen.LunoScreenPlain


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSelectContent(
    onDismissRequest: () -> Unit,
    actionSelect: ActionSelect,
    vm: ActionSelectVM = viewModel(
        key = "action_select_${actionSelect.gestureButtonId}_${actionSelect.direction}_${actionSelect.triggerType}_${actionSelect.subGestureId}_${actionSelect.automationRuleId}",
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
                is UiBaseEvent.Finish -> { onDismissRequest(); true }
                is UiBaseEvent.ResToast -> { showToast(baseEvent.res); true }
                is UiBaseEvent.StringToast -> { showToast(baseEvent.text); true }
                else -> false
            }
        }
    ) { uiState ->
        val coroutineScope = rememberCoroutineScope()

        // 内容是自带 weight(1f) 的嵌套列表，内容容器自拼（Scaffold/TopBar/backdrop 由模板提供）
        LunoScreenPlain(
            title = uiState.title,
            onBack = onDismissRequest,
        ) { topPadding, hazeState ->
            Column(modifier = Modifier.fillMaxSize()) {
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
                                } else if (uiState.selectedRecord.size < uiState.maxSelectCount || uiState.maxSelectCount <= 1) {
                                    vm.select(shortcutInfo, true)
                                }
                            }
                            currentLauncherInfo = null
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        ActionPage(
                            modifier = Modifier.fillMaxSize(),
                            // 行组件统一贴边（docs/ui-spec.md §4），左右 gutter 由列表容器提供
                            contentPadding = PaddingValues(
                                start = PageGutter,
                                end = PageGutter,
                                top = topPadding,
                                bottom = ContentBottom,
                            ),
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
                                } catch (_: Exception) { currentLauncherInfo = null }
                            }
                        )
                    }

                    if (uiState.selectedRecord.size > 0 || uiState.selectionDirty) {
                        val inLongPressMode = uiState.longPressTargetIndex != null
                        SelectedBottomBar(
                            count = uiState.selectedRecord.size,
                            expanded = isExpanded,
                            inLongPressMode = inLongPressMode,
                            hazeState = hazeState,
                            onToggleExpand = { if (!inLongPressMode) isExpanded = !isExpanded },
                            onDone = { if (!inLongPressMode) vm.done() },
                            onDismissRequest = { vm.cancelSetLongPressAction() },
                            expandedContent = {
                                SelectedActionSettings(
                                    selectedItems = uiState.selectedRecord.list,
                                    longPressTargetIndex = uiState.longPressTargetIndex,
                                    itemLabel = { context.selectedItemLabel(it, uiState.subGestures, uiState.actionLibraryEntries) },
                                    reorderMode = reorderMode,
                                    onReorderModeToggle = { reorderMode = !reorderMode },
                                    onSetLongPress = { index -> vm.startSetLongPressAction(index) },
                                    longPressVariantEnabled = actionSelect.automationRuleId.isBlank(),
                                    onMoveSelected = { from, to -> vm.moveSelectedAction(from, to) },
                                    onRemoveItem = { item ->
                                        when (item) {
                                            is StoredAction -> vm.select(item, false)
                                            is AppInfo -> vm.select(item, false)
                                            is LauncherInfo.ShortcutInfo -> vm.select(item, false)
                                        }
                                    },
                                    onClearAll = {
                                        uiState.selectedRecord.list.toList().forEach { item ->
                                            when (item) {
                                                is StoredAction -> vm.select(item, false)
                                                is AppInfo -> vm.select(item, false)
                                                is LauncherInfo.ShortcutInfo -> vm.select(item, false)
                                            }
                                        }
                                    },
                                )
                            },
                        )
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
    hazeState: HazeState,
    onToggleExpand: () -> Unit,
    onDone: () -> Unit,
    onDismissRequest: () -> Unit,
    expandedContent: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .padding(start = PageGutter, end = PageGutter, bottom = BottomBarPadding)
            .clip(RoundedCornerShape(BottomBarCapsuleRadius))
            // Haze 单层底：blur + 0.65 底色，组件自身不再画底。
            .appHazeEffect(
                hazeState,
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
            ),
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(AnimMedium.toInt())) +
                    fadeIn(animationSpec = tween(AnimMedium.toInt())),
            exit = shrinkVertically(animationSpec = tween(AnimMedium.toInt())) +
                    fadeOut(animationSpec = tween(AnimMedium.toInt())),
        ) {
            expandedContent()
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BottomBarHeight)
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
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(R.string.cancel))
                }
            } else {
                // 计数 + chevron 合成一个可点簇：点它展收已选 tile 面板。
                Row(
                    modifier = Modifier
                        .heightIn(min = MinInteractiveSize)
                        .clip(CircleShape)
                        .clickable(onClick = onToggleExpand, role = Role.Button)
                        .padding(horizontal = ShapeSmall),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
                ) {
                    Text(
                        text = stringResource(R.string.selected_count_no_limit, count),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(
                            if (expanded) R.string.collapse else R.string.expand,
                        ),
                        modifier = Modifier.size(RowIconSize),
                    )
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = onDone) {
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
