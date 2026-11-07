package hunoia.luno.quickaccess

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import hunoia.luno.quickaccess.query.SearchAction
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
    if (start(action.url, action.packageName, action.forceChooser)) return true
    return action.fallbackUrl.isNotBlank() && start(action.fallbackUrl)
}

/** 设置页「测试」用：预览 Open / OpenUrl 两类动作，不关面板。 */
fun launchSearchAction(context: Context, action: SearchAction): Boolean = when (action) {
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
