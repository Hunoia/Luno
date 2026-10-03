package hunoia.luno.runtime.overlay

import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import hunoia.luno.BuildConfig
import hunoia.luno.R
import hunoia.luno.bridge.feedback.showToastDelay
import hunoia.luno.bridge.intent.gotoOverlayPermissionSettings
import hunoia.luno.bridge.window.applyOverlayViewTreeOwners
import hunoia.luno.bridge.window.windowManager
import hunoia.luno.config.ConfigProvider
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.query.QuickSearchIndex
import hunoia.luno.quicklaunch.query.QuickSearchResult
import hunoia.luno.quicklaunch.query.toConfigAction
import hunoia.luno.runtime.GestureHost
import hunoia.luno.ui.component.SelectableListItem
import hunoia.luno.ui.component.TextIcon
import hunoia.luno.ui.component.segmentedShape
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SegmentedGap
import hunoia.luno.ui.theme.SideGestureTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class QuickSearchOverlay(private val host: GestureHost) {

    @Volatile private var overlayView: View? = null
    @Volatile private var shown = false
    // state 化：常驻 ComposeView 在重开时必须重组，否则 remember/LaunchedEffect 收不到新 session
    private var sessionId by mutableIntStateOf(0)
    private val index by lazy { QuickSearchIndex(host.context) }

    fun show() {
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "QuickSearchOverlay show")
        if (shown) return
        // 悬浮窗层级（APPLICATION_OVERLAY）在状态栏下层，但需要用户授予
        // SYSTEM_ALERT_WINDOW。未授权时 addView 会抛 BadTokenException，
        // 这里提前拦截：toast 提示 2s 后跳系统授权页。
        if (!Settings.canDrawOverlays(host.context)) {
            if (BuildConfig.DEBUG) Log.w("LunoLauncher", "QuickSearchOverlay show blocked: overlay permission missing")
            showToastDelay(R.string.quick_search_requires_overlay) {
                host.context.gotoOverlayPermissionSettings()
            }
            return
        }
        shown = true
        sessionId++
        // 数据准备放后台：首次输入不再卡主线程，且每次打开都刷新应用/快捷方式。
        host.coroutineScope.launch(Dispatchers.IO) {
            index.ensureLoaded()
            index.refreshApps()
        }
        val wm = host.context.windowManager()
        if (overlayView == null) {
            val view = createComposeView()
            val lp = createLayoutParams()
            if (!wm.safeAddView(view, lp)) {
                shown = false
                return
            }
            overlayView = view
        } else {
            expandWindow()
        }
    }

    fun close() {
        if (!shown) return
        shown = false
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "QuickSearchOverlay close")
        collapseWindow()
    }

    fun release() {
        shown = false
        val view = overlayView
        if (view != null) {
            host.context.windowManager().safeRemoveViewImmediate(view)
        }
        overlayView = null
    }

    private fun expandWindow() {
        val view = overlayView ?: return
        val lp = createLayoutParams()
        host.context.windowManager().safeUpdateViewLayout(view, lp)
    }

    private fun collapseWindow() {
        val view = overlayView ?: return
        val lp = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            format = PixelFormat.RGBA_8888
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            width = 1
            height = 1
            gravity = Gravity.START or Gravity.TOP
        }
        host.context.windowManager().safeUpdateViewLayout(view, lp)
    }

    private fun createLayoutParams() = WindowManager.LayoutParams().apply {
        // 悬浮窗层级：在应用之上、状态栏/锁屏之下。需要 SYSTEM_ALERT_WINDOW 权限，
        // 未授权由 show() 守卫拦截（见上），不会走到 addView。
        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        format = PixelFormat.RGBA_8888
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.MATCH_PARENT
        flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        // 系统级背景模糊半径（px）。不支持的设备静默忽略，半透明底色即为降级外观。
        blurBehindRadius = 80
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        gravity = Gravity.START or Gravity.TOP
    }

    private fun createComposeView(): View =
        androidx.compose.ui.platform.ComposeView(host.context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            applyOverlayViewTreeOwners(host)
            setContent {
                SideGestureTheme {
                    QuickSearchContent(
                        sessionId = sessionId,
                        index = index,
                        onClose = { close() },
                        onResult = { result ->
                            host.dispatchAction(result.toConfigAction())
                            close()
                        },
                    )
                }
            }
        }
}

