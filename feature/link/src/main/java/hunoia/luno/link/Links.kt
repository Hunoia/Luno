package hunoia.luno.link

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * 链接分发的应用内导航端口实现。
 *
 * 链接选路由 [LinkDispatcher.resolve] 产出 `ActionRequest`，执行由
 * `:core:runtime` 的 `ActionDispatch` 统一转交 Dispatcher；本对象只负责拉起
 * 规则编辑页、选择页与主界面 Intent。
 */
object AppLinkPorts : LinkPorts {

    override fun createRule(host: String) {
        val pattern = host.trim()
        if (pattern.isEmpty()) return
        val context = AppLinkPorts.context ?: return
        context.startActivity(
            Intent().apply {
                component = ComponentName(context.packageName, LinkExtras.ACTIVITY_MAIN)
                putExtra(LinkExtras.EXTRA_RULE_PATTERN, pattern)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
        )
    }

    override fun pickLinks(urls: List<String>) {
        val context = AppLinkPorts.context ?: return
        context.startActivity(
            Intent().apply {
                component = ComponentName(context.packageName, LinkExtras.ACTIVITY_SELECT)
                putStringArrayListExtra(LinkExtras.EXTRA_URLS, ArrayList(urls))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    override fun mainActivityIntent(): Intent {
        val context = AppLinkPorts.context
        return Intent().apply {
            if (context != null) {
                component = ComponentName(context.packageName, LinkExtras.ACTIVITY_MAIN)
            } else {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    override fun pickApp(url: String) {
        val context = AppLinkPorts.context ?: return
        context.startActivity(
            Intent().apply {
                component = ComponentName(context.packageName, LinkExtras.ACTIVITY_SELECT)
                putExtra(LinkExtras.EXTRA_PICK_URL, url)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    /** 由 `:app` 在 `App.onCreate` 注入（两进程都注入）。 */
    var context: Context? = null
}

/** 链接分发的单例：所有调用方共用同一份端口实现。 */
val Links: LinkDispatcher by lazy { LinkDispatcher(AppLinkPorts) }
