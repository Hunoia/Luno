package hunoia.luno.link

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import hunoia.luno.link.R
import hunoia.luno.config.model.LinkDispatchRule
import hunoia.luno.config.model.LinkDispatchSettings
import hunoia.luno.config.model.LinkMissMode
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.core.LOG_TAG

/** 规则解析出的分发目标（胶囊上显示用）。 */
data class LinkTarget(
    val packageName: String,
    val label: String,
)

/** 链接分发的外部能力：启动（必须经执行口）、应用内导航。实现方在 `:app`。 */
interface LinkPorts {
    /**
     * 按规则把链接送到目标应用；返回是否真的发出。
     *
     * 实现方**必须**走 `ActionRequest → Dispatcher`：链接分发不是执行旁路，
     * 解冻、小窗、能力闸、三态反馈都由执行口统一负责。
     */
    suspend fun launchLink(
        packageName: String,
        className: String,
        miniWindow: Boolean,
        url: String,
    ): Boolean

    /** 拉起规则编辑页并预填匹配串（应用内导航）。 */
    fun createRule(host: String)

    /** 选择应用打开链接（应用内选择页）。 */
    fun pickApp(url: String)

    /** 多条链接的选择页（胶囊一次识别到多条时）。 */
    fun pickLinks(urls: List<String>)

    /** 主界面 Intent（通知点击等需要 PendingIntent 的场景）。 */
    fun mainActivityIntent(): android.content.Intent
}

/**
 * 把链接送进指定应用的动作层：胶囊（`:service`）与 `LinkDispatchActivity`（主进程）共用。
 *
 * 分发走 `ACTION_VIEW + setPackage(pkg)` 的隐式 Intent（同动作库「启动应用」的
 * intentAction/data 通路），因此自动带上解冻、冻结检测、用量统计与小窗支持。
 * Luno 自己可能被设成默认浏览器：所有回退路径都要排除自身组件，否则会自我循环。
 */
class LinkDispatcher(private val ports: LinkPorts) {

    /** 未指定具体浏览器时，胶囊/规则列表上显示的名字。 */
    private val BROWSER_LABEL = "浏览器"

    /** 规则的目标应用显示名；包名解析失败（已卸载）回退显示包名本身。 */
    fun targetOf(context: Context, rule: LinkDispatchRule): LinkTarget? {
        val pkg = rule.targetPackage.trim()
        if (pkg.isEmpty()) {
            // 交给浏览器且未指定具体浏览器：显示成「浏览器」，图标走兜底。
            return if (rule.viaBrowser) LinkTarget("", BROWSER_LABEL) else null
        }
        val label = runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
        return LinkTarget(pkg, label)
    }

    /**
     * 规则在 [url] 上实际会用到的目标：`viaBrowser` 且未指定浏览器时，顺手把自动挑选的
     * 浏览器解析出来，胶囊/选择页才能显示「发往 小米浏览器」而不是笼统的「浏览器」。
     */
    fun resolvedTargetOf(context: Context, rule: LinkDispatchRule, url: String): LinkTarget? {
        if (!rule.viaBrowser) return targetOf(context, rule)
        val pkg = rule.targetPackage.trim().ifBlank {
            resolveBrowser(context, url).orEmpty()
        }
        if (pkg.isEmpty()) return LinkTarget("", BROWSER_LABEL)
        val label = targetOf(context, rule.copy(targetPackage = pkg, viaBrowser = false))?.label
        return LinkTarget(pkg, label ?: pkg)
    }

    /**
     * 规则表级分发：先按原链接匹配，命中规则若开了「解析短链」就跟随后重新匹配一次，
     * 再交给 [dispatch]。解析失败（超时/断网/无跳转）用原链接继续，绝不把点击吞掉。
     */
    suspend fun dispatch(
        context: Context,
        url: String,
        settings: LinkDispatchSettings,
    ): Boolean {
        val first = LinkUrls.firstMatch(url, settings.rules) ?: return false
        if (!first.resolveShortLink) return dispatch(context, url, first)
        val (effectiveUrl, rule) = resolveShortLink(url, first, settings)
        return dispatch(context, effectiveUrl, rule)
    }

    /** 短链解析 → 二次匹配；返回最终要打开的 URL 与规则。 */
    private suspend fun resolveShortLink(
        url: String,
        rule: LinkDispatchRule,
        settings: LinkDispatchSettings,
    ): Pair<String, LinkDispatchRule> {
        val candidates = LinkShortLinkResolver.candidates(url)
        if (candidates.isEmpty()) return url to rule
        // 优先挑「能命中别的规则」的候选：短链后面往往是目标应用自己的域名。
        for (candidate in candidates) {
            if (candidate == url) continue
            val matched = LinkUrls.firstMatch(candidate, settings.rules) ?: continue
            if (matched.id == rule.id) continue
            return candidate to matched
        }
        // 二次匹配不到：仍用发起解析的规则，但打开解析后的地址（例如短链直接跳到 App Link 页）。
        val fallback = candidates.firstOrNull { it != url } ?: return url to rule
        return fallback to rule
    }