@Composable
private fun QuickSearchContent(
    sessionId: Int,
    index: QuickSearchIndex,
    onClose: () -> Unit,
    onResult: (QuickSearchResult) -> Unit,
) {
    val libraryFlow = remember { ConfigProvider.newActionLibrarySettings }
    val libraryState by libraryFlow.collectAsState(initial = hunoia.luno.action.model.NewActionLibrarySettings())
    val entries = libraryState.entries
    val launcherFlow = remember { ConfigProvider.quickAppLauncherSettings }
    val launcherSettings by launcherFlow.collectAsState(initial = hunoia.luno.config.model.QuickAppLauncherSettings())
    val searchSettingsFlow = remember { ConfigProvider.quickSearchSettings }
    val searchSettings by searchSettingsFlow.collectAsState(initial = hunoia.luno.config.model.QuickSearchSettings())

    var query by remember { mutableStateOf(TextFieldValue("")) }
    val results = remember(query.text, entries, launcherSettings, searchSettings, sessionId) {
        if (query.text.isBlank()) {
            emptyList()
        } else {
            index.search(query.text, entries, launcherSettings, searchSettings)
        }
    }

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(sessionId) {
        // 重开保留上次内容并全选，打字即替换
        query = query.copy(selection = TextRange(0, query.text.length))
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (results.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = PageGutter),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 8.dp),
                    // 反转只管顺序不管小内容锚定，显式 Bottom 后单条目也必贴胶囊
                    verticalArrangement = Arrangement.spacedBy(SegmentedGap, Alignment.Bottom),
                    reverseLayout = true,
                ) {
                    groupResults(results).forEach { (titleRes, items) ->
                        itemsIndexed(items, key = { idx, item -> "$titleRes-$idx-${item.title}" }) { index, item ->
                            ResultRow(
                                result = item,
                                // 反转后视觉上下颠倒，shape 索引镜像后圆角仍落在组外端
                                shape = segmentedShape(items.size - 1 - index, items.size),
                                onClick = { onResult(item) },
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter, vertical = 8.dp)
                    .imePadding()
                    .focusRequester(focusRequester),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.text.isNotBlank()) {
                        androidx.compose.material3.IconButton(
                            onClick = { results.firstOrNull()?.let(onResult) },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                            )
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    // 半透明底色：支持 blur-behind 的设备透出底层模糊，
                    // 不支持的设备直接呈现为半透明（降级外观）。
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f),
                    focusedBorderColor = ComposeColor.Transparent,
                    unfocusedBorderColor = ComposeColor.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

private data class ResultGroup(@androidx.annotation.StringRes val titleRes: Int, val items: List<QuickSearchResult>)

private fun groupResults(results: List<QuickSearchResult>): List<ResultGroup> {
    val order = linkedMapOf<Int, MutableList<QuickSearchResult>>(
        R.string.quick_search_group_web to mutableListOf(),
        R.string.quick_search_group_actions to mutableListOf(),
        R.string.quick_search_group_library to mutableListOf(),
        R.string.quick_search_group_apps to mutableListOf(),
        R.string.quick_search_group_shortcuts to mutableListOf(),
        R.string.quick_search_group_settings to mutableListOf(),
    )
    for (r in results) {
        val key = when (r) {
            is QuickSearchResult.Url -> R.string.quick_search_group_web
            is QuickSearchResult.EngineSearch -> R.string.quick_search_group_web
            is QuickSearchResult.ActionDef -> R.string.quick_search_group_actions
            is QuickSearchResult.LibraryEntry -> R.string.quick_search_group_library
            is QuickSearchResult.App -> R.string.quick_search_group_apps
            is QuickSearchResult.Shortcut -> R.string.quick_search_group_shortcuts
            is QuickSearchResult.Settings -> R.string.quick_search_group_settings
        }
        order[key]!!.add(r)
    }
    return order.mapNotNull { (res, list) -> if (list.isNotEmpty()) ResultGroup(res, list) else null }
}

@Composable
private fun ResultRow(result: QuickSearchResult, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val subtitle = if (result is QuickSearchResult.App && result.isDisabled) {
        result.subtitle + " · " + stringResource(R.string.quick_search_app_disabled)
    } else {
        result.subtitle
    }
    SelectableListItem(
        title = result.title,
        subtitle = subtitle,
        selected = false,
        onSelect = { onClick() },
        shape = shape,
        iconModel = iconModel(result, context),
        icon = iconVector(result),
        textIcon = textIcon(result),
        verticalGap = 0.dp,
        horizontalGap = 0.dp,
        // 半透明底色：支持 blur-behind 的设备透出底层模糊，
        // 不支持的设备直接呈现为半透明（降级外观）。
        containerColor = colorScheme.surfaceBright.copy(alpha = 0.5f),
        selectedContainerColor = colorScheme.primaryContainer.copy(alpha = 0.5f),
    )
}

@Composable
private fun iconVector(result: QuickSearchResult): ImageVector? = when (result) {
    is QuickSearchResult.Url -> Icons.AutoMirrored.Filled.OpenInNew
    is QuickSearchResult.EngineSearch -> Icons.AutoMirrored.Filled.OpenInNew
    is QuickSearchResult.Settings -> Icons.Default.Search
    is QuickSearchResult.ActionDef -> result.icon as? ImageVector
    is QuickSearchResult.LibraryEntry -> result.icon as? ImageVector
    is QuickSearchResult.App -> null
    is QuickSearchResult.Shortcut -> null
}

@Composable
private fun iconModel(result: QuickSearchResult, context: android.content.Context): Any? = when (result) {
    is QuickSearchResult.App -> {
        val sizePx = with(LocalDensity.current) { 24.dp.toPx().toInt() }
        QuickLaunchFacade.appIconRequest(context, result.appInfo.packageName, sizePx)
    }
    is QuickSearchResult.Shortcut -> result.shortcut.iconBitmap
    else -> null
}

@Composable
private fun textIcon(result: QuickSearchResult): hunoia.luno.ui.component.TextIcon? = when (result) {
    is QuickSearchResult.LibraryEntry -> result.icon as? hunoia.luno.ui.component.TextIcon
    is QuickSearchResult.ActionDef -> result.icon as? hunoia.luno.ui.component.TextIcon
    else -> null
}
