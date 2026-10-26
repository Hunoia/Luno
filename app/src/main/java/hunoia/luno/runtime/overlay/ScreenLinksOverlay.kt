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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
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
import hunoia.luno.link.LinkDispatcher
import hunoia.luno.link.LinkUrls
import hunoia.luno.runtime.GestureHost
import hunoia.luno.ui.theme.SideGestureTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 屏幕上认出的一条链接：[targetLabel] 为命中规则的目标应用（未命中为 null）。 */
private data class ScreenLink(
    val url: String,
    val host: String,
    val targetLabel: String?,
)

/**
 * 识屏取链（动作 `accessibility.screenLinks`）：扫当前应用窗口的无障碍节点文本，抽出全部链接
 * 列成一张卡片，点一条就按链接分发规则直达目标应用，长按或尾部图标复制。
 *
 * 走无障碍节点树而不是 OCR：链接本身是文本，节点里的 `text`/`contentDescription` 是原文，
 * 比截图识别准得多，也不需要截屏权限。窗口自身是 TYPE_ACCESSIBILITY_OVERLAY，扫描时按包名
 * 跳过自家窗口，因此不会把自己列进结果。
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

        /** 首轮没扫到时的补扫间隔（最多 3 轮）：面板退场瞬间窗口树可能刚恢复。 */
        const val SCAN_RETRY_MS = 200L

        /** 面板退场后的额外等待（同 ScreenPick：Dismissed 之后窗口树可能还在收尾）。 */
        const val PANEL_POST_SETTLE_MS = 80L
    }

    private var overlayView: View? = null

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

    /** Compose 态：扫描中转圈，扫完换成列表。 */
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
        session++
        val current = session
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
        host.coroutineScope.launch { scan(current) }
        return true
    }

    fun close() {
        session++
        val view = overlayView ?: return
        overlayView = null
        scanning = true
        links = emptyList()
        runCatching { host.context.windowManager().removeView(view) }
    }

    private suspend fun scan(current: Int) {
        val context = host.context
        val settings = runCatching { ConfigProvider.getActionSettings().linkDispatch }
            .getOrDefault(LinkDispatchSettings())
        if (!settings.enabled) {
            if (current == session) {
                showToast(R.string.screen_links_disabled)
                close()
            }
            return
        }
        val hadPanels = openPanelCount.get() > 0
        awaitPanelsDismissed()
        if (hadPanels) delay(PANEL_POST_SETTLE_MS)
        delay(SCAN_SETTLE_MS)
        var urls = emptyList<Pair<String, Int>>()
        for (attempt in 0 until 3) {
            if (current != session) return
            urls = scanNow(context)
            if (urls.isNotEmpty()) break
            if (attempt < 2) delay(SCAN_RETRY_MS)
        }
        if (current != session) return
        if (urls.isEmpty()) {
            showToast(R.string.screen_links_empty)
            close()
            return
        }
        val resolved = withContext(Dispatchers.IO) {
            // 按屏幕上的视觉顺序（y 从上到下）展示，遍历顺序对用户没意义。
            urls.sortedBy { it.second }.map { (url, _) ->
                val rule = LinkUrls.firstMatch(url, settings.rules)
                ScreenLink(
                    url = url,
                    host = LinkUrls.hostOf(url) ?: url,
                    targetLabel = rule?.let {
                        LinkDispatcher.resolvedTargetOf(context, it, url)?.label
                    },
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

    private fun onPick(link: ScreenLink) {
        val current = session
        val context = host.context
        host.coroutineScope.launch {
            val settings = runCatching { ConfigProvider.getActionSettings().linkDispatch }
                .getOrDefault(LinkDispatchSettings())
            val dispatched = settings.enabled && LinkDispatcher.dispatch(context, link.url, settings)
            if (!dispatched && settings.enabled) {
                LinkDispatcher.fallback(context, link.url, settings.missAction)
            }
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

    private suspend fun scanNow(context: android.content.Context): List<Pair<String, Int>> =
        withContext(Dispatchers.Default) {
            runCatching { ScreenLinkScanner.scan(host.accessibilityService, context.packageName) }
                .getOrDefault(emptyList())
        }

    private fun onCopy(link: ScreenLink) {
        val label = runCatching { host.context.getString(R.string.app_name) }.getOrDefault("Luno")
        val copied = copyText(host.context, label, link.url)
        showToast(if (copied) R.string.screen_links_copied else R.string.screen_links_unavailable)
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
    fun scan(service: AccessibilityService, ownPackage: String): List<Pair<String, Int>> {
        val windows = service.windows ?: return emptyList()
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
        Log.w(LOG_TAG, "screen links: windows=$scannedWindows nodes=$visited urls=${found.size}")
        return found.map { it.key to it.value }
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
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            node.text?.toString()?.let { addUrls(it, bounds.top, out) }
            node.contentDescription?.toString()?.let { addUrls(it, bounds.top, out) }
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
                .padding(bottom = with(androidx.compose.ui.platform.LocalDensity.current) {
                    bottomInsetPx.toDp()
                }),
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
                            modifier = Modifier.padding(end = 8.dp),
                        )
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
                                onLongClick = { onCopy(link) },
                                onCopy = { onCopy(link) },
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
    onLongClick: () -> Unit,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        IconButton(onClick = onCopy) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.screen_links_copy),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
