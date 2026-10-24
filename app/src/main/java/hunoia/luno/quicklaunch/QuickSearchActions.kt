package hunoia.luno.quicklaunch

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.material.icons.filled.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import hunoia.luno.BuildConfig
import hunoia.luno.R
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.bridge.system.buildViewIntent
import hunoia.luno.bridge.system.normalizeOpenAppOrUrl
import hunoia.luno.config.model.MAX_QUERY_CHARS
import hunoia.luno.config.model.MAX_RECENT_QUERIES
import hunoia.luno.config.model.MIN_HISTORY_CHARS
import hunoia.luno.quicklaunch.query.SearchAction
import hunoia.luno.quicklaunch.query.SearchToggleKind
import kotlinx.coroutines.launch

/** 直拷文本 + 已复制 toast：计算器/网址/引擎长按共用。 */
internal fun QuickSearchActivity.copyText(text: String) {
    hunoia.luno.bridge.system.copyText(this, "quicksearch", text)
    showToast(R.string.quick_search_copied)
}

/**
 * 执行引擎打开：先按指定包/选择器打开，失败再试回退链接。
 * 返回是否最终打开成功；设置页「测试」与搜索结果点按共用，避免两套行为。
 *
 * URL 走 [normalizeOpenAppOrUrl] + [buildViewIntent]（与 Launcher.launchUrl 同一套）：
 * 裸域名补 https://、intent:// / android-app:// 解析、http(s) 补 BROWSABLE。
 * 直接 Uri.parse 会让「example.com」这类无 scheme 输入直接打不开。
 */
internal fun launchSearchUrl(context: Context, action: SearchAction.OpenUrl): Boolean {
    fun start(url: String, packageName: String = "", chooser: Boolean = false): Boolean {
        val normalized = normalizeOpenAppOrUrl(url) ?: return false
        val intent = buildViewIntent(normalized)
        if (packageName.isNotBlank()) intent.setPackage(packageName)
        val launch = if (chooser) {
            // 只有单个可处理应用时，部分系统会直接拉起而不显示选择器；
            // 显式关掉 AUTO_LAUNCH，保证「每次询问」真的每次都问。
            // 注：EXTRA_AUTO_LAUNCH_SINGLE_CHOICE 仅 API 33+ 生效，旧系统忽略该 extra。
            Intent.createChooser(intent, null).apply {
                putExtra(Intent.EXTRA_AUTO_LAUNCH_SINGLE_CHOICE, false)
            }
        } else {
            intent
        }
        return runCatching { context.startActivity(launch) }.isSuccess
    }
    if (start(action.url, action.packageName, action.forceChooser)) return true
    return action.fallbackUrl.isNotBlank() && start(action.fallbackUrl)
}

/** 设置页「测试」用：预览 Open / OpenUrl 两类动作，不关面板。 */
internal fun launchSearchAction(context: Context, action: SearchAction): Boolean = when (action) {
    is SearchAction.Open -> {
        val intent = Intent(action.intent)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.isSuccess
    }
    is SearchAction.OpenUrl -> launchSearchUrl(context, action)
    else -> false
}

/** 确认搜索记历史：只记 6 字以上、不超 [MAX_QUERY_CHARS] 的，去重 cap 上限，顺手清掉已存短串，供各结果分支共用。 */
internal fun QuickSearchActivity.recordQuery(text: String) {
    if (text.isBlank() || text.trim().length < MIN_HISTORY_CHARS) return
        // 超长（粘贴的 MB 级文本）直接丢弃：记进去只会拖慢 DataStore 全文件重写与解析。
    if (text.length > MAX_QUERY_CHARS) return
    lifecycleScope.launch {
        runCatching {
            hunoia.luno.config.ConfigProvider.updateQuickSearchSettings {
                it.copy(
                    recentQueries = (listOf(text) + it.recentQueries)
                        .distinct()
                        .filter { q -> q.trim().length >= MIN_HISTORY_CHARS }
                        .take(MAX_RECENT_QUERIES),
                )
            }
        }
    }
}

