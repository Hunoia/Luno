package hunoia.luno.gridpanel.internal

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import hunoia.luno.gridpanel.R
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.AppLaunchSource
import hunoia.luno.config.model.AppUsageStats
import hunoia.luno.config.model.QuickAppLauncherSettings
import hunoia.luno.config.model.miniWindowSettings
import hunoia.luno.gridpanel.LocalGridPanelClose
import hunoia.luno.quicklaunch.launch.AppLaunchCoordinator
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.panel.AppGridContent
import hunoia.luno.quicklaunch.query.AppListRepository
import hunoia.luno.quicklaunch.query.AppSearch
import hunoia.luno.ui.theme.AppItemPadding
import hunoia.luno.ui.theme.GridCellSpacing

/** 抽屉网格的内边距：格子只有面板的一小块，留 4dp 让图标别贴边。 */
private val DrawerPadding = 4.dp

/** 图标预取尺寸下限：太小的格子算出的 px 会让 Coil 拿到比实际显示更糊的图。 */
private const val MinIconPx = 24

/**
 * 应用抽屉页面部件：整块可滚动的全量应用网格，点按启动、长按按 `tapOpensMiniWindow`
 * 取反（与快速启动器同一语义）。
 *
 * 复用关系：
 * - 应用列表来自进程级共享仓 [AppListRepository]（与快速启动器同一份，只扫一次包）；
 * - 排序复用 `AppSearch.sortApps`，搜索词恒为空 —— 部件里没有键盘，永远全量按使用频率排；
 * - 网格复用 `AppGridContent`（快速启动器的纯网格内核，不含键盘/药丸/面板几何）。
 * 禁用应用：网格面板没有 a11y 的启用回调，点按静默忽略（与「不可用即不可点」一致）。
 */
@Composable
fun AppDrawerCell(
    modifier: Modifier = Modifier,
    spanX: Int = 4,
    spanY: Int = 4,
    /** 编辑态=false：内部滚动与点按全禁，事件让给层根拖拽/缩放。 */
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val closePanel = LocalGridPanelClose.current
    val appList by AppListRepository.state.collectAsState()
    val usageStats by hunoia.luno.config.ConfigProvider.appUsageStats.collectAsState(initial = AppUsageStats())
    val launcherSettings by hunoia.luno.config.ConfigProvider.quickAppLauncherSettings.collectAsState(initial = QuickAppLauncherSettings())
    val advanced by hunoia.luno.config.ConfigProvider.advancedSettings.collectAsState(initial = AdvancedSettings())
    val miniWindowSettings = remember(advanced) { advanced.miniWindowSettings() }

    // 引用计数：部件离开组合就退订；多个部件/快速启动器同时在线只维持一份列表。
    DisposableEffect(Unit) {
        AppListRepository.acquire()
        onDispose { AppListRepository.release() }
    }
    // 系统语言变了 label 要重扫（与快速启动器同一判断）。
    val localeTag = LocalConfiguration.current.locales[0].toLanguageTag()
    LaunchedEffect(localeTag) { AppListRepository.reloadIfLocaleChanged() }

    // 无搜索词：全量按使用频率排序（sortApps 的 tokens 传空）。
    val apps = remember(appList.apps, usageStats) {
        AppSearch.sortApps(appList.apps, usageStats, emptyList())
    }

    fun launch(app: AppInfo, miniWindow: Boolean) {
        // 直接走启动协调器：QuickLaunchFacade.launchApp 的 onLaunch 是需要宿主注入的
        // 回调，默认实现只返回 true 不真启动（部件里直接用它就变成「只关面板」）。
        val launched = AppLaunchCoordinator.launchAppInfo(
            context = context,
            appInfo = app,
            miniWindow = miniWindow,
            miniWindowSettings = miniWindowSettings,
            source = AppLaunchSource.GRID_PANEL,
        )
        if (launched) closePanel() else showToast(hunoia.luno.core.bridge.R.string.launch_failed)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val columns = spanX.coerceIn(2, 6)
        val cellWidth = (maxWidth - DrawerPadding * 2 - GridCellSpacing * (columns - 1)) / columns
        // AppItem 自己留 AppItemPadding，图标实际显示宽度要再扣掉两侧。
        val iconSizePx = with(LocalDensity.current) {
            (cellWidth - AppItemPadding * 2).toPx().toInt().coerceAtLeast(MinIconPx)
        }
        AppGridContent(
            apps = apps,
            disabledPkgs = appList.disabledPkgs,
            gridState = rememberLazyGridState(),
            iconSizePx = iconSizePx,
            gridColumns = columns,
            onClick = { app, isDisabled ->
                when {
                    !enabled -> Unit
                    // 禁用应用静默忽略会让人以为点坏了：给一句提示。
                    isDisabled -> showToast(R.string.app_drawer_disabled_app)
                    else -> launch(app, launcherSettings.tapOpensMiniWindow)
                }
            },
            onLongPress = { app, isDisabled ->
                when {
                    !enabled -> Unit
                    isDisabled -> showToast(R.string.app_drawer_disabled_app)
                    else -> launch(app, !launcherSettings.tapOpensMiniWindow)
                }
            },
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(DrawerPadding),
            userScrollEnabled = enabled,
        )
    }
}
