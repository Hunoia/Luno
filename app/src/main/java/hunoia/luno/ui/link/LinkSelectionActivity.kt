package hunoia.luno.ui.link

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.link.LinkDispatcher
import hunoia.luno.link.LinkExtras
import hunoia.luno.link.LinkUrls
import hunoia.luno.quicklaunch.icon.SearchIconCache
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.theme.RowIconSize
import hunoia.luno.ui.theme.SideGestureTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 链接选择/接管页，两种模式：
 * - **多链接**（[EXTRA_URLS]）：一次认出多条链接时列出每一条及其分发目标，点行进目标应用，
 *   长按进系统分享面板。
 * - **选应用**（[EXTRA_PICK_URL]）：未命中规则且回退策略为「选择应用」时，列出本机所有能打开
 *   该链接的应用。自己实现而不是 `Intent.createChooser`：系统选择器在「Luno 已被设为默认
 *   浏览器」时会把唯一的默认项排除掉，界面直接空白（HyperOS 实测），也无法排序/排除自身。
 */
class LinkSelectionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pickUrl = intent?.getStringExtra(LinkExtras.EXTRA_PICK_URL)
        val urls = intent?.getStringArrayListExtra(LinkExtras.EXTRA_URLS).orEmpty()
        when {
            pickUrl != null -> setContent {
                SideGestureTheme {
                    AppPickerContent(url = pickUrl, onDone = { finish() })
                }
            }
            urls.isNotEmpty() -> setContent {
                SideGestureTheme {
                    LinkSelectionContent(urls = urls, onDone = { finish() })
                }
            }
            else -> finish()
        }
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        /** 多链接模式；调用方可能是 Service，必须带 NEW_TASK。 */
        fun intent(context: Context, urls: List<String>): Intent =
            Intent(context, LinkSelectionActivity::class.java)
                .putStringArrayListExtra(LinkExtras.EXTRA_URLS, ArrayList(urls))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** 选应用模式：列出能打开该链接的应用。 */
        fun pickIntent(context: Context, url: String): Intent =
            Intent(context, LinkSelectionActivity::class.java)
                .putExtra(LinkExtras.EXTRA_PICK_URL, url)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

@Composable
private fun LinkSelectionContent(
    urls: List<String>,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by TuneConfig.action.collectAsStateWithLifecycle(
        initialValue = ActionSettings(),
    )
    val linkSettings = settings.linkDispatch
    var handled by remember { mutableStateOf(false) }

    fun pick(url: String) {
        if (handled) return
        handled = true
        val rule = LinkUrls.firstMatch(url, linkSettings.rules)
        scope.launch {
            // 规则表级分发：命中规则开了短链解析时，内部跟随后会重新匹配。
            val dispatched = rule != null && LinkDispatcher.dispatch(context, url, linkSettings)
            if (!dispatched) {
                LinkDispatcher.fallbackFor(context, url, rule, linkSettings)
            }
            onDone()
        }
    }

    fun share(url: String) {
        if (handled) return
        handled = true
        LinkDispatcher.share(context, url)
        onDone()
    }

    LunoScreen(
        title = stringResource(R.string.link_dispatch_select_title),
        onBack = onDone,
    ) {
        LunoGroup(
            subtitle = stringResource(R.string.link_dispatch_select_hint, urls.size),
        ) {
            urls.forEachIndexed { index, url ->
                val rule = remember(url, linkSettings) {
                    LinkUrls.firstMatch(url, linkSettings.rules)
                }
                val targetLabel by produceState(initialValue = null as String?, url, rule?.id) {
                    value = rule?.let { LinkDispatcher.resolvedTargetOf(context, it, url)?.label }
                }
                val host = remember(url) { LinkUrls.hostOf(url) ?: url }
                LunoSettingsRow(
                    title = host,
                    subtitle = if (targetLabel != null) {
                        stringResource(
                            R.string.link_dispatch_select_target,
                            targetLabel.orEmpty(),
                            url,
                        )
                    } else {
                        stringResource(R.string.link_dispatch_select_no_target, url)
                    },
                    shape = segmentedShape(index, urls.size),
                    onClick = { pick(url) },
                    onLongClick = { share(url) },
                )
            }
        }
    }
}

/** 能打开某个链接的应用（按包去重，保留系统解析顺序）。 */
private data class LinkHandler(
    val packageName: String,
    val label: String,
    val component: ComponentName,
)

@Composable
private fun AppPickerContent(
    url: String,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    var handled by remember { mutableStateOf(false) }
    val handlers by produceState(initialValue = emptyList<LinkHandler>(), url) {
        value = withContext(Dispatchers.IO) { queryHandlers(context, url) }
    }

    fun launch(handler: LinkHandler) {
        if (handled) return
        handled = true
        val launched = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .setComponent(handler.component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
        if (!launched) showToast(R.string.link_dispatch_open_failed)
        onDone()
    }

    fun share() {
        if (handled) return
        handled = true
        LinkDispatcher.share(context, url)
        onDone()
    }

    LunoScreen(
        title = stringResource(R.string.link_dispatch_pick_app_title),
        onBack = onDone,
    ) {
        LunoGroup(
            subtitle = if (handlers.isEmpty()) {
                stringResource(R.string.link_dispatch_pick_app_empty)
            } else {
                stringResource(R.string.link_dispatch_pick_app_hint)
            },
        ) {
            handlers.forEachIndexed { index, handler ->
                AppRow(
                    handler = handler,
                    shape = segmentedShape(index, handlers.size + 1),
                    onClick = { launch(handler) },
                )
            }
            LunoSettingsRow(
                title = stringResource(R.string.link_dispatch_share_action),
                shape = segmentedShape(handlers.size, handlers.size + 1),
                onClick = { share() },
            )
        }
    }
}

@Composable
private fun AppRow(
    handler: LinkHandler,
    shape: Shape,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { RowIconSize.toPx().toInt() }
    val icon by produceState<ImageBitmap?>(initialValue = null, handler.packageName) {
        val cached = SearchIconCache.get(handler.packageName, sizePx)
        value = cached ?: withContext(Dispatchers.IO) {
            runCatching {
                SearchIconCache.ensureAll(
                    context.applicationContext,
                    listOf(handler.packageName),
                    sizePx,
                )
                SearchIconCache.get(handler.packageName, sizePx)
            }.getOrNull()
        }
    }
    LunoSettingsRow(
        title = handler.label,
        subtitle = handler.packageName,
        iconBitmap = icon,
        shape = shape,
        onClick = onClick,
    )
}

/**
 * 本机能打开该链接的应用（排除自己，避免自我循环）。
 *
 * 必须带 [PackageManager.MATCH_ALL]：系统默认浏览器存在时，`queryIntentActivities`
 * 默认只回默认项（HyperOS 实测），拿到的列表会被「过滤掉自己」清空成空页。
 */
private fun queryHandlers(context: Context, url: String): List<LinkHandler> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    return runCatching {
        pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { resolveInfo ->
                LinkHandler(
                    packageName = resolveInfo.activityInfo.packageName,
                    label = resolveInfo.loadLabel(pm).toString(),
                    component = ComponentName(
                        resolveInfo.activityInfo.packageName,
                        resolveInfo.activityInfo.name,
                    ),
                )
            }
            .toList()
    }.getOrElse { emptyList() }
}
