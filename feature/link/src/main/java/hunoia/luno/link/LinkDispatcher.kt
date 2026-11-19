package hunoia.luno.link

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import hunoia.luno.core.LunoLog as Log
import hunoia.luno.link.R
import hunoia.luno.bridge.system.queryIntentActivitiesCompat
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.RecognitionMatch
import hunoia.luno.config.model.RecognitionRule
import hunoia.luno.config.model.RecognitionSettings
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.domain.ActionRequest
import hunoia.luno.core.domain.AppLaunchAction
import hunoia.luno.core.domain.DispatchOrigin
import hunoia.luno.runtime.dispatch.ActionDispatch
import hunoia.luno.runtime.recognition.LinkShortLinkResolver
import hunoia.luno.runtime.recognition.LinkUrls

/** 规则解析出的分发目标（胶囊上显示用）。 */
data class LinkTarget(
    val packageName: String,
    val label: String,
)

/**
 * 链接的 Android 侧执行壳：挑浏览器、选应用页、发起 `ACTION_VIEW`、分发单条链接。
 *
 * **规则判定不在这里**：谁命中哪条链接由 `:core:runtime` 的识别器决定，本对象只负责
 * 「按规则里声明的目标把链接送出去」。因此它不认识规则表顺序、不做正则匹配。
 */
class LinkDispatcher(private val ports: LinkPorts) {

    private companion object {
        /** 探测「通用浏览器」用的地址：只认能处理普通网页的应用，避免把 B 站 / 抖音当浏览器。 */
        const val BROWSER_PROBE_URL = "https://example.com"
    }

    /** 规则的目标应用显示名；包名解析失败（已卸载）回退显示包名本身。 */
    fun targetOf(context: Context, match: RecognitionMatch.Link): LinkTarget? {
        val pkg = match.targetPackage.trim()
        if (pkg.isEmpty()) {
            // 交给浏览器且未指定具体浏览器：显示成「浏览器」，图标走兜底。
            return if (match.viaBrowser) {
                LinkTarget("", context.getString(R.string.link_browser_label))
            } else {
                null
            }
        }
        return LinkTarget(pkg, labelOf(context, pkg))
    }

    /**
     * 规则实际会用到的目标：目标包名为空时，顺手把自动挑选的浏览器解析出来，
     * 胶囊上才能显示「发往 小米浏览器」而不是笼统的「浏览器」（或空白）。
     */
    fun resolvedTargetOf(
        context: Context,
        match: RecognitionMatch.Link,
        browserPackage: String = "",
    ): LinkTarget? {
        if (match.targetPackage.isNotBlank()) return targetOf(context, match)
        val pkg = resolveBrowser(context, browserPackage)
        return if (pkg == null) {
            LinkTarget("", context.getString(R.string.link_browser_label))
        } else {
            LinkTarget(pkg, labelOf(context, pkg))
        }
    }

    /** 按规则 id 解析目标显示名；规则不存在 / 不是链接规则时返回 null。 */
    fun resolvedTargetFor(
        context: Context,
        ruleId: String,
        settings: RecognitionSettings,
    ): LinkTarget? {
        val match = settings.rules
            .firstOrNull { it.id == ruleId }
            ?.match as? RecognitionMatch.Link
            ?: return null
        return resolvedTargetOf(context, match, settings.browserPackage)
    }

    /**
     * 已选好规则后分发一次：短链解析 → 二次匹配（仍走识别器）→ 产出 `ActionRequest` 并执行。
     *
     * 短链解析会联网，可能改写 URL 与目标应用；不执行时返回 false，由调用方回退。
     */
    suspend fun dispatchMatched(
        context: Context,
        url: String,
        rule: RecognitionRule,
        settings: RecognitionSettings,
    ): Boolean {
        val match = rule.match as? RecognitionMatch.Link ?: return false
        val (effectiveUrl, effectiveRule) = if (match.resolveShortLink) {
            resolveShortLink(context, url, rule, settings)
        } else {
            url to rule
        }
        val resolution = resolutionOf(
            context = context,
            url = effectiveUrl,
            rule = effectiveRule,
            browserPackage = settings.browserPackage,
        ) ?: return false
        return dispatch(resolution)
    }

