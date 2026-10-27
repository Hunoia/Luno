package hunoia.luno.runtime.overlay

import android.annotation.SuppressLint
import android.accessibilityservice.AccessibilityService
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.bridge.system.copyText
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.bridge.ui.applyOverlayViewTreeOwners
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.LinkDispatchSettings
import hunoia.luno.core.Events
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.PanelDismissedEvent
import hunoia.luno.core.PanelShownEvent
import hunoia.luno.link.Links
import hunoia.luno.link.LinkUrls
import hunoia.luno.quicklaunch.icon.SearchIconCache
import hunoia.luno.runtime.GestureHost
import hunoia.luno.ui.theme.SideGestureTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 屏幕上认出的一条链接；[targetPackage] 为命中规则的目标应用（未命中为 null）。 */
private data class ScreenLink(
    val url: String,
    val host: String,
    val targetPackage: String?,
    val targetLabel: String?,
)

/**
 * 识屏取链（动作 `accessibility.screenLinks`）：扫当前应用窗口的无障碍节点文本，抽出全部链接
 * 列成一张卡片，点一条就按链接分发规则直达目标应用，长按或尾部菜单可以复制 / 浏览器打开 /
 * 分享 / 用该域名新建规则。
 *
 * 走无障碍节点树而不是 OCR：链接本身是文本，节点里的 `text`/`contentDescription` 是原文，
 * 比截图识别准得多，也不需要截屏权限。窗口自身是 TYPE_ACCESSIBILITY_OVERLAY，扫描时按包名
 * 跳过自家窗口，因此不会把自己列进结果。
 *
 * 与「链接分发」总开关解耦：开关只管分发，识屏、复制、列表始终可用；关掉时点条目走全局
 * 未命中策略（浏览器/分享/选择应用），而不是直接报错关层。
 */
internal class ScreenLinksOverlay(private val host: GestureHost) {

    private companion object {
        /** 无障碍 overlay 不带这个标志时触摸会被系统拦掉（Android 12 起）。 */
        const val TRUSTED_OVERLAY_FLAG = 0x300000

        /** 抬手后停一拍再扫：避免把临上屏的过渡帧算进去。 */
        const val SCAN_SETTLE_MS = 80L

        /**
         * 等面板真正退场：动作面板在自家 a11y overlay 里要淡出 400ms 才发 Dismissed，
         * 面板还在时底下应用的窗口在无障碍里拿不到内容（用户实测：从面板触发就 0 条）。
         * 与 ScreenPick 同一套等待口径。
         */
        const val PANEL_SETTLE_MAX_MS = 900L
        const val PANEL_SETTLE_POLL_MS = 50L

        /** 首轮没扫到时的补扫间隔（最多 3 轮）：只在窗口/节点数为 0 时补扫。 */
        const val SCAN_RETRY_MS = 200L

        /** 面板退场后的额外等待（同 ScreenPick：Dismissed 之后窗口树可能还在收尾）。 */
        const val PANEL_POST_SETTLE_MS = 80L
    }

    private var overlayView: View? = null

    /** 扫描协程：连续触发/关层时取消上一轮，避免多个扫描并发抢 Binder。 */
    private var scanJob: Job? = null

    /** 在屏面板计数（动作面板/网格面板等自家 overlay）：全部退场前不扫。 */
    private val openPanelCount = java.util.concurrent.atomic.AtomicInteger(0)

    private val panelShownSubscriber: (PanelShownEvent) -> Unit = {
        openPanelCount.incrementAndGet()
    }
    private val panelDismissedSubscriber: (PanelDismissedEvent) -> Unit = {
        openPanelCount.updateAndGet { count -> (count - 1).coerceAtLeast(0) }
    }

    init {
        Events.subscribe(PanelShownEvent::class, panelShownSubscriber)
        Events.subscribe(PanelDismissedEvent::class, panelDismissedSubscriber)
    }

    /** 会话号：异步扫描回来时若已关层/换会话就丢弃结果。 */
    private var session = 0

    /** Compose 态：扫描中转圈，扫完换成列表；空结果保留卡片给「重新扫描」。 */
    private var scanning by mutableStateOf(true)
    private var links by mutableStateOf<List<ScreenLink>>(emptyList())

    fun release() {
        Events.unsubscribe(PanelShownEvent::class, panelShownSubscriber)
        Events.unsubscribe(PanelDismissedEvent::class, panelDismissedSubscriber)
        close()
    }

