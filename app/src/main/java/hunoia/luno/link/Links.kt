package hunoia.luno.link

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import hunoia.luno.service.DispatchClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 链接分发的端口实现（`:app` 侧）。
 *
 * 启动一律经执行口：`:service` 进程用本进程的 Dispatcher（[LocalDispatch]），
 * 主进程经 [DispatchClient] 的 IPC 把 `ActionRequest` 交给 `:service` 的同一个 Dispatcher。
 * 链接分发不再自己调启动基础设施 —— 这是评审 P0 的落地。
 */
object AppLinkPorts : LinkPorts {

    override suspend fun launchLink(
        packageName: String,
        className: String,
        miniWindow: Boolean,
        url: String,
    ): Boolean = dispatchLaunch(
        hunoia.luno.core.domain.StoredAction(
            typeId = "app.launch",
            params = JsonObject(
                buildMap {
                    put("packageName", JsonPrimitive(packageName))
                    if (className.isNotEmpty()) put("className", JsonPrimitive(className))
                    put("miniWindow", JsonPrimitive(miniWindow))
                    put("intentAction", JsonPrimitive(Intent.ACTION_VIEW))
                    put("data", JsonPrimitive(url))
                }
            ),
        )
    )

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

    private suspend fun dispatchLaunch(
        action: hunoia.luno.core.domain.StoredAction,
    ): Boolean = LocalDispatch.provider?.invoke(action) ?: DispatchClient.dispatch(action)
}

/**
 * 本进程内的可等待分发口：`SideGestureService` 起来后由 `GestureCoordinator` 注入。
 *
 * 主进程没有它（provider = null），自动回退到 IPC。
 */
object LocalDispatch {
    var provider: (suspend (hunoia.luno.core.domain.StoredAction) -> Boolean)? = null
}

/** 链接分发的单例：所有调用方共用同一份端口实现。 */
val Links: LinkDispatcher by lazy { LinkDispatcher(AppLinkPorts) }
