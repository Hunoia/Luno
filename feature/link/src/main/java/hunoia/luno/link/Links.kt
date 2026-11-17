package hunoia.luno.link

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * 链接相关应用内导航端口：拉起规则编辑页、选择页与主界面 Intent。
 *
 * 链接选路与匹配由识别器（`:core:runtime`）决定，本对象只负责跳页。
 */
interface LinkPorts {
    /** 选择应用打开链接（应用内选择页）。 */
    fun pickApp(url: String)

    /** 多条链接的选择页（一次识别到多条时）。 */
    fun pickLinks(urls: List<String>)

    /** 主界面 Intent（通知点击等需要 PendingIntent 的场景）。 */
    fun mainActivityIntent(): Intent
}

/** 端口实现：`:app` 在 `App.onCreate` 注入 Context（两进程都注入）。 */
object AppLinkPorts : LinkPorts {

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

/** 链接执行壳的单例：所有调用方共用同一份端口实现。 */
val Links: LinkDispatcher by lazy { LinkDispatcher(AppLinkPorts) }