/** 结果级 Pin：同一 query 下切换固定状态，写回最近使用顺序。 */
internal fun QuickSearchActivity.toggleResultPin(query: String, key: String) {
    val normalized = hunoia.luno.quicklaunch.query.normalizePinQuery(query)
    if (normalized.isEmpty()) return
    lifecycleScope.launch {
        runCatching {
            var pinned = false
            hunoia.luno.config.ConfigProvider.updateQuickSearchSettings { current ->
                val existing = current.pinnedResults[normalized].orEmpty()
                pinned = key !in existing
                val next = if (pinned) {
                    (listOf(key) + existing).distinct()
                        .take(hunoia.luno.config.model.MAX_PINS_PER_QUERY)
                } else {
                    existing.filterNot { it == key }
                }
                // 使用顺序「新的在前」：本次 query 前插，归一化淘汰时丢尾部。
                val order = (listOf(normalized) + current.pinnedQueryOrder)
                    .distinct()
                    .take(hunoia.luno.config.model.MAX_PINNED_QUERIES)
                current.copy(
                    pinnedResults = if (next.isEmpty()) {
                        current.pinnedResults - normalized
                    } else {
                        current.pinnedResults + (normalized to next)
                    },
                    pinnedQueryOrder = order,
                )
            }
            showToast(if (pinned) R.string.search_result_pinned else R.string.search_result_unpinned)
        }
    }
}

/**
 * 全局隐藏结果；隐藏后从所有 Pin 桶里移除，避免恢复后残留。
 * [title] 一起存：设置页的「已隐藏的结果」只存 key 的话用户认不出是哪条；
 * 时间戳供设置页按时间倒序展示。
 */
internal fun QuickSearchActivity.hideSearchResult(key: String, title: String) {
    if (key.isBlank()) return
    lifecycleScope.launch {
        runCatching {
            hunoia.luno.config.ConfigProvider.updateQuickSearchSettings { current ->
                val nextKeys = (current.hiddenResultKeys + key)
                    .distinct()
                    .takeLast(hunoia.luno.config.model.MAX_HIDDEN_RESULTS)
                current.copy(
                    hiddenResultKeys = nextKeys,
                    hiddenResultLabels = (current.hiddenResultLabels + (key to title))
                        .filterKeys { it in nextKeys },
                    hiddenResultTimes = (current.hiddenResultTimes + (key to System.currentTimeMillis()))
                        .filterKeys { it in nextKeys },
                    pinnedResults = current.pinnedResults.mapValues { (_, keys) ->
                        keys.filterNot { it == key }
                    }.filterValues { it.isNotEmpty() },
                )
            }
            showToast(R.string.search_result_hidden)
        }
    }
}

/** 草稿只存文本，不存图片；关闭面板/确认搜索时调用。 */
internal fun QuickSearchActivity.saveSearchDraft(query: String) {
    lifecycleScope.launch {
        runCatching {
            hunoia.luno.config.ConfigProvider.updateQuickSearchSettings {
                it.copy(lastQuery = query.take(MAX_QUERY_CHARS))
            }
        }
    }
}

/** P3：分享文件（图片/视频/文档长按）。 */
internal fun QuickSearchActivity.shareUri(uri: String, mime: String) {
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

internal fun QuickSearchActivity.toggleTorch() {
    if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        torchPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        return
    }
    flipTorch()
}

internal fun QuickSearchActivity.flipTorch() {
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
internal fun QuickSearchActivity.handleToggle(kind: SearchToggleKind) {
    when (kind) {
        SearchToggleKind.WIFI -> runCatching {
            startActivity(Intent(android.provider.Settings.Panel.ACTION_WIFI))
        }
        SearchToggleKind.BLUETOOTH -> runCatching {
            startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
        }
        SearchToggleKind.TORCH -> toggleTorch()
        SearchToggleKind.DND -> {
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
        SearchToggleKind.LOCATION -> runCatching {
            startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
        SearchToggleKind.HOTSPOT -> {
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

/**
     * 退场统一入口：只置 closing 位，finish() 由退场动画播完后交接（见 content 的 onExitFinished），
     * 不设固定 delay——动画和计时并发，固定 delay 只会在播完后多晾一段纯 blur。
     * 返回键也走这里（见 onCreate 的 onBackPressedDispatcher 拦截）。
     */

internal fun QuickSearchActivity.requestClose() {
    if (closingState.value) return
    if (BuildConfig.DEBUG) Log.i("LunoQuickSearch", "closeStart=${System.currentTimeMillis()}")
    closingState.value = true
        // blur 提前摘（背景立刻清晰，剩下 390ms 晾的是透明窗 + 清晰底，尾巴隐形）；
        // 重开恢复 blur 见 onWindowFocusChanged。
    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
}

/** 退场动画播完后来调：拆窗 + 压住系统退场动画（0 = 禁用）。 */
internal fun QuickSearchActivity.finishAfterExit() {
    finish()
    if (android.os.Build.VERSION.SDK_INT >= 34) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