    /** 返回是否成功挂窗（锁屏/被系统拒绝返回 false，动作层据此判失败）。 */
    fun show(): Boolean {
        if (overlayView != null) return true
        val keyguard = host.context.getSystemService(android.app.KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked == true) return false
        scanning = true
        links = emptyList()
        val lp = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.RGBA_8888
            // 不挂 LAYOUT_NO_LIMITS：本层不需要像素级坐标对齐（那是 ScreenPick 的需求），
            // 铺满"应用可见区"才能让底部卡片避开状态栏/导航栏，滚到底也看得见。
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or TRUSTED_OVERLAY_FLAG
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            x = 0
            y = 0
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            @SuppressLint("RtlHardcoded")
            gravity = Gravity.LEFT or Gravity.TOP
        }
        val composeView = ComposeView(host.context).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            applyOverlayViewTreeOwners(host)
            setContent {
                SideGestureTheme {
                    ScreenLinksCard(
                        bottomInsetPx = bottomInsetPx(),
                        scanning = scanning,
                        links = links,
                        onPick = ::onPick,
                        onCopy = ::onCopy,
                        onOpenBrowser = ::onOpenBrowser,
                        onShare = ::onShare,
                        onCreateRule = ::onCreateRule,
                        onCopyAll = ::onCopyAll,
                        onShareAll = ::onShareAll,
                        onRescan = ::rescan,
                        onDismiss = ::close,
                    )
                }
            }
        }
        if (!host.context.windowManager().safeAddView(composeView, lp)) {
            Log.w(LOG_TAG, "screen links: addView failed")
            showToast(R.string.screen_links_unavailable)
            return false
        }
        overlayView = composeView
        startScan()
        return true
    }

    fun close() {
        session++
        scanJob?.cancel()
        scanJob = null
        val view = overlayView ?: return
        overlayView = null
        scanning = true
        links = emptyList()
        runCatching { host.context.windowManager().removeView(view) }
    }

    /** 重新扫描：复用当前卡片，不重建窗口（空结果时用户滚完页面可直接重试）。 */
    private fun rescan() {
        if (overlayView == null) return
        scanning = true
        links = emptyList()
        startScan()
    }

    private fun startScan() {
        session++
        val current = session
        scanJob?.cancel()
        scanJob = host.coroutineScope.launch { scan(current) }
    }

    private suspend fun scan(current: Int) {
        val context = host.context
        val settings = runCatching { ConfigProvider.getActionSettings().linkDispatch }
            .getOrDefault(LinkDispatchSettings())
        val hadPanels = openPanelCount.get() > 0
        awaitPanelsDismissed()
        if (hadPanels) delay(PANEL_POST_SETTLE_MS)
        delay(SCAN_SETTLE_MS)
        var result = ScanResult.Empty
        for (attempt in 0 until 3) {
            if (current != session) return
            result = scanNow()
            if (current != session) return
            if (result.urls.isNotEmpty()) break
            // 只有「窗口/节点都没拿到」才值得补扫；屏幕上确实没链接时，重扫只是白跑
            // 3 轮 × 最多 4000 个 Binder 调用。
            if (result.windows > 0 && result.nodes > 0) break
            if (attempt < 2) delay(SCAN_RETRY_MS)
        }
        if (current != session) return
        val rules = if (settings.enabled) settings.rules else emptyList()
        val resolved = withContext(Dispatchers.IO) {
            // 按屏幕上的视觉顺序（y 从上到下）展示，遍历顺序对用户没意义。
            result.urls.sortedBy { it.second }.map { (url, _) ->
                val rule = LinkUrls.firstMatch(url, rules)
                val target = rule?.let { Links.resolvedTargetOf(context, it, url) }
                ScreenLink(
                    url = url,
                    host = LinkUrls.hostOf(url) ?: url,
                    targetPackage = target?.packageName,
                    targetLabel = target?.label,
                )
            }
        }
        if (current != session) return
        links = resolved
        scanning = false
    }

    /**
     * 卡片离屏幕下沿的留白：手势导航下 `navigationBars` inset 是 0（导航条浮在内容上），
     * 直接贴底会让最后一行压在手势条上。有 inset 用它，没有就给一个最小留白。
     */
    private fun bottomInsetPx(): Int {
        val systemInset = runCatching {
            host.context.windowManager().currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars())
                .bottom
        }.getOrDefault(0)
        return maxOf(systemInset, DensityProvider.dp2px(16f))
    }

    private suspend fun currentSettings(): LinkDispatchSettings =
        runCatching { ConfigProvider.getActionSettings().linkDispatch }
            .getOrDefault(LinkDispatchSettings())

    private fun onPick(link: ScreenLink) {
        val current = session
        val context = host.context
        host.coroutineScope.launch {
            val settings = currentSettings()
            val rule = if (settings.enabled) LinkUrls.firstMatch(link.url, settings.rules) else null
            val dispatched = settings.enabled && Links.dispatch(context, link.url, settings)
            if (!dispatched) Links.fallbackFor(context, link.url, rule, settings)
            if (current == session) close()
        }
    }

    /** 面板还在（淡出中）时底下应用的窗口内容取不到，等它退场再扫。 */
    private suspend fun awaitPanelsDismissed() {
        val deadline = android.os.SystemClock.uptimeMillis() + PANEL_SETTLE_MAX_MS
        while (openPanelCount.get() > 0 && android.os.SystemClock.uptimeMillis() < deadline) {
            delay(PANEL_SETTLE_POLL_MS)
        }
    }

    private suspend fun scanNow(): ScanResult = withContext(Dispatchers.Default) {
        runCatching { ScreenLinkScanner.scan(host.accessibilityService, host.context.packageName) }
            .getOrDefault(ScanResult.Empty)
    }

    private fun onCopy(link: ScreenLink) {
        val copied = copyText(host.context, appLabel(), link.url)
        showToast(if (copied) R.string.screen_links_copied else R.string.screen_links_unavailable)
    }

    private fun onOpenBrowser(link: ScreenLink) {
        Links.openInBrowser(host.context, link.url)
        close()
    }

    private fun onShare(link: ScreenLink) {
        Links.share(host.context, link.url)
        close()
    }

    private fun onCreateRule(link: ScreenLink) {
        // 用注册域名（不是完整 URL）预填：同域名下的不同路径应该共用一条规则。
        val ruleHost = runCatching { java.net.URI(link.url).host }.getOrNull()
            ?.removePrefix("www.")
            ?: link.host.removePrefix("www.")
        Links.createRule(host.context, ruleHost)
        close()
    }

    private fun onCopyAll() {
        if (links.isEmpty()) return
        val copied = copyText(host.context, appLabel(), links.joinToString("\n") { it.url })
        showToast(if (copied) R.string.screen_links_copied else R.string.screen_links_unavailable)
    }

    private fun onShareAll() {
        if (links.isEmpty()) return
        Links.shareAll(host.context, links.map { it.url })
        close()
    }

    private fun appLabel(): String =
        runCatching { host.context.getString(R.string.app_name) }.getOrDefault("Luno")
}