    /** 按规则把 [url] 分发给目标应用。返回是否已发出启动。 */
    suspend fun dispatch(context: Context, url: String, rule: LinkDispatchRule): Boolean {
        if (!rule.isValid) return false
        val target = if (rule.viaBrowser) {
            // 交给浏览器：指定了就发给指定浏览器，没指定就自动挑一个非 Luno 的。
            rule.targetPackage.trim().ifBlank {
                resolveBrowser(context, url) ?: run {
                    Log.w(LOG_TAG, "link dispatch: no other browser for $url")
                    return false
                }
            }
        } else {
            rule.targetPackage.trim()
        }
        // 规则指向 Luno 自己：直接返回 false，交给上层走回退，避免死循环。
        if (target == context.packageName) {
            Log.w(LOG_TAG, "link dispatch target is self, skip: $url")
            return false
        }
        val effectiveUrl = LinkUrls.applyTemplate(url, rule.template)
        val launched = runCatching {
            ports.launchLink(
                packageName = target,
                className = rule.targetActivity.trim(),
                miniWindow = rule.miniWindow,
                url = effectiveUrl,
            )
        }.onFailure { Log.w(LOG_TAG, "link dispatch failed: $effectiveUrl -> $target", it) }
            .getOrDefault(false)
        if (launched) {
            Log.i(LOG_TAG, "link dispatched: ${rule.name}/${rule.id} -> $target (${effectiveUrl.take(80)})")
        }
        return launched
    }

    /** 系统分享面板（既有剪贴板胶囊点击行为）。 */
    fun share(context: Context, url: String) {
        shareText(context, url)
    }

    /** 一次分享多条链接（识屏卡片的「分享全部」）：每条一行。 */
    fun shareAll(context: Context, urls: List<String>) {
        if (urls.isEmpty()) return
        shareText(context, urls.joinToString("\n"))
    }

    private fun shareText(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startExternalIntent(context, Intent.createChooser(send, null))
    }

    /**
     * 用屏幕识别到的域名新建一条分发规则：拉起主界面的规则编辑页并预填匹配串。
     * 不在 `:service` 里直接写配置——目标应用得由用户选，走既有编辑页最省事。
     */
    fun createRule(context: Context, host: String) {
        val pattern = host.trim()
        if (pattern.isEmpty()) return
        runCatching { ports.createRule(pattern) }
            .onFailure { Log.w(LOG_TAG, "link rule editor launch failed", it) }
    }

    /** 用其他浏览器打开；解析到 Luno 自己时改成「选择应用」，避免自我循环。 */
    fun openInBrowser(context: Context, url: String) {
        val view = viewIntent(url)
        val resolvedPkg = runCatching {
            context.packageManager.resolveActivity(view, 0)?.activityInfo?.packageName
        }.getOrNull()
        if (resolvedPkg == null || resolvedPkg == context.packageName) {
            pickApp(context, url)
            return
        }
        startExternalIntent(context, view)
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

    /** 按未命中策略回退；[policy] 见 [LinkMissMode]。 */
    fun fallback(context: Context, url: String, policy: LinkMissMode) {
        when (policy) {
            LinkMissMode.SHARE -> share(context, url)
            LinkMissMode.BROWSER -> openInBrowser(context, url)
            LinkMissMode.PICKER -> pickApp(context, url)
        }
    }

    /**
     * 分发失败后的回退：命中规则可以自定义自己的回退方式，没设则跟随全局。
     * 只应在「有命中规则但没发出去」时调用；完全未命中直接用 [fallback]。
     */
    fun fallbackFor(
        context: Context,
        url: String,
        rule: LinkDispatchRule?,
        settings: LinkDispatchSettings,
    ) {
        fallback(context, url, rule?.missAction ?: settings.missAction)
    }

    /**
     * 自动挑一个能打开该链接的浏览器：优先系统默认浏览器，但**绝不含 Luno 自己**
     * （Luno 常被设为默认浏览器，选它等于自己调自己）；候选为空返回 null。
     */
    fun resolveBrowser(context: Context, url: String): String? {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
        val candidates = runCatching {
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .map { it.activityInfo.packageName }
                .distinct()
                .filter { it != context.packageName }
        }.getOrElse { emptyList() }
        if (candidates.isEmpty()) return null
        val defaultPkg = runCatching {
            pm.resolveActivity(intent, 0)?.activityInfo?.packageName
        }.getOrNull()
        if (defaultPkg != null && defaultPkg != context.packageName && defaultPkg in candidates) {
            return defaultPkg
        }
        return candidates.first()
    }

    private fun viewIntent(url: String): Intent =
        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun startExternalIntent(context: Context, intent: Intent) {
        // 自己也可能在候选里（默认浏览器就是 Luno）：选择器里显式排掉分发入口组件。
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
