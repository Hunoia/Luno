package hunoia.luno.quicklaunch

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import hunoia.luno.BuildConfig
import hunoia.luno.R
import hunoia.luno.bridge.feedback.showToast
import hunoia.luno.quicklaunch.query.QuickSearchIndex
import hunoia.luno.quicklaunch.query.QuickSearchResult
import hunoia.luno.quicklaunch.query.toConfigAction
import hunoia.luno.service.SideGestureService
import hunoia.luno.ui.component.SelectableListItem
import hunoia.luno.ui.component.TextIcon
import hunoia.luno.ui.component.segmentedShape
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SegmentedGap
import hunoia.luno.ui.theme.SideGestureTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 全屏透明搜索面板（Activity 版）。
 *
 * 之前是 Service 里 addView 的裸悬浮窗：无 decor、无 insets，内容直接画到透明状态栏
 * 背后 + 全屏 blur，在部分 ROM 上把状态栏图标糊到看不见。改透明 Activity 后状态栏、
 * 通知弹窗、键盘 insets 全部交还系统（对照 gesture_evo 的 QuickSearchActivity 实现）。
 *
 * 约定：singleTask + noHistory，重复触发走 onNewIntent（内容保留，不重置）。
 */
class QuickSearchActivity : ComponentActivity() {

    private val index by lazy { QuickSearchIndex(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // 全屏背景模糊：Activity 在状态栏下层（BASE_APPLICATION），blur 只糊窗口背后的层，
        // 状态栏不受影响（悬浮窗时代整窗 blur 糊掉状态栏是因为窗口在它上层，已成历史）。
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.attributes.blurBehindRadius = 80
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "QuickSearchActivity onCreate taskId=$taskId")
        // 数据准备放后台：首次输入不再卡主线程，且每次打开都刷新应用/快捷方式。
        lifecycleScope.launch(Dispatchers.IO) {
            index.ensureLoaded()
            index.refreshApps()
        }
        setContent {
            SideGestureTheme {
                QuickSearchContent(
                    index = index,
                    onClose = { finish() },
                    onResult = { result ->
                        SideGestureService.current?.dispatchAction(result.toConfigAction())
                        finish()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask 重复触发：面板已在前台，保留当前内容，不做任何事。
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "QuickSearchActivity onNewIntent taskId=$taskId")
    }

    companion object {
        /**
         * 从手势/Service 上下文启动搜索面板。
         *
         * BAL（后台启动限制）：无障碍服务不在豁免名单，但应用有常驻可见窗口（手势触钮）
         * 时 BAL_ALLOW_VISIBLE_WINDOW 放行——悬浮触钮点开 Activity 是标准路径。
         * 真机若被拦：logcat 会有 "Background activity start ... blocked"，届时再处理。
         */
        fun start(context: Context) {
            val intent = Intent(context, QuickSearchActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val ok = runCatching { context.startActivity(intent) }.isSuccess
            if (!ok) {
                Log.w("LunoLauncher", "QuickSearchActivity start failed")
                showToast(R.string.quick_search_start_failed)
            }
        }
    }
}

@Composable
private fun QuickSearchContent(
    index: QuickSearchIndex,
    onClose: () -> Unit,
    onResult: (QuickSearchResult) -> Unit,
) {
    val libraryFlow = remember { hunoia.luno.config.ConfigProvider.newActionLibrarySettings }
    val libraryState by libraryFlow.collectAsState(initial = hunoia.luno.action.model.NewActionLibrarySettings())
    val entries = libraryState.entries
    val launcherFlow = remember { hunoia.luno.config.ConfigProvider.quickAppLauncherSettings }
    val launcherSettings by launcherFlow.collectAsState(initial = hunoia.luno.config.model.QuickAppLauncherSettings())
    val searchSettingsFlow = remember { hunoia.luno.config.ConfigProvider.quickSearchSettings }
    val searchSettings by searchSettingsFlow.collectAsState(initial = hunoia.luno.config.model.QuickSearchSettings())

    var query by remember { mutableStateOf(TextFieldValue("")) }
    val results = remember(query.text, entries, launcherSettings, searchSettings) {
        if (query.text.isBlank()) {
            emptyList()
        } else {
            index.search(query.text, entries, launcherSettings, searchSettings)
        }
    }

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
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
        // statusBarsPadding：内容从状态栏下方开始，不垫在透明状态栏图标后面
        //（白卡压白图标会导致图标看不清，跟 blur 无关，必须避让）。
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
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

            // 回车确认 = 右侧 Check：跳过兜底（除非只剩兜底），打最佳真实结果。
            val confirmFirst: () -> Unit = {
                (results.firstOrNull { it !is QuickSearchResult.EngineSearch }
                    ?: results.firstOrNull())?.let(onResult)
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
                            // 注意列表是反转的：firstOrNull 是视觉底部（离胶囊最近），与显示顺序相反。
                            onClick = confirmFirst,
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
                // 默认英文键盘 + 回车即确认（否则回车只是收键盘）。
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { confirmFirst() }),
                colors = OutlinedTextFieldDefaults.colors(
                    // 半透明底色：透出窗口背后的系统模糊，不支持的设备直接呈现半透明。
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
        // 半透明底色：透出窗口背后的系统模糊，不支持的设备直接呈现半透明。
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
private fun textIcon(result: QuickSearchResult): TextIcon? = when (result) {
    is QuickSearchResult.LibraryEntry -> result.icon as? TextIcon
    is QuickSearchResult.ActionDef -> result.icon as? TextIcon
    else -> null
}