    /** 短链解析 → 二次匹配（用识别器，不另写一套匹配）。 */
    private suspend fun resolveShortLink(
        context: Context,
        url: String,
        origin: RecognitionRule,
        settings: RecognitionSettings,
    ): Pair<String, RecognitionRule> {
        val candidates = LinkShortLinkResolver.candidates(url)
        if (candidates.isEmpty()) return url to origin
        // 优先挑「能命中别的链接规则」的候选：短链后面往往是目标应用自己的域名。
        for (candidate in candidates) {
            if (candidate == url) continue
            val matched = LinkUrls.firstMatch(candidate, settings.rules) ?: continue
            if (matched.id == origin.id) continue
            return candidate to matched
        }
        // 二次匹配不到：仍用发起解析的规则，但打开解析后的地址。
        val fallback = candidates.firstOrNull { it != url } ?: return url to origin
        return fallback to origin
    }

    /**
     * 把已经选好的规则翻译成 `app.launch` 请求。这里只做 Resolve，不碰执行。
     */
    private fun resolutionOf(
        context: Context,
        url: String,
        rule: RecognitionRule,
        browserPackage: String,
    ): LinkResolution? {
        val match = rule.match as? RecognitionMatch.Link ?: return null
        // 空目标 = 「用能打开它的应用」：交给浏览器（优先全局设定 / 系统默认，避开 Luno 自己）。
        // 不这么兜的话，内置「任意链接」规则会产出 packageName 为空的动作，永远启动失败。
        val target = match.targetPackage.trim().takeIf { it.isNotEmpty() }
            ?: resolveBrowser(context, browserPackage)
            ?: run {
                Log.w(LOG_TAG, "link resolve: no external handler/browser for $url")
                return null
            }
        // 规则指向 Luno 自己：返回 null 交给上层回退，避免死循环。
        if (target == context.packageName) {
            Log.w(LOG_TAG, "link resolve target is self, skip: $url")
            return null
        }
        val effectiveUrl = LinkUrls.applyTemplate(url, match.template)
        return LinkResolution(
            rule = rule,
            targetPackage = target,
            url = effectiveUrl,
            request = ActionRequest(
                stored = AppLaunchAction.stored(
                    packageName = target,
                    className = match.targetActivity.trim(),
                    miniWindow = match.miniWindow,
                    intentAction = Intent.ACTION_VIEW,
                    data = effectiveUrl,
                ),
                origin = DispatchOrigin.LINK,
            ),
        )
    }

    private suspend fun dispatch(resolution: LinkResolution): Boolean {
        val launched = runCatching { ActionDispatch.dispatchAwait(resolution.request) }
            .onFailure {
                Log.w(
                    LOG_TAG,
                    "link dispatch failed: ${resolution.url.take(80)} -> ${resolution.targetPackage}",
                    it,
                )
            }
            .getOrDefault(false)
        if (launched) {
            Log.i(
                LOG_TAG,
                "link dispatched: ${resolution.rule.name}/${resolution.rule.id} -> " +
                    "${resolution.targetPackage} (${resolution.url.take(80)})",
            )
        }
        return launched
    }

    /** 系统分享面板。 */
    fun share(context: Context, url: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startExternalIntent(context, Intent.createChooser(send, null))
    }

    /** 用外部浏览器直接打开：优先 [preferredPackage]，否则自动挑（都避开 Luno 自己）。 */
    fun openInBrowser(context: Context, url: String, preferredPackage: String = "") {
        val pkg = resolveBrowser(context, preferredPackage)
        if (pkg == null) {
            Log.w(LOG_TAG, "link browser: no other browser for $url")
            pickApp(context, url)
            return
        }
        // 用 setPackage 直达选中的浏览器：Luno 自己是默认浏览器时，裸 ACTION_VIEW 会回到自己。
        val launched = runCatching {
            context.startActivity(viewIntent(url).setPackage(pkg))
        }.onFailure { Log.w(LOG_TAG, "link browser launch failed: $pkg", it) }.isSuccess
        if (!launched) pickApp(context, url)
    }

