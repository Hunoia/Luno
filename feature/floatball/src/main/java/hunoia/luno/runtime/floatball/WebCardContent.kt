package hunoia.luno.runtime.floatball

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import hunoia.luno.bridge.system.ImageOutput
import hunoia.luno.bridge.system.normalizeOpenAppOrUrl
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.clipboard.ClipboardCenter
import hunoia.luno.core.LunoLog as Log
import hunoia.luno.core.LOG_TAG
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.PressTextButton
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.feedback.LunoIconButton
import hunoia.luno.ui.component.feedback.LunoLinearProgress
import hunoia.luno.ui.component.menu.LunoDropdownMenu
import hunoia.luno.ui.component.menu.LunoDropdownMenuItem
import hunoia.luno.ui.theme.LargeShape
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** 图片字节上限：网页图超出直接放弃（readBytes 全量进内存，防 MB 级大图 OOM）。 */
private const val MaxImageBytes = 30L * 1024 * 1024

/** 缩放手柄直径：与悬浮卡角缩放同款 36dp。 */
private val ResizeHandleSize = 36.dp

/**
 * 搜索小窗内容（宿主 = [WebCardWindow] 的 overlay 窗口）：WebView + 导航条 + 卡内长按菜单。
 * 几何全部归窗口所有，内容只回传拖拽 / 缩放的像素增量；窗口即卡片边界，内容 fillMaxSize。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WebCardContent(
    url: String,
    onMoveBy: (Float, Float) -> Unit,
    onResizeBy: (Float, Float) -> Unit,
    onResizeCommit: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()

    var loadProgress by remember { mutableIntStateOf(0) }
    var pageHost by remember { mutableStateOf("") }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var menuImageUrl by remember { mutableStateOf<String?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    // 长按图片的卡内锚点与按点位置（px，相对网页区左上角）：setOnTouchListener 顺路记录。
    var menuAnchor by remember { mutableStateOf(Offset.Zero) }
    var lastTouch by remember { mutableStateOf(Offset.Zero) }

    // 局部函数先声明再被 remember 闭包引用：菜单落点先于 WebView 创建。
    fun showImageMenu(imageUrl: String, x: Float, y: Float) {
        menuAnchor = Offset(x, y)
        menuImageUrl = imageUrl
    }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            // _blank 目标一律落在当前视图（不支持多窗就没有 onCreateWindow 语义）。
            settings.setSupportMultipleWindows(false)
            // 载入前不闪白底：圆角外露的是透明窗而非白块。
            setBackgroundColor(Color.Transparent.toArgb())
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val scheme = request.url.scheme?.lowercase()
                    if (scheme == "http" || scheme == "https") return false
                    // intent:/tel:/market: 等非网页 scheme 交系统；无处理方就地吞掉，留在卡内。
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                    return true
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    // 新一次主框架加载开始即视为健康；错误态只在 onReceivedError 里重新点亮。
                    loadFailed = false
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    // 只把主框架失败当整页错误；图片/脚本加载失败不换掉整卡。
                    if (request.isForMainFrame) loadFailed = true
                }

                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                    pageHost = url?.toUri()?.host.orEmpty()
                    canGoBack = view.canGoBack()
                    canGoForward = view.canGoForward()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    loadProgress = newProgress
                }
            }
            setDownloadListener { downloadUrl, _, _, mime, _ ->
                val uri = downloadUrl.toUri()
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    if (mime.isNullOrBlank()) setData(uri) else setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(intent) }
                    .onFailure { showToast(hunoia.luno.core.bridge.R.string.launch_no_handler) }
            }
            setOnTouchListener { _, event ->
                // 只记录按点位置（卡内长按菜单的锚点），不消费——WebView 原生交互照常。
                lastTouch = Offset(event.x, event.y)
                false
            }
            setOnLongClickListener { v ->
                val view = v as WebView
                when (view.hitTestResult.type) {
                    WebView.HitTestResult.IMAGE_TYPE -> {
                        val extra = view.hitTestResult.extra
                        if (extra != null) showImageMenu(extra, lastTouch.x, lastTouch.y)
                        extra != null
                    }
                    WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                        // extra 是链接地址不是图片：requestFocusNodeHref 异步拿真实图 URL；
                        // 锚点取长按那一刻的位置（回调回来时手指已抬起）。
                        val px = lastTouch.x
                        val py = lastTouch.y
                        val msg = Message.obtain(object : Handler(Looper.getMainLooper()) {
                            override fun handleMessage(m: Message) {
                                m.data?.getString("src")?.let { showImageMenu(it, px, py) }
                            }
                        })
                        view.requestFocusNodeHref(msg)
                        true
                    }
                    // 非图片长按返回 false，交回 WebView 原生行为（文本选择等）。
                    else -> false
                }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { webView.destroy() }
    }
    LaunchedEffect(url) {
        val target = normalizeOpenAppOrUrl(url) ?: url.toUri()
        webView.loadUrl(target.toString())
    }

    fun copyLink(imageUrl: String) {
        ClipboardCenter.writeTextImmediate(imageUrl, "webcard")
        showToast(hunoia.luno.design.R.string.copied)
        menuImageUrl = null
    }

    fun saveImage(imageUrl: String) {
        menuImageUrl = null
        // WebView 方法有调用线程校验（getSettings 在 IO 线程直接抛 "All WebView methods must
        // be called on the same thread"）——UA / Referer / Cookie 必须在主线程先取齐，
        // IO 协程只拿纯参数。
        val userAgent = webView.settings.userAgentString
        val referer = webView.url
        val cookies = CookieManager.getInstance().getCookie(imageUrl)
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val conn = URL(imageUrl).openConnection() as HttpURLConnection
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 15_000
                    conn.instanceFollowRedirects = true
                    // 带上 WebView 的 UA + 页面 Referer + 该域 Cookie：部分图床三者都校验。
                    conn.setRequestProperty("User-Agent", userAgent)
                    referer?.takeIf { it.isNotBlank() }?.let { conn.setRequestProperty("Referer", it) }
                    cookies?.takeIf { it.isNotBlank() }?.let { conn.setRequestProperty("Cookie", it) }
                    if (conn.contentLengthLong > MaxImageBytes) return@runCatching false
                    val bytes = conn.inputStream.use { it.readBytes() }
                    if (bytes.isEmpty() || bytes.size > MaxImageBytes) return@runCatching false
                    val path = imageUrl.substringBefore('#').substringBefore('?')
                    val mime = conn.contentType?.substringBefore(';')?.trim()?.takeIf { it.isNotBlank() }
                        ?: guessImageMime(path)
                        ?: "image/jpeg"
                    ImageOutput.saveToGallery(appContext, bytes, mime) != null
                }.onFailure {
                    Log.w(LOG_TAG, "web card save image failed: ${it.javaClass.simpleName}: ${it.message}")
                }.getOrDefault(false)
            }
            showToast(if (ok) R.string.web_card_saved else R.string.web_card_save_failed)
        }
    }

    /** 当前页逃生舱：Google 登录墙等 WebView 受限场景跳系统浏览器。 */
    fun openInBrowser() {
        val current = webView.url
        if (current.isNullOrBlank()) return
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, current.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { showToast(hunoia.luno.core.bridge.R.string.launch_no_handler) }
    }

    fun copyPageLink() {
        val current = webView.url
        if (current.isNullOrBlank()) return
        ClipboardCenter.writeTextImmediate(current, "webcard")
        showToast(hunoia.luno.design.R.string.copied)
    }

    fun sharePage() {
        val current = webView.url
        if (current.isNullOrBlank()) return
        runCatching {
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, current)
                    },
                    null,
                ),
            )
        }.onFailure { showToast(hunoia.luno.core.bridge.R.string.launch_no_handler) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = LargeShape,
        // 窗口 = 卡片边界（activityFloat 卡同款口径）：阴影会被窗口边界裁掉，只留描边。
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    // 顶栏即拖柄 = 移动窗口本身。slop 前不消费指针——原地点按钮不受影响；
                    // 过 slop 才消费并转成移窗。
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onMoveBy(dragAmount.x, dragAmount.y)
                        }
                    }
                    .padding(horizontal = 4.dp),
            ) {
                LunoIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(hunoia.luno.design.R.string.go_back),
                    enabled = canGoBack,
                    onClick = { webView.goBack() },
                )
                LunoIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.web_card_forward),
                    enabled = canGoForward,
                    onClick = { webView.goForward() },
                )
                LunoIconButton(
                    icon = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.web_card_refresh),
                    onClick = { webView.reload() },
                )
                Text(
                    text = pageHost,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Box {
                    LunoIconButton(
                        icon = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.web_card_more),
                        onClick = { menuExpanded = true },
                    )
                    LunoDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        LunoDropdownMenuItem(
                            text = stringResource(R.string.web_card_open_in_browser),
                            onClick = {
                                menuExpanded = false
                                openInBrowser()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                )
                            },
                        )
                        LunoDropdownMenuItem(
                            text = stringResource(R.string.web_card_copy_page_link),
                            onClick = {
                                menuExpanded = false
                                copyPageLink()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Link, contentDescription = null)
                            },
                        )
                        LunoDropdownMenuItem(
                            text = stringResource(R.string.web_card_share_page),
                            onClick = {
                                menuExpanded = false
                                sharePage()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Share, contentDescription = null)
                            },
                        )
                    }
                }
                LunoIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(hunoia.luno.design.R.string.close),
                    onClick = onClose,
                )
            }
            if (loadProgress in 1..99) {
                LunoLinearProgress(
                    progress = { loadProgress / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    trackColor = Color.Transparent,
                )
            }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
                if (loadFailed) {
                    // 主框架加载失败：换 Luno 错误态（WebView 实例保留，重试不丢回退历史）。
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        EmptyState(
                            message = stringResource(R.string.web_card_error),
                            icon = Icons.Default.CloudOff,
                            action = {
                                PressTextButton(onClick = {
                                    loadFailed = false
                                    webView.reload()
                                }) {
                                    Text(stringResource(R.string.web_card_retry))
                                }
                            },
                        )
                        PressTextButton(onClick = { openInBrowser() }) {
                            Text(stringResource(R.string.web_card_open_in_browser))
                        }
                    }
                }
                menuImageUrl?.let { imageUrl ->
                    // 卡内长按菜单：锚在按点位置，靠右/下时往回钳（弹窗可越窗显示，只是难看）。
                    Box(
                        Modifier.offset {
                            IntOffset(
                                menuAnchor.x.coerceIn(0f, (maxWidth - 200.dp).toPx()).roundToInt(),
                                menuAnchor.y.coerceIn(0f, (maxHeight - 130.dp).toPx()).roundToInt(),
                            )
                        },
                    ) {
                        LunoDropdownMenu(
                            expanded = true,
                            onDismissRequest = { menuImageUrl = null },
                        ) {
                            LunoDropdownMenuItem(
                                text = stringResource(R.string.web_card_save_image),
                                onClick = { saveImage(imageUrl) },
                                leadingIcon = {
                                    Icon(Icons.Default.Save, contentDescription = null)
                                },
                            )
                            LunoDropdownMenuItem(
                                text = stringResource(R.string.web_card_copy_link),
                                onClick = { copyLink(imageUrl) },
                                leadingIcon = {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                                },
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(ResizeHandleSize)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            CircleShape,
                        )
                        .pointerInput(Unit) {
                            // 增量改写窗口尺寸：从当前窗口尺寸累加，不重置快照——跳变根因
                            // （onDragStart 用过期闭包快照重置尺寸）从结构上移除。
                            detectDragGestures(
                                onDrag = { change, amount ->
                                    change.consume()
                                    onResizeBy(amount.x, amount.y)
                                },
                                onDragEnd = { onResizeCommit() },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.OpenInFull,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 从 URL 扩展名猜 MIME；猜不出返回 null（调用方再兜底）。 */
private fun guessImageMime(path: String): String? = when (path.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    "bmp" -> "image/bmp"
    else -> null
}
