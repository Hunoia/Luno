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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import hunoia.luno.BuildConfig
import hunoia.luno.R
import hunoia.luno.bridge.system.PackageChangeReceiver
import hunoia.luno.bridge.system.readClipboardText
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.action.model.StoredAction
import hunoia.luno.core.Events
import hunoia.luno.core.PanelDismissedEvent
import hunoia.luno.core.PanelShownEvent
import hunoia.luno.config.model.MAX_CLIP_HISTORY
import hunoia.luno.config.model.MAX_CLIP_STORE_CHARS
import hunoia.luno.quicklaunch.panel.QuickSearchContent
import hunoia.luno.quicklaunch.query.AppSearch
import hunoia.luno.quicklaunch.query.QuickSearchIndexProvider
import hunoia.luno.quicklaunch.query.SearchAction
import hunoia.luno.quicklaunch.query.SearchResult
import hunoia.luno.quicklaunch.query.SOURCE_CALC
import hunoia.luno.service.DispatchClient
import hunoia.luno.ui.theme.SideGestureTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 全屏透明搜索面板：状态栏、通知弹窗、键盘 insets 全部交还系统，
 * 全屏背景模糊只糊窗口背后，不影响状态栏。
 * 约定：singleTask + noHistory，重复触发走 onNewIntent（内容保留，不重置）。
 */
class QuickSearchActivity : ComponentActivity() {

    private val index by lazy { QuickSearchIndexProvider.get(applicationContext) }

    /** 面板开着时装卸应用：立即重扫，结果经 indexVersion 触发重搜。 */
    private val onPackageChanged: () -> Unit = {
        lifecycleScope.launch(Dispatchers.IO) { runCatching { index.invalidateApps() } }
    }

    override fun onStart() {
        super.onStart()
        PackageChangeReceiver.register(this, onPackageChanged)
    }

    override fun onStop() {
        PackageChangeReceiver.unregister(this, onPackageChanged)
        super.onStop()
    }

