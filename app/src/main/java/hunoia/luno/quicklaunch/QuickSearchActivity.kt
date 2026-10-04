package hunoia.luno.quicklaunch

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shortcut
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import hunoia.luno.BuildConfig
import hunoia.luno.R
import hunoia.luno.bridge.feedback.showToast
import hunoia.luno.config.model.BLANK_RECENT_LIMIT
import hunoia.luno.config.model.MAX_CLIP_HISTORY
import hunoia.luno.config.model.MAX_RECENT_QUERIES
import hunoia.luno.config.model.SRC_ACTION
import hunoia.luno.config.model.SRC_APP
import hunoia.luno.config.model.SRC_CALENDAR
import hunoia.luno.config.model.SRC_CALL
import hunoia.luno.config.model.SRC_CONTACT
import hunoia.luno.config.model.SRC_FILE
import hunoia.luno.config.model.SRC_MUSIC
import hunoia.luno.config.model.SRC_SETTINGS
import hunoia.luno.config.model.SRC_SHORTCUT
import hunoia.luno.config.model.SRC_SMS
import hunoia.luno.config.model.SRC_TOGGLE
import hunoia.luno.config.model.effectiveEngines
import hunoia.luno.config.model.effectiveTrigger
import hunoia.luno.quicklaunch.query.QuickSearchIndex
import hunoia.luno.quicklaunch.query.QuickSearchResult
import hunoia.luno.quicklaunch.query.longPressAction
import hunoia.luno.quicklaunch.query.resolvedUrl
import hunoia.luno.quicklaunch.query.toConfigAction
import hunoia.luno.ui.permission.rememberPermissionState
import hunoia.luno.ui.settings.search.sourceTitle
import hunoia.luno.service.SideGestureService
import hunoia.luno.ui.component.SelectableListItem
import hunoia.luno.ui.component.TextIcon
import hunoia.luno.ui.component.segmentedShape
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SegmentedGap
import hunoia.luno.ui.theme.SideGestureTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    /** 直拷文本 + 已复制 toast：计算器/网址/引擎长按共用。 */
    private fun copyText(text: String) {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
            ClipData.newPlainText("quicksearch", text),
        )
        showToast(R.string.quick_search_copied)
    }

    /**
     * 剪贴板状态：窗口拿到焦点后读（取剪贴板要的是窗口焦点，onResume 时焦点
     * 通常还没到——之前首开无行就是读早了）。只记长度不记内容。
     */
    private val clipState = mutableStateOf<String?>(null)
    /** 上次已记入剪贴板历史的文本：内存去重，焦点反复进出不重复写 DataStore。 */
    private var lastClipRecorded: String? = null

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (BuildConfig.DEBUG) Log.i("LunoQuickSearch", "focus=$hasFocus")
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
        // 剪贴板历史：读到新文本才记（去重 cap 10），空态多行展示。
        val clip = clipState.value
        if (clip != null && clip != lastClipRecorded) {
            lastClipRecorded = clip
            lifecycleScope.launch {
                runCatching {
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

    /** 确认搜索记历史：去重 cap 上限，供各结果分支共用。 */
    private fun recordQuery(text: String) {
        if (text.isBlank()) return
        lifecycleScope.launch {
            runCatching {
                hunoia.luno.config.ConfigProvider.updateQuickSearchSettings {
                    it.copy(
                        recentQueries = (listOf(text) + it.recentQueries)
                            .distinct()
                            .take(MAX_RECENT_QUERIES),
                    )
                }
            }
        }
    }

    /** P3：直开 uri（音乐/图片/视频/文档），无 handler 接住 toast。 */
    private fun viewUri(uri: String, mime: String) {
        val ok = runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(android.net.Uri.parse(uri), mime.ifBlank { "*/*" })
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
            )
        }.isSuccess
        if (!ok) showToast(R.string.quick_search_no_handler)
    }

    /** P3：分享文件（图片/视频/文档长按）。 */
    private fun shareUri(uri: String, mime: String) {
        val ok = runCatching {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = mime.ifBlank { "*/*" }
                        putExtra(Intent.EXTRA_STREAM, android.net.Uri.parse(uri))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    null,
                ),
            )
        }.isSuccess
        if (!ok) showToast(R.string.quick_search_no_handler)
    }

    /** 手电状态（CameraManager 无 getter，翻一次记一次，供开关行副标题用）。 */
    private val torchState = mutableStateOf(false)
    private val flashCameraId: String? by lazy {
        runCatching {
            val cm = getSystemService(android.hardware.camera2.CameraManager::class.java) ?: return@runCatching null
            cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }

    /** 手电直切：无 CAMERA 运行时权限先要（ Manifest 已有 CAMERA），要到再翻。 */
    private val torchPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) flipTorch()
        }

    private fun toggleTorch() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            torchPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            return
        }
        flipTorch()
    }

    private fun flipTorch() {
        val id = flashCameraId ?: return
        val next = !torchState.value
        val ok = runCatching {
            getSystemService(android.hardware.camera2.CameraManager::class.java)?.setTorchMode(id, next)
        }.isSuccess
        if (ok) torchState.value = next
    }

    /**
     * 快捷开关分发：能直切的切（手电/DND 已授权），不能直切的跳面板或设置页
     * （WiFi/蓝牙/定位/热点/DND 未授权——Android 10+ 起第三方本就切不动，不硬调私有 API）。
     */
    private fun handleToggle(kind: QuickSearchResult.ToggleKind) {
        when (kind) {
            QuickSearchResult.ToggleKind.WIFI -> runCatching {
                startActivity(
                    if (android.os.Build.VERSION.SDK_INT >= 29) {
                        Intent(android.provider.Settings.Panel.ACTION_WIFI)
                    } else {
                        Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
                    },
                )
            }
            QuickSearchResult.ToggleKind.BLUETOOTH -> runCatching {
                startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
            }
            QuickSearchResult.ToggleKind.TORCH -> toggleTorch()
            QuickSearchResult.ToggleKind.DND -> {
                val nm = getSystemService(android.app.NotificationManager::class.java)
                if (nm?.isNotificationPolicyAccessGranted == true) {
                    runCatching {
                        nm.setInterruptionFilter(
                            if (nm.currentInterruptionFilter ==
                                android.app.NotificationManager.INTERRUPTION_FILTER_NONE
                            ) {
                                android.app.NotificationManager.INTERRUPTION_FILTER_ALL
                            } else {
                                android.app.NotificationManager.INTERRUPTION_FILTER_NONE
                            },
                        )
                    }
                    requestClose()
                } else {
                    runCatching {
                        startActivity(
                            Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
                        )
                    }
                }
            }
            QuickSearchResult.ToggleKind.LOCATION -> runCatching {
                startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            QuickSearchResult.ToggleKind.HOTSPOT -> {
                val ok = runCatching {
                    startActivity(Intent("android.settings.TETHER_SETTINGS"))
                }.isSuccess
                if (!ok) {
                    runCatching {
                        startActivity(Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS))
                    }
                }
            }
        }
    }

    /** 通话记录/联系人点：只跳拨号盘不直拨（CALL_PHONE 太危险，不碰）。 */
    private fun openDial(number: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:$number")))
        }
    }

    /** 短信点：打开跟该号码的会话；通话记录长按/联系人长沿用此入口。 */
    private fun openSmsThread(address: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:$address")))
        }
    }

    /** 日历点：看事件详情（系统日历接）。 */
    private fun openCalendarEvent(eventId: Long) {
        val ok = runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    data = android.content.ContentUris.withAppendedId(
                        android.provider.CalendarContract.Events.CONTENT_URI,
                        eventId,
                    )
                },
            )
        }.isSuccess
        if (!ok) showToast(R.string.quick_search_no_handler)
    }

    /**
     * 退场统一入口：只置 closing 位。finish() 由退场动画播完后亲自交接
     * （见 content 的 onExitFinished），不设固定 delay——动画和计时是并发的，
     * 固定 delay 只会在播完后多晾一段纯 blur。
     * 返回键也走这里（见 onCreate 的 onBackPressedDispatcher 拦截）。
     */
    private val closingState = mutableStateOf(false)

    private fun requestClose() {
        if (closingState.value) return
        if (BuildConfig.DEBUG) Log.i("LunoQuickSearch", "closeStart=${System.currentTimeMillis()}")
        closingState.value = true
        // blur 提前摘（背景立刻清晰，剩下 390ms 晾的是透明窗 + 清晰底，尾巴隐形）。
        // 穿透触摸试过，关闭已经够快，不需要，加状态位反而多风险，已删。
        // 重开恢复 blur 见 onWindowFocusChanged。
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    }

    /** 退场动画播完后来调：拆窗 + 压住系统退场动画（0 = 禁用）。 */
    private fun finishAfterExit() {
        finish()
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

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
                                SideGestureService.current?.dispatchAction(
                                    result.longPressAction()
                                        ?: result.toConfigAction(),
                                )
                                requestClose()
                            } else {
                                SideGestureService.current?.dispatchAction(result.toConfigAction())
                                requestClose()
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
                        } else {
                            // 长按：应用类进详情页，网址/引擎行复制链接，其余无语义。
                            val detail = result.longPressAction()
                            if (detail != null) {
                                SideGestureService.current?.dispatchAction(detail)
                                requestClose()
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

@Composable
private fun QuickSearchContent(
    index: QuickSearchIndex,
    clipboardText: String?,
    torchOn: Boolean,
    closing: Boolean,
    onClose: () -> Unit,
    onExitFinished: () -> Unit,
    onResult: (QuickSearchResult, String) -> Unit,
    onLongPress: (QuickSearchResult) -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val libraryFlow = remember { hunoia.luno.config.ConfigProvider.newActionLibrarySettings }
    val libraryState by libraryFlow.collectAsState(initial = hunoia.luno.action.model.NewActionLibrarySettings())
    val entries = libraryState.entries
    val launcherFlow = remember { hunoia.luno.config.ConfigProvider.quickAppLauncherSettings }
    val launcherSettings by launcherFlow.collectAsState(initial = hunoia.luno.config.model.QuickAppLauncherSettings())
    val searchSettingsFlow = remember { hunoia.luno.config.ConfigProvider.quickSearchSettings }
    val searchSettings by searchSettingsFlow.collectAsState(initial = hunoia.luno.config.model.QuickSearchSettings())

    var query by remember { mutableStateOf(TextFieldValue("")) }
    val scope = rememberCoroutineScope()
    // 联系人权限 + 后台加载：授权后 IO 线程一次性进内存，打字只查内存；
    // 加载完成 bump tick 触发 results 重算。
    val contactsPerm = rememberPermissionState(android.Manifest.permission.READ_CONTACTS)
    var contactsTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(contactsPerm.isGranted, searchSettings.searchContacts) {
        if (contactsPerm.isGranted && searchSettings.searchContacts) {
            withContext(Dispatchers.IO) { index.ensureContacts() }
            contactsTick++
        }
    }
    // P3 媒体权限：三个一次要（部分授权也认，有啥搜啥）；all-files 不是运行时权限，
    // 单独查 isExternalStorageManager。resume tick 覆盖从设置页回来不重组的问题。
    var permTick by remember { mutableIntStateOf(0) }
    val mediaLifecycle = LocalLifecycleOwner.current
    DisposableEffect(mediaLifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permTick++
        }
        mediaLifecycle.lifecycle.addObserver(observer)
        onDispose { mediaLifecycle.lifecycle.removeObserver(observer) }
    }
    val permCtx = LocalContext.current
    val act = permCtx as? Activity
    @Composable
    fun permGranted(p: String) = remember(permTick) {
        ContextCompat.checkSelfPermission(permCtx, p) == PackageManager.PERMISSION_GRANTED
    }
    val audioGranted = permGranted(android.Manifest.permission.READ_MEDIA_AUDIO)
    val imagesGranted = permGranted(android.Manifest.permission.READ_MEDIA_IMAGES)
    val videoGranted = permGranted(android.Manifest.permission.READ_MEDIA_VIDEO)
    val filesGranted = remember(permTick) { android.os.Environment.isExternalStorageManager() }
    val mediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permTick++ }
    // 三个全灭且都不再弹系统框 = 永久拒绝，授权行改跳设置页。
    fun permRationale(p: String) =
        act?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, p) } ?: false
    val mediaDeniedForever = !audioGranted && !imagesGranted && !videoGranted &&
        !permRationale(android.Manifest.permission.READ_MEDIA_AUDIO) &&
        !permRationale(android.Manifest.permission.READ_MEDIA_IMAGES) &&
        !permRationale(android.Manifest.permission.READ_MEDIA_VIDEO)
    var mediaTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(
        audioGranted, imagesGranted, videoGranted, filesGranted,
        searchSettings.searchMusic, searchSettings.searchFiles,
    ) {
        if ((searchSettings.searchMusic || searchSettings.searchFiles) &&
            (audioGranted || imagesGranted || videoGranted || filesGranted)
        ) {
            withContext(Dispatchers.IO) { index.ensureMedia() }
            mediaTick++
        }
    }
    // 日历/短信/通话：单权限 + IO 一次加载 + tick 重算（联系人同款）。
    val calendarPerm = rememberPermissionState(android.Manifest.permission.READ_CALENDAR)
    val smsPerm = rememberPermissionState(android.Manifest.permission.READ_SMS)
    val callsPerm = rememberPermissionState(android.Manifest.permission.READ_CALL_LOG)
    var calendarTick by remember { mutableIntStateOf(0) }
    var smsTick by remember { mutableIntStateOf(0) }
    var callsTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(calendarPerm.isGranted, searchSettings.searchCalendar) {
        if (calendarPerm.isGranted && searchSettings.searchCalendar) {
            withContext(Dispatchers.IO) { index.ensureCalendar() }
            calendarTick++
        }
    }
    LaunchedEffect(smsPerm.isGranted, searchSettings.searchSms) {
        if (smsPerm.isGranted && searchSettings.searchSms) {
            withContext(Dispatchers.IO) { index.ensureSms() }
            smsTick++
        }
    }
    LaunchedEffect(callsPerm.isGranted, searchSettings.searchCalls) {
        if (callsPerm.isGranted && searchSettings.searchCalls) {
            withContext(Dispatchers.IO) { index.ensureCalls() }
            callsTick++
        }
    }
    // 手电状态是 Activity 翻的，参数透进来供 results/开关行副标题用。
    val results = remember(
        query.text, entries, launcherSettings, searchSettings,
        contactsTick, mediaTick, calendarTick, smsTick, callsTick, torchOn,
    ) {
        if (query.text.isBlank()) {
            emptyList()
        } else {
            index.search(query.text, entries, launcherSettings, searchSettings, torchOn)
        }
    }
    // 历史行共用：长按删单条（搜索态/空态同一语义）。
    val deleteHistory: (String) -> Unit = { h ->
        scope.launch {
            runCatching {
                hunoia.luno.config.ConfigProvider.updateQuickSearchSettings {
                    it.copy(recentQueries = it.recentQueries.filterNot { q -> q == h })
                }
            }
        }
    }

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    // 语音输入：系统识别器回填 query；没识别器就不显示按钮（不报错）。
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(
                android.speech.RecognizerIntent.EXTRA_RESULTS,
            )?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { heard ->
                query = TextFieldValue(heard, TextRange(heard.length))
            }
        }
    }
    val voiceAvailable = remember(permCtx) {
        Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(
            permCtx.packageManager,
        ) != null
    }
    // 进出场动画状态：进场从 0.92/0 起播到 1，closing 置位后播到 0.95/0。
    val panelScale = remember { Animatable(0.92f) }
    val panelAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { panelScale.animateTo(1f, tween(300, easing = LinearOutSlowInEasing)) }
        launch { panelAlpha.animateTo(1f, tween(300)) }
    }
    LaunchedEffect(closing) {
        // 退场纯淡出 200ms：缩放的重采样尾段全是糊的（字和图标都糊），删掉只留 alpha。
        // 键盘跟淡出并行收（之前等 finish 后才收，拖一条 200ms+ 的尾巴）。
        // 播完（animateTo 返回）才调 onExitFinished 拆窗：提前拆会掐断结尾，
        // 播完才拆又会晾纯 blur——精确交接点就是播完这一刻。
        if (closing) {
            keyboard?.hide()
            panelAlpha.animateTo(0f, tween(200))
            if (BuildConfig.DEBUG) Log.i("LunoQuickSearch", "fadeDone=${System.currentTimeMillis()}")
            onExitFinished()
        }
    }
    // 退场中吞掉行点击：requestClose 幂等但 dispatch 不是，守一层。
    val safeResult: (QuickSearchResult, String) -> Unit = { r, q -> if (!closing) onResult(r, q) }
    val safeLongPress: (QuickSearchResult) -> Unit = { if (!closing) onLongPress(it) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 澎湃风进出场：底部轴心缩放 + 淡入淡出。进场 0.92→1/300ms 减速，
            // 退场 1→0.95/200ms 加速；退场中点击全部吞掉（closing 门控）。
            .graphicsLayer {
                scaleX = panelScale.value
                scaleY = panelScale.value
                alpha = panelAlpha.value
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !closing,
                onClick = onClose,
            ),
    ) {
        // statusBarsPadding：内容从状态栏下方开始，不垫在透明状态栏图标后面
        //（白卡压白图标会导致图标看不清，跟 blur 无关，必须避让）。
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            if (results.isNotEmpty()) {
                // 搜索态：匹配的历史全部参展（点即回填，贴胶囊），结果排其上。
                // 标识指令（bb 新闻 / sms 138）下历史不参展：纯目标结果。
                val q = query.text.trim()
                val matches = if (index.isDirective(q, searchSettings, launcherSettings)) {
                    emptyList()
                } else {
                    searchSettings.recentQueries.filter {
                        it.isNotBlank() && !it.equals(q, ignoreCase = true) && it.contains(q, ignoreCase = true)
                    }
                }
                val total = matches.size + results.size
                // 反转列表内容超高时会把首项顶出可视区之下：受控滚动，内容一变钉回
                // 第 0 项（最贴胶囊那行）。API 级保证可见，不依赖布局锚定行为。
                val searchListState = rememberLazyListState()
                LaunchedEffect(total) { searchListState.scrollToItem(0) }
                LazyColumn(
                    state = searchListState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = PageGutter).listFade(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
                    // 反转只管顺序不管小内容锚定，显式 Bottom 后单条目也必贴胶囊
                    verticalArrangement = Arrangement.spacedBy(SegmentedGap, Alignment.Bottom),
                    reverseLayout = true,
                ) {
                    items(matches, key = { "m-$it" }) { h ->
                        val pos = matches.indexOf(h)
                        HistoryRow(
                            text = h,
                            shape = segmentedShape(total - 1 - pos, total),
                            onClick = { query = TextFieldValue(h, TextRange(h.length)) },
                            onLongClick = { deleteHistory(h) },
                        )
                    }
                    itemsIndexed(results, key = { idx, item -> "${item::class.simpleName}-$idx-${item.title}" }) { index, item ->
                        val pos = matches.size + index
                        ResultRow(
                            result = item,
                            // 反转后视觉上下颠倒，shape 索引镜像后圆角仍落在组外端
                            shape = segmentedShape(total - 1 - pos, total),
                            onClick = { safeResult(item, query.text.trim()) },
                            onLongClick = { safeLongPress(item) },
                        )
                    }
                }
            } else if (query.text.isEmpty()) {
                // 空输入：剪贴板历史最贴胶囊，其次搜索历史（最近 3），常用、最近文件在上。
                // 剪贴板由 Activity.onWindowFocusChanged 喂进来（首开 composition 早于焦点）。
                val history = searchSettings.recentQueries.take(BLANK_RECENT_LIMIT)
                val clips = if (searchSettings.searchClipboard) {
                    // 当前剪贴优先（焦点刚读到的），后面跟历史去重。
                    (listOfNotNull(clipboardText) + searchSettings.clipHistory).distinct()
                } else {
                    emptyList()
                }
                val used = remember(launcherSettings) { index.topUsed(launcherSettings) }
                val recent = remember(mediaTick, searchSettings.searchFiles) {
                    if (searchSettings.searchFiles) index.recentFiles() else emptyList()
                }
                val clipCount = clips.size
                // 联系人未授权：顶部给一行授权入口（点申请，永久拒绝跳设置页）。
                val showGrant = searchSettings.searchContacts && !contactsPerm.isGranted
                val grantCount = if (showGrant) 1 else 0
                // P3：媒体缺权限给一行（一次要三个），all-files 没开给一行（跳设置页）。
                val showMediaGrant = (searchSettings.searchMusic || searchSettings.searchFiles) &&
                    !(audioGranted && imagesGranted && videoGranted)
                val showFilesGrant = searchSettings.searchFiles && !filesGranted
                val mediaGrantCount = if (showMediaGrant) 1 else 0
                val filesGrantCount = if (showFilesGrant) 1 else 0
                // 日历/短信/通话：各给一行授权入口（不合并，用户定的）。
                val showCalendarGrant = searchSettings.searchCalendar && !calendarPerm.isGranted
                val showSmsGrant = searchSettings.searchSms && !smsPerm.isGranted
                val showCallsGrant = searchSettings.searchCalls && !callsPerm.isGranted
                val calendarGrantCount = if (showCalendarGrant) 1 else 0
                val smsGrantCount = if (showSmsGrant) 1 else 0
                val callsGrantCount = if (showCallsGrant) 1 else 0
                if (history.isNotEmpty() || clips.isNotEmpty() || used.isNotEmpty() || recent.isNotEmpty() ||
                    showGrant || showMediaGrant || showFilesGrant ||
                    showCalendarGrant || showSmsGrant || showCallsGrant
                ) {
                    val total = history.size + clipCount + used.size + recent.size +
                        grantCount + mediaGrantCount + filesGrantCount +
                        calendarGrantCount + smsGrantCount + callsGrantCount
                    val blankListState = rememberLazyListState()
                    LaunchedEffect(total) { blankListState.scrollToItem(0) }
                    LazyColumn(
                        state = blankListState,
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = PageGutter).listFade(),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(SegmentedGap, Alignment.Bottom),
                        reverseLayout = true,
                    ) {
                        // 剪贴板历史最贴胶囊（首条=当前剪贴），其次搜索历史，最后常用/最近文件。
                        items(clips, key = { "c-$it" }) { clipText ->
                            val pos = clips.indexOf(clipText)
                            ClipboardRow(
                                text = clipText,
                                shape = segmentedShape(total - 1 - pos, total),
                                onClick = {
                                    query = TextFieldValue(clipText, TextRange(clipText.length))
                                },
                            )
                        }
                        items(history, key = { "h-$it" }) { h ->
                            val pos = clipCount + history.indexOf(h)
                            HistoryRow(
                                text = h,
                                shape = segmentedShape(total - 1 - pos, total),
                                onClick = {
                                    query = TextFieldValue(h, TextRange(h.length))
                                },
                                onLongClick = { deleteHistory(h) },
                            )
                        }
                        itemsIndexed(used, key = { idx, app -> "u-$idx-${app.appInfo.packageName}" }) { idx, app ->
                            val pos = clipCount + history.size + idx
                            ResultRow(
                                result = app,
                                shape = segmentedShape(total - 1 - pos, total),
                                onClick = { safeResult(app, "") },
                                onLongClick = { safeLongPress(app) },
                            )
                        }
                        // 最近文件（图库 + 文档按修改时间，不搜只展，点开/长按分享走 ResultRow）。
                        itemsIndexed(recent, key = { idx, item -> "r-$idx-${item.title}" }) { idx, item ->
                            val pos = clipCount + history.size + used.size + idx
                            ResultRow(
                                result = item,
                                shape = segmentedShape(total - 1 - pos, total),
                                onClick = { safeResult(item, "") },
                                onLongClick = { safeLongPress(item) },
                            )
                        }
                        if (showGrant) {
                            item(key = "grant-contacts") {
                                val pos = history.size + clipCount + used.size + recent.size
                                HistoryRow(
                                    text = stringResource(R.string.quick_search_grant_contacts),
                                    shape = segmentedShape(total - 1 - pos, total),
                                    icon = Icons.Default.Person,
                                    onClick = {
                                        if (contactsPerm.deniedForever) onOpenAppSettings()
                                        else contactsPerm.launchPermissionRequest()
                                    },
                                )
                            }
                        }
                        if (showMediaGrant) {
                            item(key = "grant-media") {
                                val pos = history.size + clipCount + used.size + recent.size + grantCount
                                HistoryRow(
                                    text = stringResource(R.string.quick_search_grant_media),
                                    shape = segmentedShape(total - 1 - pos, total),
                                    icon = Icons.Default.MusicNote,
                                    onClick = {
                                        if (mediaDeniedForever) {
                                            onOpenAppSettings()
                                        } else {
                                            mediaLauncher.launch(
                                                arrayOf(
                                                    android.Manifest.permission.READ_MEDIA_AUDIO,
                                                    android.Manifest.permission.READ_MEDIA_IMAGES,
                                                    android.Manifest.permission.READ_MEDIA_VIDEO,
                                                ),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                        if (showFilesGrant) {
                            item(key = "grant-files") {
                                val pos = history.size + clipCount + used.size + recent.size + grantCount + mediaGrantCount
                                HistoryRow(
                                    text = stringResource(R.string.quick_search_grant_files),
                                    shape = segmentedShape(total - 1 - pos, total),
                                    icon = Icons.Default.Description,
                                    onClick = {
                                        runCatching {
                                            permCtx.startActivity(
                                                Intent(
                                                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                                    android.net.Uri.parse("package:${permCtx.packageName}"),
                                                ),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                        if (showCalendarGrant) {
                            item(key = "grant-calendar") {
                                val pos = history.size + clipCount + used.size + recent.size +
                                    grantCount + mediaGrantCount + filesGrantCount
                                HistoryRow(
                                    text = stringResource(R.string.quick_search_grant_calendar),
                                    shape = segmentedShape(total - 1 - pos, total),
                                    icon = Icons.Default.DateRange,
                                    onClick = {
                                        if (calendarPerm.deniedForever) onOpenAppSettings()
                                        else calendarPerm.launchPermissionRequest()
                                    },
                                )
                            }
                        }
                        if (showSmsGrant) {
                            item(key = "grant-sms") {
                                val pos = history.size + clipCount + used.size + recent.size +
                                    grantCount + mediaGrantCount + filesGrantCount + calendarGrantCount
                                HistoryRow(
                                    text = stringResource(R.string.quick_search_grant_sms),
                                    shape = segmentedShape(total - 1 - pos, total),
                                    icon = Icons.AutoMirrored.Filled.Message,
                                    onClick = {
                                        if (smsPerm.deniedForever) onOpenAppSettings()
                                        else smsPerm.launchPermissionRequest()
                                    },
                                )
                            }
                        }
                        if (showCallsGrant) {
                            item(key = "grant-calls") {
                                val pos = history.size + clipCount + used.size + recent.size +
                                    grantCount + mediaGrantCount + filesGrantCount +
                                    calendarGrantCount + smsGrantCount
                                HistoryRow(
                                    text = stringResource(R.string.quick_search_grant_calls),
                                    shape = segmentedShape(total - 1 - pos, total),
                                    icon = Icons.Default.Call,
                                    onClick = {
                                        if (callsPerm.deniedForever) onOpenAppSettings()
                                        else callsPerm.launchPermissionRequest()
                                    },
                                )
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
            } else if (query.text.isBlank()) {
                // 全空格输入：标识提示（图标 来源名：标识），点即填入标识。
                // 空串走老空态；删掉空格回去。标识关闭的来源/引擎跳过。
                val hintSources = remember(searchSettings) {
                    listOf(
                        SRC_APP to Icons.Default.Apps,
                        SRC_SHORTCUT to Icons.Default.Shortcut,
                        SRC_ACTION to Icons.Default.PlayArrow,
                        SRC_SETTINGS to Icons.Default.Settings,
                        SRC_CONTACT to Icons.Default.Person,
                        SRC_MUSIC to Icons.Default.MusicNote,
                        SRC_FILE to Icons.Default.Description,
                        SRC_CALENDAR to Icons.Default.DateRange,
                        SRC_SMS to Icons.AutoMirrored.Filled.Message,
                        SRC_CALL to Icons.Default.Call,
                        SRC_TOGGLE to Icons.Default.Tune,
                    ).mapNotNull { (key, icon) ->
                        val t = searchSettings.effectiveTrigger(key)
                        if (t.isBlank()) null else Triple(key, t, icon)
                    }
                }
                val hintEngines = remember(searchSettings) {
                    searchSettings.effectiveEngines()
                        .filter { it.trigger.isNotBlank() }
                        .map { it.name to it.trigger.trim() }
                }
                val hintTotal = hintSources.size + hintEngines.size
                if (hintTotal > 0) {
                    val hintListState = rememberLazyListState()
                    LaunchedEffect(hintTotal) { hintListState.scrollToItem(0) }
                    LazyColumn(
                        state = hintListState,
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = PageGutter).listFade(),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(SegmentedGap, Alignment.Bottom),
                        reverseLayout = true,
                    ) {
                        itemsIndexed(hintSources, key = { _, h -> "th-${h.first}" }) { idx, h ->
                            val trigger = h.second
                            HistoryRow(
                                text = "${sourceTitle(h.first)}：$trigger",
                                shape = segmentedShape(hintTotal - 1 - idx, hintTotal),
                                icon = h.third,
                                onClick = {
                                    query = TextFieldValue(trigger, TextRange(trigger.length))
                                },
                            )
                        }
                        itemsIndexed(hintEngines, key = { _, e -> "te-${e.second}" }) { idx, e ->
                            val trigger = e.second
                            HistoryRow(
                                text = "${e.first}：$trigger",
                                shape = segmentedShape(hintTotal - 1 - hintSources.size - idx, hintTotal),
                                textIcon = e.first.firstOrNull()?.toString()?.let { TextIcon(it) },
                                onClick = {
                                    query = TextFieldValue(trigger, TextRange(trigger.length))
                                },
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            // 回车确认 = 右侧 Check：打列表首位。引擎兜底 tier -1 垫底，
            // 只有它是唯一行时才会被确认（不再需要跳过特例）。
            // 计算器排表头，天然成为确认目标。
            val confirmFirst: () -> Unit = {
                results.firstOrNull()?.let { safeResult(it, query.text.trim()) }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter)
                    // 顶部 0：列表底边直接坐到输入框上沿，中间 0 间隙。
                    .padding(bottom = 8.dp)
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
                    } else if (voiceAvailable) {
                        androidx.compose.material3.IconButton(
                            onClick = {
                                voiceLauncher.launch(
                                    Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(
                                            android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                            android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                        )
                                        putExtra(
                                            android.speech.RecognizerIntent.EXTRA_PROMPT,
                                            query.text,
                                        )
                                    },
                                )
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                            )
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                // 默认英文键盘 + 回车即确认（否则回车只是收键盘）。
                // Ascii 会锁死英文切不出中文；Text 则完全交给输入法。
                // 中间态（默认英文但可切换）API 层面不存在，二选一后定 Ascii。
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

/**
 * 列表上下羽化：顶部 28dp / 底部 24dp，溢出截断 melts 进背景。
 * 底部罩的是钉住的首行——贴紧输入框后它 melts 进框，顺滑但首行底部是虚的，
 * 这是内容羽化的税。离屏层多一笔绘制，行多时装机看帧率。
 */
private fun Modifier.listFade(
    top: androidx.compose.ui.unit.Dp = 28.dp,
    bottom: androidx.compose.ui.unit.Dp = 12.dp,
): Modifier = this
    .graphicsLayer {
        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
    }
    .drawWithContent {
        drawContent()
        val h = size.height
        if (h <= 0f) return@drawWithContent
        val topF = (top.toPx() / h).coerceIn(0f, 0.5f)
        val bottomF = (bottom.toPx() / h).coerceIn(0f, (1f - topF).coerceAtLeast(0f))
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to ComposeColor.Transparent,
                topF to ComposeColor.Black,
                (1f - bottomF) to ComposeColor.Black,
                1f to ComposeColor.Transparent,
            ),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
        )
    }

/** 剪贴板行：标题=剪贴内容（单行省略），无副标题（跟历史行只差图标）；点即回填，无长按语义。 */@Composable
private fun ClipboardRow(text: String, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    SelectableListItem(
        title = text,
        subtitle = "",
        selected = false,
        onSelect = { onClick() },
        shape = shape,
        icon = Icons.Default.ContentPaste,
        verticalGap = 0.dp,
        horizontalGap = 0.dp,
        containerColor = MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.65f),
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
    )
}
@Composable
private fun HistoryRow(
    text: String,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    icon: ImageVector = Icons.Default.Search,
    textIcon: TextIcon? = null,
) {
    SelectableListItem(
        title = text,
        subtitle = "",
        selected = false,
        onSelect = { onClick() },
        shape = shape,
        icon = icon,
        textIcon = textIcon,
        verticalGap = 0.dp,
        horizontalGap = 0.dp,
        containerColor = MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.65f),
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
        onLongClick = onLongClick,
    )
}

@Composable
private fun ResultRow(
    result: QuickSearchResult,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
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
        shape = shape,        iconModel = iconModel(result, context),
        icon = iconVector(result),
        textIcon = textIcon(result),
        verticalGap = 0.dp,
        horizontalGap = 0.dp,
        // 半透明底色：透出窗口背后的系统模糊，不支持的设备直接呈现半透明。
        containerColor = colorScheme.surfaceBright.copy(alpha = 0.65f),
        selectedContainerColor = colorScheme.primaryContainer.copy(alpha = 0.65f),
        onLongClick = onLongClick,
    )
}

@Composable
private fun iconVector(result: QuickSearchResult): ImageVector? = when (result) {
    is QuickSearchResult.Url -> Icons.AutoMirrored.Filled.OpenInNew
    is QuickSearchResult.EngineSearch -> null
    is QuickSearchResult.Calc -> null
    is QuickSearchResult.Settings -> Icons.Default.Search
    is QuickSearchResult.ActionDef -> result.icon as? ImageVector
    is QuickSearchResult.LibraryEntry -> result.icon as? ImageVector
    is QuickSearchResult.App -> null
    is QuickSearchResult.Shortcut -> null
    is QuickSearchResult.Contact -> null
    is QuickSearchResult.AudioTrack -> Icons.Default.MusicNote
    is QuickSearchResult.ImageVideo ->
        if (result.mime.startsWith("video")) Icons.Default.Movie else Icons.Default.Image
    is QuickSearchResult.DocFile -> Icons.Default.Description
    is QuickSearchResult.CalendarEvent -> Icons.Default.DateRange
    is QuickSearchResult.SmsMsg -> Icons.AutoMirrored.Filled.Message
    is QuickSearchResult.CallLogEntry -> Icons.Default.Call
    is QuickSearchResult.PhoneToggle -> when (result.kind) {
        QuickSearchResult.ToggleKind.WIFI -> Icons.Default.Wifi
        QuickSearchResult.ToggleKind.BLUETOOTH -> Icons.Default.Bluetooth
        QuickSearchResult.ToggleKind.TORCH -> Icons.Default.FlashlightOn
        QuickSearchResult.ToggleKind.DND -> Icons.Default.DoNotDisturbOn
        QuickSearchResult.ToggleKind.LOCATION -> Icons.Default.LocationOn
        QuickSearchResult.ToggleKind.HOTSPOT -> Icons.Default.WifiTethering
    }
}

@Composable
private fun iconModel(result: QuickSearchResult, context: android.content.Context): Any? {
    val sizePx = with(LocalDensity.current) { 24.dp.toPx().toInt() }
    return when (result) {
        is QuickSearchResult.App ->
            QuickLaunchFacade.appIconRequest(context, result.appInfo.packageName, sizePx)
        // 快捷方式自带图标优先（行为不变），取不到才回落来源应用图标（之前行首空着）。
        is QuickSearchResult.Shortcut -> result.shortcut.iconBitmap
            ?: QuickLaunchFacade.appIconRequest(context, result.shortcut.packageName, sizePx)
        else -> null
    }
}

@Composable
private fun textIcon(result: QuickSearchResult): TextIcon? = when (result) {
    is QuickSearchResult.LibraryEntry -> result.icon as? TextIcon
    is QuickSearchResult.ActionDef -> result.icon as? TextIcon
    // 引擎行用首字图标（B/G/D…），计算器行用 =，联系人用姓名首字，替代通用图标。
    is QuickSearchResult.EngineSearch -> result.engineName.firstOrNull()?.toString()?.let { TextIcon(it) }
    is QuickSearchResult.Calc -> TextIcon("=")
    is QuickSearchResult.Contact -> result.name.firstOrNull()?.toString()?.let { TextIcon(it) }
    else -> null
}