/** 一次扫描的结果：URL + 屏幕 y，附带窗口/节点计数（决定要不要补扫）。 */
private data class ScanResult(
    val urls: List<Pair<String, Int>>,
    val windows: Int,
    val nodes: Int,
) {
    companion object {
        val Empty = ScanResult(emptyList(), windows = 0, nodes = 0)
    }
}

/** 无障碍节点树里的链接扫描（纯遍历，无 UI）。 */
private object ScreenLinkScanner {

    /**
     * 扫描预算：聊天/长列表这类页面节点树又深又宽，600 个节点会在摸到屏幕中段前就用完
     * （微信一屏消息就能超过）。上限仍保留，防病态节点树拖死后台线程。
     */
    private const val MaxNodes = 4_000
    private const val MaxDepth = 60

    /** 返回「URL → 首次出现的屏幕 y」：便于按视觉顺序展示，而不是按遍历顺序。 */
    fun scan(service: AccessibilityService, ownPackage: String): ScanResult {
        val windows = service.windows ?: return ScanResult.Empty
        val found = LinkedHashMap<String, Int>()
        var visited = 0
        var scannedWindows = 0
        for (window in windows) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val root = window.root ?: continue
            if (root.packageName?.toString() == ownPackage) continue
            scannedWindows++
            visited = collect(root, found, depth = 0, visited = visited)
            if (visited >= MaxNodes) break
        }
        // WARN 级：部分 ROM（HyperOS）会把第三方应用的 INFO 日志吞掉，排查时只留 I 看不见。
        Log.w(
            LOG_TAG,
            "screen links: windows=$scannedWindows nodes=$visited urls=${found.size}",
        )
        return ScanResult(found.map { it.key to it.value }, scannedWindows, visited)
    }

    /** DFS 收集节点文本里的链接；返回累计访问的节点数，超预算提前停。 */
    private fun collect(
        node: AccessibilityNodeInfo,
        out: MutableMap<String, Int>,
        depth: Int,
        visited: Int,
    ): Int {
        var count = visited
        if (count >= MaxNodes || depth > MaxDepth) return count
        count++
        if (node.isVisibleToUser && !node.isPassword) {
            val text = node.text?.toString()
            val description = node.contentDescription?.toString()
            if (!text.isNullOrEmpty() || !description.isNullOrEmpty()) {
                // 只有真的带文本的节点才去取 bounds：容器节点占绝大多数，逐个
                // getBoundsInScreen 是纯浪费的跨进程调用。
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                text?.let { addUrls(it, bounds.top, out) }
                description?.let { addUrls(it, bounds.top, out) }
            }
        }
        if (count < MaxNodes) {
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                count = collect(child, out, depth + 1, count)
                if (count >= MaxNodes) break
            }
        }
        return count
    }

    /** 一条文本里可能有多条链接；同一链接只记首次出现的 y。 */
    private fun addUrls(text: String, top: Int, out: MutableMap<String, Int>) {
        if (text.isEmpty()) return
        // 识屏要比剪贴板宽松：屏幕上常显示「github.com/xxx」这种不带协议的写法。
        LinkUrls.extractUrls(text, allowBareDomains = true).forEach { url ->
            if (!out.containsKey(url)) out[url] = top
        }
    }
}

