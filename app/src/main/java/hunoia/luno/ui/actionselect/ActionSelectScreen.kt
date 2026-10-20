package hunoia.luno.ui.actionselect

import android.app.Activity
import android.content.Intent
import android.content.Intent.ShortcutIconResource
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
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
import hunoia.luno.R
import hunoia.luno.config.model.GridPanelDefaults
import hunoia.luno.gridpanel.widget.PanelAppWidgetHost
import hunoia.luno.gridpanel.widget.WidgetProviderGroup
import hunoia.luno.gridpanel.widget.WidgetSpan
import hunoia.luno.gridpanel.widget.loadWidgetProviderGroups
import hunoia.luno.gridpanel.widget.widgetSpanFor
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

/** 已分配 id、等着某个 Activity 回话的小部件。 */
private class PendingWidget(
    val appWidgetId: Int,
    val provider: String,
    val span: WidgetSpan,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSelectContent(
    onDismissRequest: () -> Unit,
    actionSelect: ActionSelect,
    vm: ActionSelectVM = viewModel(
        key = "action_select_${actionSelect.gestureButtonId}_${actionSelect.direction}_${actionSelect.triggerType}_${actionSelect.subGestureId}_${actionSelect.automationRuleId}_${actionSelect.actionPanelId}_${actionSelect.gridPanelId}",
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
                                // 创建器返回的**位图图标**必须落盘成文件（iconPath）：
                                // iconBitmap 是 @Transient 不序列化，只存内存的话
                                // 重启/回流后 getIcon() 拿不到 → 图标空白。
                                val iconPath = bitmap?.let { bmp ->
                                    withContext(Dispatchers.IO) {
                                        runCatching {
                                            val dir = File(context.filesDir, "shortcut_icons").apply { mkdirs() }
                                            val file = File(dir, "cs_${launcherInfo.packageName}_${System.currentTimeMillis()}.png")
                                            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                                            file.absolutePath
                                        }.getOrNull()
                                    }
                                }
                                Log.d("ActionSelect", "shortcut created: pkg=${launcherInfo.packageName} label=$label " +
                                    "iconRes=$iconRes iconPath=$iconPath bitmap!=null=${bitmap != null} intent=${intent != null}")
                                val shortcutInfo = LauncherInfo.ShortcutInfo(
                                    packageName = launcherInfo.packageName, className = launcherInfo.className,
                                    intents = intent?.let { listOf(it) } ?: emptyList(), label = label,
                                    iconRes = iconRes, iconPath = iconPath, iconBitmap = bitmap
                                )
                                vm.addNewShortcut(launcherInfo, shortcutInfo)
                                vm.select(shortcutInfo)
                            } else {
                                Log.w("ActionSelect", "shortcut creator returned resultCode=${result.resultCode} " +
                                    "data=${result.data?.extras?.keySet()} launcherInfo=${launcherInfo != null}")
                            }
                            currentLauncherInfo = null
                        }
                    }

                    // ---- 桌面小部件 ----------------------------------------------------
                    // 绑定与配置都要 Activity 回结果（ACTION_APPWIDGET_BIND 要用户点授权，
                    // ACTION_APPWIDGET_CONFIGURE 是 provider 自己的配置页），所以这一段留在
                    // 组合侧而不是 VM：VM 拿不到 launcher，回来的结果也没地方接。
                    // 与选择页同在 :service，和面板共用同一个 PanelAppWidgetHost 实例。
                    val gridPanelId = actionSelect.gridPanelId
                    val gridPanel = uiState.gridPanels.firstOrNull { it.id == gridPanelId }
                    // 面板还没回流（首帧）用默认网格估 span，只影响选择卡上的角标预览；
                    // 落盘时 saveGridPanelWidget 会按最新面板尺寸重新降格，不吃这里的近似。
                    val gridColumns = gridPanel?.columnCount ?: GridPanelDefaults.ColumnCount
                    val gridRows = gridPanel?.gridRows ?: GridPanelDefaults.RowCount * 2
                    var widgetGroups by remember { mutableStateOf<List<WidgetProviderGroup>>(emptyList()) }
                    LaunchedEffect(gridPanelId) {
                        if (gridPanelId.isBlank()) return@LaunchedEffect
                        widgetGroups = runCatching { loadWidgetProviderGroups(context) }
                            .onFailure { showToast(context.getString(R.string.grid_panel_widget_pick_failed)) }
                            .getOrDefault(emptyList())
                    }

                    /** 已分配 id、等着某个 Activity 回话的小部件。 */
                    var pendingWidget by remember { mutableStateOf<PendingWidget?>(null) }

                    // 绑上之后：provider 声明了 configure 就先跑它的配置页，否则直接落盘。
                    // 用参数传 launcher，不写成捕获后面声明的 val —— 少一处前向引用。
                    fun afterBound(p: PendingWidget, configureLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>) {
                        val configure = AppWidgetManager.getInstance(context)
                            .getAppWidgetInfo(p.appWidgetId)?.configure
                        if (configure == null) {
                            pendingWidget = null
                            vm.commitWidget(p.provider, p.appWidgetId, p.span.spanX, p.span.spanY)
                        } else {
                            pendingWidget = p
                            configureLauncher.launch(
                                Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                                    .setComponent(configure)
                                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, p.appWidgetId)
                            )
                        }
                    }

                    val configureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
                        val p = pendingWidget
                        pendingWidget = null
                        // 配置页非 OK 也照样落盘（和一墨同款）：用户可能只是按了返回，
                        // 但 id 已经绑上、provider 也已经在画了，收掉反而留个死格。
                        if (p != null) vm.commitWidget(p.provider, p.appWidgetId, p.span.spanX, p.span.spanY)
                    }
                    val bindLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                        val p = pendingWidget
                        pendingWidget = null
                        when {
                            p == null -> Unit
                            // 用户拒绝授权：id 已经分配但没绑上，不回收会一直占着宿主槽位
                            result.resultCode != Activity.RESULT_OK ->
                                PanelAppWidgetHost.get().release(p.appWidgetId)
                            else -> afterBound(p, configureLauncher)
                        }
                    }
                    fun pickWidget(info: AppWidgetProviderInfo) {
                        val component = info.provider ?: return
                        val host = PanelAppWidgetHost.get()
                        val appWidgetId = host.allocateAppWidgetId()
                        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                            showToast(R.string.grid_panel_widget_pick_failed)
                            return
                        }
                        val pending = PendingWidget(
                            appWidgetId = appWidgetId,
                            provider = component.flattenToString(),
                            span = widgetSpanFor(info, gridColumns, gridRows),
                        )
                        if (AppWidgetManager.getInstance(context)
                                .bindAppWidgetIdIfAllowed(appWidgetId, component)
                        ) {
                            afterBound(pending, configureLauncher)
                        } else {
                            // 宿主不能自绑（非桌面宿主常态）：走系统授权对话框
                            pendingWidget = pending
                            bindLauncher.launch(
                                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, component)
                            )
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
                            actionPanels = uiState.actionPanels,
                            gridPanels = uiState.gridPanels,
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
                                   // 必须带 ACTION_CREATE_SHORTCUT：创建器是按这个 action
                                    // 查出来的（AppQuery），裸包名启动会被创建器视为非法
                                    // 调用，直接 finish 取消（实测 150ms 返回 resultCode=0）。
                                    shortcutLauncher.launch(
                                        Intent(Intent.ACTION_CREATE_SHORTCUT).apply {
                                            setClassName(launcherInfo.packageName, launcherInfo.className)
                                        },
                                    )
                                } catch (_: Exception) { currentLauncherInfo = null }
                            },
                            showWidgetChip = gridPanelId.isNotBlank(),
                            widgetGroups = widgetGroups,
                            widgetSpanOf = { info -> widgetSpanFor(info, gridColumns, gridRows) },
                            onSelectWidget = ::pickWidget,
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
