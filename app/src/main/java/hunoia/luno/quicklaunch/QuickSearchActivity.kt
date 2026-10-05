package hunoia.luno.quicklaunch

import android.app.Activity
import android.content.ClipboardManager
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import hunoia.luno.BuildConfig
import hunoia.luno.R
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.MAX_CLIP_HISTORY
import hunoia.luno.quicklaunch.panel.QuickSearchContent
import hunoia.luno.quicklaunch.query.QuickSearchIndex
import hunoia.luno.quicklaunch.query.QuickSearchResult
import hunoia.luno.quicklaunch.query.longPressAction
import hunoia.luno.quicklaunch.query.resolvedUrl
import hunoia.luno.quicklaunch.query.toConfigAction
import hunoia.luno.service.DispatchClient
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

    /**
     * 剪贴板状态：窗口拿到焦点后读（取剪贴板要的是窗口焦点，onResume 时焦点
     * 通常还没到——之前首开无行就是读早了）。只记长度不记内容。
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
        clipState.value = runCatching {
            getSystemService(ClipboardManager::class.java)
                ?.primaryClip?.getItemAt(0)?.coerceToText(this)
                ?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        }.getOrNull().also {
            if (BuildConfig.DEBUG) Log.i("LunoQuickSearch", "clipLen=${it?.length}")
        }
        // 剪贴板历史：读到新文本才记（去重 cap 50），空态多行展示。
        // 已固定的不进历史（不占 50 名额；固定和历史互不交叉）。
        val clip = clipState.value
        if (clip != null && clip != lastClipRecorded) {
            lastClipRecorded = clip
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
                        // 计算器：直接复制结果，不进 dispatcher（toConfigAction 对 Calc 会抛错）。
                        if (result is QuickSearchResult.Calc) {
                            copyText(result.value)
                            requestClose()
                        } else {
                            recordQuery(confirmedQuery)
                            // 联系人：Activity 直调拨号 intent（toConfigAction 对 Contact 会抛错）。
                            if (result is QuickSearchResult.Contact) {
                                runCatching {
                                    startActivity(
                                        Intent(
                                            Intent.ACTION_DIAL,
                                            android.net.Uri.parse("tel:${result.number}"),
                                        ),
                                    )
                                }
                                finish()
                            } else if (result is QuickSearchResult.AudioTrack) {
                                // 音乐：默认播放器直接播，不弹选择器。
                                viewUri(result.uri, "audio/*")
                                requestClose()
                            } else if (result is QuickSearchResult.ImageVideo) {
                                viewUri(result.uri, result.mime)
                                requestClose()
                            } else if (result is QuickSearchResult.DocFile) {
                                viewUri(result.uri, result.mime)
                                requestClose()
                            } else if (result is QuickSearchResult.CalendarEvent) {
                                recordQuery(confirmedQuery)
                                openCalendarEvent(result.eventId)
                                requestClose()
                            } else if (result is QuickSearchResult.SmsMsg) {
                                recordQuery(confirmedQuery)
                                // 短信点：打开跟该号码的会话（正文不动，只看）。
                                if (result.address.isNotBlank()) openSmsThread(result.address)
                                requestClose()
                            } else if (result is QuickSearchResult.CallLogEntry) {
                                recordQuery(confirmedQuery)
                                openDial(result.number)
                                requestClose()
                            } else if (result is QuickSearchResult.PhoneToggle) {
                                recordQuery(confirmedQuery)
                                handleToggle(result.kind)
                                // 直切类（手电/DND）自己 requestClose，跳设置页的不关面板
                                //（从设置回来还要继续搜）。
                            } else if (result is QuickSearchResult.App && result.isDisabled) {
                                // 小清理：已停用应用点选不再走启动（行为未定义），进详情页让用户手动启用。
                                recordQuery(confirmedQuery)
                                if (DispatchClient.dispatch(
                                        result.longPressAction()
                                            ?: result.toConfigAction(),
                                    )
                                ) {
                                    requestClose()
                                } else {
                                    showToast(R.string.dispatch_service_not_ready)
                                }
                            } else {
                                if (DispatchClient.dispatch(result.toConfigAction())) {
                                    requestClose()
                                } else {
                                    showToast(R.string.dispatch_service_not_ready)
                                }
                            }
                        }
                    },
                    onLongPress = { result ->
                        // 联系人长按：直调短信 intent。
                        if (result is QuickSearchResult.Contact) {
                            runCatching {
                                startActivity(
                                    Intent(
                                        Intent.ACTION_SENDTO,
                                        android.net.Uri.parse("smsto:${result.number}"),
                                    ),
                                )
                            }
                            finish()
                        } else if (result is QuickSearchResult.ImageVideo) {
                            // 图片/视频/文档/音乐长按：分享文件。
                            shareUri(result.uri, result.mime)
                            requestClose()
                        } else if (result is QuickSearchResult.AudioTrack) {
                            shareUri(result.uri, "audio/*")
                            requestClose()
                        } else if (result is QuickSearchResult.DocFile) {
                            shareUri(result.uri, result.mime)
                            requestClose()
                        } else if (result is QuickSearchResult.CalendarEvent) {
                            // 日历长按：复制“标题 时间 地点”。
                            copyText(
                                listOf(result.title_, result.timeText, result.location)
                                    .filter { it.isNotEmpty() }
                                    .joinToString(" "),
                            )
                            requestClose()
                        } else if (result is QuickSearchResult.SmsMsg) {
                            // 短信长按：复制正文。
                            copyText(result.body)
                            requestClose()
                        } else if (result is QuickSearchResult.CallLogEntry) {
                            // 通话长按：给对方发短信。
                            if (result.number.isNotBlank()) openSmsThread(result.number)
                            requestClose()
                        } else if (result is QuickSearchResult.ClipEntry) {
                            // 剪贴长按：写回系统剪贴板（已复制 toast），和日历/短信长按复制同套路。
                            copyText(result.text)
                            requestClose()
                        } else {
                            // 长按：应用类进详情页，网址/引擎行复制链接，其余无语义。
                            val detail = result.longPressAction()
                            if (detail != null) {
                                if (DispatchClient.dispatch(detail)) {
                                    requestClose()
                                } else {
                                    showToast(R.string.dispatch_service_not_ready)
                                }
                            } else {
                                when (result) {
                                    is QuickSearchResult.Url -> {
                                        copyText(result.url)
                                        requestClose()
                                    }
                                    is QuickSearchResult.EngineSearch -> {
                                        copyText(result.resolvedUrl())
                                        requestClose()
                                    }
                                    else -> Unit
                                }
                            }
                        }
                    },
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask 重复触发：面板已在前台，保留当前内容，不做任何事。
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "QuickSearchActivity onNewIntent taskId=$taskId")
    }

    override fun onDestroy() {
        if (BuildConfig.DEBUG) Log.i("LunoQuickSearch", "destroyed=${System.currentTimeMillis()}")
        super.onDestroy()
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