@Composable
private fun ScreenLinksCard(
    bottomInsetPx: Int,
    scanning: Boolean,
    links: List<ScreenLink>,
    onPick: (ScreenLink) -> Unit,
    onCopy: (ScreenLink) -> Unit,
    onOpenBrowser: (ScreenLink) -> Unit,
    onShare: (ScreenLink) -> Unit,
    onCreateRule: (ScreenLink) -> Unit,
    onCopyAll: () -> Unit,
    onShareAll: () -> Unit,
    onRescan: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f))
            // 点空白处关闭：本层不可获焦，返回键收不到，只能靠点。
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = with(LocalDensity.current) { bottomInsetPx.toDp() }),
            // 卡片自己吃掉点击，不要穿透到后面的"点空白关闭"。
            onClick = {},
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (scanning) {
                            stringResource(R.string.screen_links_scanning)
                        } else {
                            stringResource(R.string.screen_links_title)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (scanning) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.screen_links_count, links.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                        if (links.isNotEmpty()) {
                            IconButton(onClick = onCopyAll) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = stringResource(
                                        R.string.screen_links_copy_all,
                                    ),
                                )
                            }
                            IconButton(onClick = onShareAll) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = stringResource(
                                        R.string.screen_links_share_all,
                                    ),
                                )
                            }
                        }
                        IconButton(onClick = onRescan) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.screen_links_rescan),
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.screen_links_close),
                        )
                    }
                }
                if (links.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            // 上限防 20 条链接把卡片铺满整屏（剩下的滚动看）。
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        links.forEach { link ->
                            ScreenLinkRow(
                                link = link,
                                onClick = { onPick(link) },
                                onCopy = { onCopy(link) },
                                onOpenBrowser = { onOpenBrowser(link) },
                                onShare = { onShare(link) },
                                onCreateRule = { onCreateRule(link) },
                            )
                        }
                    }
                } else if (!scanning) {
                    // 空结果不再 toast + 关层：用户可能只是还没滚到有链接的位置。
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.screen_links_empty),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.screen_links_empty_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onRescan) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = stringResource(R.string.screen_links_rescan),
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScreenLinkRow(
    link: ScreenLink,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    onOpenBrowser: () -> Unit,
    onShare: () -> Unit,
    onCreateRule: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuOpen = true },
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TargetIcon(packageName = link.targetPackage)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = link.host,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (link.targetLabel != null) {
                    stringResource(R.string.screen_links_target, link.targetLabel)
                } else {
                    stringResource(R.string.screen_links_no_target)
                } + " · " + link.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.screen_links_more),
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.screen_links_copy)) },
                    onClick = { menuOpen = false; onCopy() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.screen_links_open_browser)) },
                    onClick = { menuOpen = false; onOpenBrowser() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.screen_links_share)) },
                    onClick = { menuOpen = false; onShare() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.screen_links_create_rule)) },
                    onClick = { menuOpen = false; onCreateRule() },
                )
            }
        }
    }
}

/** 命中规则时显示目标应用图标；未命中显示链接图标占位。 */
@Composable
private fun TargetIcon(packageName: String?) {
    val size = 28.dp
    val sizePx = with(LocalDensity.current) { size.toPx().toInt() }
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName) {
        val pkg = packageName ?: return@produceState
        value = SearchIconCache.get(pkg, sizePx) ?: withContext(Dispatchers.IO) {
            runCatching {
                SearchIconCache.ensureAll(context.applicationContext, listOf(pkg), sizePx)
                SearchIconCache.get(pkg, sizePx)
            }.getOrNull()
        }
    }
    if (icon != null) {
        Image(
            bitmap = icon!!,
            contentDescription = null,
            modifier = Modifier.size(size),
        )
    } else {
        Icon(
            imageVector = Icons.Default.Link,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}
