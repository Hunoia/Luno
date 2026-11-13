package hunoia.luno.gridpanel.internal

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.core.AppContext
import hunoia.luno.core.domain.AppInfo
import hunoia.luno.core.domain.AppLaunchAction
import hunoia.luno.core.domain.DispatchOrigin
import hunoia.luno.gridpanel.LocalGridPanelAnchor
import hunoia.luno.gridpanel.LocalGridPanelClose
import hunoia.luno.quickaccess.launch.DisabledPackageEnabler
import hunoia.luno.quickaccess.panel.QuickLaunchBody
import hunoia.luno.quickaccess.panel.QuickLaunchContentState
import hunoia.luno.runtime.surface.SurfaceDispatch

private const val TAG = "QuickLaunchWidget"

/**
 * 整页快速启动：候选行 + 搜索键盘 + 应用网格，仅作为单功能面板内容。
 *
 * 与快速启动单功能面板共用 [QuickLaunchContentState] / [QuickLaunchBody]：
 * 搜索、键盘、候选、启动语义只有一份。宿主差异在这里收口：
 * - 关闭走面板的 `LocalGridPanelClose`（启动成功即收面板）；
 * - 小窗锚点取面板打开时的触点；
 * - 面板编辑态用一层 Initial-pass 消费者挡住内容点击，避免拖动/缩放时误启动。
 */
@Composable
fun QuickLaunchPanelCell(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val closePanel = LocalGridPanelClose.current
    val panelAnchor = LocalGridPanelAnchor.current
    val settings by hunoia.luno.domain.search.SearchCenter.launcher
        .collectAsState(initial = null)
    val initialSettings = settings ?: return

    val enabler = remember {
        DisabledPackageEnabler(
            context = context,
            scopeProvider = { AppContext.applicationScope!! },
            log = { message -> hunoia.luno.core.LunoLog.i(TAG, message) },
        )
    }
    DisposableEffect(enabler) {
        onDispose { enabler.release() }
    }

    val state = remember {
        QuickLaunchContentState(
            context = context,
            coroutineScope = scope,
            initialSettings = initialSettings,
            requestEnableDisabledPackage = { packageName, callback ->
                enabler.request(packageName) { success ->
                    if (!success) {
                        val suspended = hunoia.luno.quickaccess.query.DisabledAppQuery
                            .isSuspended(context, packageName)
                        showToast(
                            if (suspended) {
                                hunoia.luno.capability.launch.R.string.app_suspended_cannot_enable
                            } else {
                                hunoia.luno.capability.launch.R.string.enable_disabled_app_failed
                            },
                        )
                    }
                    callback(success)
                }
            },
            onLaunch = { app: AppInfo, miniWindow: Boolean ->
                SurfaceDispatch.dispatch(
                    action = AppLaunchAction.stored(
                        packageName = app.packageName,
                        className = app.className,
                        miniWindow = miniWindow,
                        userId = app.userId,
                    ),
                    origin = DispatchOrigin.PANEL,
                    launchAnchor = panelAnchor,
                ) { launched ->
                    if (!launched) {
                        showToast(hunoia.luno.quickaccess.R.string.quick_launcher_start_failed)
                    }
                }
                // 已提交即视为「已接管」：真实失败由 onSettled 的平台 Toast 反馈。
                true
            },
            onLaunched = { closePanel() },
        )
    }
    DisposableEffect(state) {
        onDispose { state.dispose() }
    }

    val localeTag = LocalConfiguration.current.locales[0].toLanguageTag()
    LaunchedEffect(localeTag) { state.reloadAppsIfLocaleChanged() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .then(if (enabled) Modifier else Modifier.blockAllPointerInput()),
    ) {
        QuickLaunchBody(
            state = state,
            panelWidth = maxWidth,
            panelHeight = maxHeight,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 编辑态挡板：内容只负责显示，点击/滚动全部交给面板的缩放与换功能手势。 */
private fun Modifier.blockAllPointerInput(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { it.consume() }
        }
    }
}
