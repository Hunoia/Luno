package hunoia.luno.ui.actionselect

import android.app.Activity
import android.content.Intent
import android.content.Intent.ShortcutIconResource
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aaron.compose.component.UDFComponent
import com.aaron.compose.component.UiBaseEvent
import hunoia.luno.quicklaunch.QuickLaunchFacade
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
                                vm.select(shortcutInfo)
                            }
                            currentLauncherInfo = null
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
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
                            rules = uiState.rules,
                            subGestures = uiState.subGestures,
                            appInfos = uiState.apps,
                            createShortcuts = uiState.createShortcuts,
                            launchShortcuts = uiState.launchShortcuts,
                            selectedRecord = uiState.selectedRecord,
                            permissionState = permissionState,
                            // 点一行即存即返回，没有底栏、没有完成键、没有多选中间态。
                            onSelect = { action -> vm.select(action) },
                            onSelectLibraryEntry = { entry -> vm.select(entry) },
                            onSelectApp = { appInfo -> vm.select(appInfo) },
                            onSelectShortcut = { shortcutInfo -> vm.select(shortcutInfo) },
                            onAppLongClick = { appInfo -> vm.toggleMiniWindow(appInfo) },
                            onShortcutClick = { launcherInfo ->
                                try {
                                    currentLauncherInfo = launcherInfo
                                    shortcutLauncher.launch(Intent().apply { setClassName(launcherInfo.packageName, launcherInfo.className) })
                                } catch (_: Exception) { currentLauncherInfo = null }
                            }
                        )
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