    /**
     * 列出能打开该链接的应用，由用户挑。
     *
     * 不用 `Intent.createChooser`：Luno 自己被设为默认浏览器时，系统选择器会把默认项排除后
     * 直接显示空白（HyperOS 实测），而且无法控制排序。改用自绘 [LinkExtras.ACTIVITY_SELECT]。
     */
    fun pickApp(context: Context, url: String) {
        runCatching { ports.pickApp(url) }
            .onFailure { Log.w(LOG_TAG, "link picker launch failed", it) }
    }

    /** 多条链接选择页（应用内导航，委托端口）。 */
    fun pickLinks(urls: List<String>) = ports.pickLinks(urls)

    /** 主界面 Intent（通知 PendingIntent 用）。 */
    fun mainActivityIntent(): Intent = ports.mainActivityIntent()

    /**
     * 本机可用的浏览器：用通用网页地址探测（只认真正能处理任意网页的应用），
     * 排除 Luno 自己，保留系统解析顺序。
     */
    fun browsers(context: Context): List<LinkTarget> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_VIEW, BROWSER_PROBE_URL.toUri())
        val resolved = runCatching {
            pm.queryIntentActivitiesCompat(intent, PackageManager.MATCH_ALL)
        }.getOrElse { emptyList() }
        return resolved.asSequence()
            .map { it.activityInfo.packageName }
            .filter { it != context.packageName }
            .distinct()
            .map { pkg -> LinkTarget(pkg, labelOf(context, pkg)) }
            .toList()
    }

    /**
     * 挑一个浏览器：优先 [preferredPackage]，否则系统默认浏览器，再否则列表第一个；
     * **绝不含 Luno 自己**（Luno 常被设为默认浏览器，选它等于自己调自己）；无候选返回 null。
     */
    fun resolveBrowser(context: Context, preferredPackage: String = ""): String? {
        val candidates = browsers(context).map { it.packageName }
        if (candidates.isEmpty()) return null
        val defaultPkg = runCatching {
            context.packageManager
                .resolveActivity(Intent(Intent.ACTION_VIEW, BROWSER_PROBE_URL.toUri()), 0)
                ?.activityInfo?.packageName
        }.getOrNull()
        return pickBrowserPackage(
            candidates = candidates,
            preferred = preferredPackage,
            defaultPackage = defaultPkg,
            selfPackage = context.packageName,
        )
    }

    private fun labelOf(context: Context, pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun viewIntent(url: String): Intent =
        Intent(Intent.ACTION_VIEW, url.toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun startExternalIntent(context: Context, intent: Intent) {
        val chooser = intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // 组件名用字面量：link 层不反向依赖 ui 层（分发流程要能在 :service 进程里跑）。
        val excluded = ComponentName(context.packageName, LinkExtras.ACTIVITY_DISPATCH)
        chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(excluded))
        val launched = runCatching { context.startActivity(chooser) }
            .onFailure { Log.w(LOG_TAG, "link chooser launch failed", it) }
            .isSuccess
        if (!launched) showToast(R.string.link_dispatch_open_failed)
    }
}

/** 一次链接选路的结果：Resolve 只产出动作请求，不执行。 */
data class LinkResolution(
    val rule: RecognitionRule,
    val targetPackage: String,
    val url: String,
    val request: ActionRequest,
)

/**
 * 浏览器选择（纯逻辑，方便单测）：优先显式指定，其次系统默认，最后候选第一个。
 *
 * - 显式指定但已不在候选列表（卸载 / 根本不是浏览器）时继续自动，不静默失败；
 * - 始终排除 Luno 自己（它是默认浏览器时选它 = 自己调自己）。
 */
internal fun pickBrowserPackage(
    candidates: List<String>,
    preferred: String,
    defaultPackage: String?,
    selfPackage: String,
): String? {
    val available = candidates.filter { it != selfPackage }.distinct()
    if (available.isEmpty()) return null
    val chosen = preferred.trim()
    if (chosen.isNotEmpty() && chosen in available) return chosen
    if (defaultPackage != null && defaultPackage != selfPackage && defaultPackage in available) {
        return defaultPackage
    }
    return available.first()
}