    /**
     * 剪贴板状态：窗口拿到焦点后读（取剪贴板要窗口焦点，onResume 时焦点通常还没到）。
     * 只记长度不记内容。
     */
    private val clipState = mutableStateOf<String?>(null)
    /** 上次已记入剪贴板历史的文本：内存去重，焦点反复进出不重复写 DataStore。 */
    private var lastClipRecorded: String? = null
    /** 锁屏态：锁屏时空页/搜索都不展剪贴内容（KeyguardManager 实时读，随焦点一起更新）。 */
    private val lockedState = mutableStateOf(false)

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (BuildConfig.DEBUG) Log.i("LunoQuickSearch", "focus=$hasFocus")
        lockedState.value = runCatching {
            getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true
        }.getOrDefault(false)
        if (!hasFocus) return
        // singleTask 复用：requestClose 里摘的 blur 在这里补回，
        // 否则 200ms 内重开的面板没糊。新鲜 onCreate 走默认值，不受影响。
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        clipState.value = readClipboardText(this)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            .also {
            if (BuildConfig.DEBUG) Log.i("LunoQuickSearch", "clipLen=${it?.length}")
        }
        // 剪贴板历史：读到新文本才记（去重 cap 50），空态多行展示。
        // 已固定的不进历史（不占 50 名额；固定和历史互不交叉）。
        val clip = clipState.value
        if (clip != null && clip != lastClipRecorded) {
            lastClipRecorded = clip
            // 超长剪贴（富文本 clip 转出的 MB 级 base64 等）不进历史：匹配只看前 64 字，
            // 记着纯占 DataStore。固定路径不受限（见 togglePin，保留全文供长按复制）。
            if (clip.length > MAX_CLIP_STORE_CHARS) {
                if (BuildConfig.DEBUG) Log.i("LunoQuickSearch", "clipTooLong=${clip.length}")
            } else {
                lifecycleScope.launch {
                    runCatching {
                        val pinned = hunoia.luno.config.ConfigProvider.getQuickSearchSettings().pinnedClips
                        if (clip in pinned) return@runCatching
                        hunoia.luno.config.ConfigProvider.updateQuickSearchSettings {
                            it.copy(
                                clipHistory = (listOf(clip) + it.clipHistory)
                                    .distinct()
                                    .take(MAX_CLIP_HISTORY),
                            )
                        }
                    }
                }
            }
        }
    }

    /** 手电状态（CameraManager 无 getter，翻一次记一次，供开关行副标题用）。 */
    internal val torchState = mutableStateOf(false)
    internal val flashCameraId: String? by lazy {
        runCatching {
            val cm = getSystemService(android.hardware.camera2.CameraManager::class.java) ?: return@runCatching null
            cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }

    /** 手电直切：无 CAMERA 运行时权限先要（ Manifest 已有 CAMERA），要到再翻。 */
    internal val torchPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) flipTorch()
        }

    internal val closingState = mutableStateOf(false)

    /** 图片搜索：PickImage 先记住目标包，选图回来再执行 ShareImage。 */
    private var pendingImagePackage: String? = null
    private val imageSearchLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        val pkg = pendingImagePackage
        pendingImagePackage = null
        if (uri != null && !pkg.isNullOrBlank()) {
            executeAction(SearchAction.ShareImage(uri.toString(), pkg))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // 全屏背景模糊：Activity 在状态栏下层（BASE_APPLICATION），blur 只糊窗口背后的层，
        // 状态栏不受影响。
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.attributes.blurBehindRadius = 80
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "QuickSearchActivity onCreate taskId=$taskId")
        if (!shownEventPosted) {
            shownEventPosted = true
            Events.post(PanelShownEvent())
        }
        // 数据准备放后台：首次输入不再卡主线程，且每次打开都刷新应用/快捷方式。
        lifecycleScope.launch(Dispatchers.IO) {
            index.ensureLoaded()
            runCatching { index.refreshApps() }
        }
        // 返回键不直接 finish：走退场动画。
        onBackPressedDispatcher.addCallback(
            this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = requestClose()
            },
        )
        setContent {
            SideGestureTheme {
                QuickSearchContent(
                    index = index,
                    clipboardText = clipState.value,
                    torchOn = torchState.value,
                    closing = closingState.value,
                    locked = lockedState.value,
                    onClose = { requestClose() },
                    onExitFinished = { finishAfterExit() },
                    onResult = { result, confirmedQuery ->
                        handleResultTap(result, confirmedQuery)
                    },
                    onAction = { action, confirmedQuery ->
                        if (action !is SearchAction.SetQuery && action !is SearchAction.ShowEngines) {
                            recordQuery(confirmedQuery)
                        }
                        executeAction(action)
                    },
                    onTogglePin = { query, key -> toggleResultPin(query, key) },
                    onHideResult = { key, title -> hideSearchResult(key, title) },
                    onSaveDraft = { query -> saveSearchDraft(query) },
                    onOpenAppSettings = {
                        // 权限被勾不再询问时跳本应用详情页，手动开。
                        runCatching {
                            startActivity(
                                Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.parse("package:$packageName"),
                                ),
                            )
                        }
                    },
                )
            }
        }
    }


    /** 结果点按统一入口：计算器复制、其余记历史后执行通用动作。 */
    private fun handleResultTap(result: SearchResult, confirmedQuery: String) {
        if (result.source != SOURCE_CALC) recordQuery(confirmedQuery)
        executeAction(result.primary)
    }

    private fun executeAction(action: SearchAction) {
        when (action) {
            is SearchAction.Run -> dispatchOrToast(action.action)
            is SearchAction.Open -> {
                val ok = runCatching { startActivity(action.intent) }.isSuccess
                if (!ok) showToast(R.string.launch_no_handler)
                requestClose()
            }
            is SearchAction.OpenUrl -> {
                if (!launchSearchUrl(this, action)) showToast(R.string.launch_no_handler)
                requestClose()
            }
            is SearchAction.PickImage -> {
                pendingImagePackage = action.packageName
                runCatching { imageSearchLauncher.launch("image/*") }.onFailure {
                    pendingImagePackage = null
                    showToast(R.string.launch_no_handler)
                }
            }
            is SearchAction.ShareImage -> {
                val ok = runCatching {
                    val uri = android.net.Uri.parse(action.uri)
                    val mime = contentResolver.getType(uri) ?: "image/*"
                    startActivity(
                        Intent(Intent.ACTION_SEND).apply {
                            type = mime
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            if (action.packageName.isNotBlank()) setPackage(action.packageName)
                        },
                    )
                }.isSuccess
                if (!ok) showToast(R.string.launch_no_handler)
                requestClose()
            }
            is SearchAction.Copy -> {
                copyText(action.text)
                requestClose()
            }
            is SearchAction.Share -> shareUri(action.uri, action.mime).also { requestClose() }
            // 由 QuickSearchContent 直接回填/开引擎 Sheet；万一走到这里不处理。
            is SearchAction.SetQuery -> Unit
            is SearchAction.ShowEngines -> Unit
            is SearchAction.SystemToggle -> handleToggle(action.kind)
        }
    }

    /** 分发 + 未就绪提示 + 成功关面板：点按与长按的 App/详情页共用。 */
    private fun dispatchOrToast(action: StoredAction) {
        if (DispatchClient.dispatch(action)) {
            requestClose()
        } else {
            showToast(R.string.dispatch_service_not_ready)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask 重复触发：面板已在前台，保留当前内容；只重扫包列表，避免
        // 上次打开期间装卸的应用在复用时仍按旧索引搜不到。
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "QuickSearchActivity onNewIntent taskId=$taskId")
        lifecycleScope.launch(Dispatchers.IO) { runCatching { index.invalidateApps() } }
    }

    override fun onDestroy() {
        if (BuildConfig.DEBUG) Log.i("LunoQuickSearch", "destroyed=${System.currentTimeMillis()}")
        if (shownEventPosted) {
            shownEventPosted = false
            Events.post(PanelDismissedEvent())
        }
        // 索引本身留在进程里复用；拼音索引和联系人/媒体等大表是内存大头，
        // 关面板后压回小容量，下次打开按需重建。
        if (isFinishing) {
            AppSearch.trimPinyinCache()
            index.trimSessionSources()
        }
        super.onDestroy()
    }

    /** Shown/Dismissed 事件配对闩锁（onNewIntent 复用实例时不重复发 Shown）。 */
    private var shownEventPosted = false

    companion object {
        /**
         * 从手势/Service 上下文启动搜索面板。
         * BAL（后台启动限制）：无障碍服务不在豁免名单，但应用有常驻可见窗口（手势触钮）
         * 时 BAL_ALLOW_VISIBLE_WINDOW 放行——悬浮触钮点开 Activity 是标准路径。
         * 真机若被拦：logcat 会有 "Background activity start ... blocked"。
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
