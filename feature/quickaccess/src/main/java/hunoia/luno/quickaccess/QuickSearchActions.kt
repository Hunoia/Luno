package hunoia.luno.quickaccess

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.core.net.toUri
import hunoia.luno.core.LunoLog as Log
import androidx.lifecycle.lifecycleScope
import hunoia.luno.quickaccess.BuildConfig
import hunoia.luno.quickaccess.R
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.bridge.system.buildViewIntent
import hunoia.luno.bridge.system.normalizeOpenAppOrUrl
import hunoia.luno.config.model.MAX_QUERY_CHARS
import hunoia.luno.config.model.MAX_RECENT_QUERIES
import hunoia.luno.config.model.MIN_HISTORY_CHARS
import hunoia.luno.core.AppContext
import hunoia.luno.core.LOG_TAG
import hunoia.luno.quickaccess.query.SearchAction
import hunoia.luno.runtime.surface.GestureCommand
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 直拷文本 + 已复制 toast：计算器/网址/引擎长按共用。 */
internal fun QuickSearchActivity.copyText(text: String) {
    hunoia.luno.clipboard.ClipboardCenter.writeTextImmediate(text, "quicksearch")
    showToast(hunoia.luno.design.R.string.copied)
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
    // 自定义标签页：仅无指定包、非选择器时尝试；失败回退普通打开。
    // 指定包（App 模式）不能走 Custom Tabs：Custom Tabs 只会拉起默认浏览器的标签页，
    // 与「在指定应用里打开」互斥，所以设置页在 App 模式下不展示该开关。
    if (action.useCustomTabs && action.packageName.isBlank() && !action.forceChooser) {
        if (CustomTabsLauncher.launch(context, action.url)) return true
    }
    if (start(action.url, action.packageName, action.forceChooser)) return true
    return action.fallbackUrl.isNotBlank() && start(action.fallbackUrl)
}

/** 跳转到目标并注入查询词（+可选 IME 回车）。设置页「测试」与搜索结果点按共用。 */
internal fun launchInjectQuery(context: Context, action: SearchAction.InjectQuery): Boolean {
    // 1. 启动目标：优先打开指定 Activity，否则打开外部链接（在目标包里）。
    val intent = when {
        action.targetActivity.isNotBlank() -> Intent().apply {
            component = ComponentName(action.targetPackage, action.targetActivity)
        }
        action.externLink.isNotBlank() -> {
            val normalized = normalizeOpenAppOrUrl(action.externLink) ?: return false
            buildViewIntent(normalized).apply { setPackage(action.targetPackage) }
        }
        else -> return false
    }
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val launched = runCatching { context.startActivity(intent) }.isSuccess
    if (!launched) return false
    // 2. 等目标前台，注入查询词；进程级 scope 保证面板关掉后仍执行（与拖拽插入同口径）。
    val query = action.query
    if (query.isNotBlank()) {
        // scope 缺失意味着注入不可能发生：不能返回 true 让调用方以为成功。
        val scope = AppContext.applicationScope
        if (scope == null) {
            Log.w(LOG_TAG, "launchInjectQuery: applicationScope missing, query not injected")
            return false
        }
        scope.launch(Dispatchers.Default) {
            delay(InjectQuerySettleMs)
            val insert = GestureCommand.insertTextFocused ?: return@launch
            runCatching { insert(query) }
            if (action.autoInputEnter) {
                GestureCommand.submitFocused?.let { runCatching { it() } }
            }
        }
    }
    return true
}

/** 目标应用前台前的等待：太短输入框还没出现，太长用户能感觉到卡顿。 */
private const val InjectQuerySettleMs = 420L

/** 自定义标签页服务连接超时：超时未连上退回系统浏览器。 */
private const val CustomTabsBindTimeoutMs = 1500L

/**
 * Custom Tabs 打开：绑定默认浏览器自定义标签页服务、预热、拉起会话。
 * 服务连不上/无可用包时返回 false（调用方回退普通打开），连上但超时才拉起则退回 ACTION_VIEW。
 */
private object CustomTabsLauncher {
    fun launch(context: Context, url: String): Boolean {
        val normalized = normalizeOpenAppOrUrl(url) ?: return false
        val uri = normalized.toUri()
        val appContext = context.applicationContext
        val launched = AtomicBoolean(false)
        val mainHandler = Handler(Looper.getMainLooper())
        val connection = object : CustomTabsServiceConnection() {
            override fun onCustomTabsServiceConnected(name: ComponentName, client: CustomTabsClient) {
                if (launched.getAndSet(true)) return
                // 成功路径也要摘掉超时回调，否则 Handler 会多持有 connection/context 1.5s。
                mainHandler.removeCallbacksAndMessages(null)
                runCatching { client.warmup(0) }
                val session = client.newSession(null)
                runCatching { CustomTabsIntent.Builder(session).build().launchUrl(context, uri) }
                runCatching { appContext.unbindService(this) }
            }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        val packageName = CustomTabsClient.getPackageName(appContext, null, true) ?: return false
        if (!CustomTabsClient.bindCustomTabsService(appContext, packageName, connection)) return false
        mainHandler.postDelayed({
            if (launched.getAndSet(true)) return@postDelayed
            runCatching { appContext.unbindService(connection) }
            // 服务一直没连上（罕见）：退回系统浏览器，保证链接一定打开。
            runCatching { appContext.startActivity(buildViewIntent(normalized)) }
        }, CustomTabsBindTimeoutMs)
        return true
    }
}

/** 设置页「测试」用：预览 Open / OpenUrl 两类动作，不关面板。 */
fun launchSearchAction(context: Context, action: SearchAction): Boolean = when (action) {
    is SearchAction.Open -> {
        val intent = Intent(action.intent)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.isSuccess
    }
    is SearchAction.OpenUrl -> launchSearchUrl(context, action)
    is SearchAction.InjectQuery -> launchInjectQuery(context, action)
    else -> false
}

/** 确认搜索记历史：只记 6 字以上、不超 [MAX_QUERY_CHARS] 的，去重 cap 上限，顺手清掉已存短串，供各结果分支共用。 */
internal fun QuickSearchActivity.recordQuery(text: String) {
    if (text.isBlank() || text.trim().length < MIN_HISTORY_CHARS) return
        // 超长（粘贴的 MB 级文本）直接丢弃：记进去只会拖慢 DataStore 全文件重写与解析。
    if (text.length > MAX_QUERY_CHARS) return
    lifecycleScope.launch {
        runCatching {
            hunoia.luno.domain.search.SearchCenter.updateSearch {
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
    val normalized = hunoia.luno.quickaccess.query.normalizePinQuery(query)
    if (normalized.isEmpty()) return
    lifecycleScope.launch {
        runCatching {
            var pinned = false
            hunoia.luno.domain.search.SearchCenter.updateSearch { current ->
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
