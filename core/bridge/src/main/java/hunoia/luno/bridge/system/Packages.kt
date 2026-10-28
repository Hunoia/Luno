package hunoia.luno.bridge.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

/** 包变化事件：action 见 [hunoia.luno.core.domain.PackageChangeAction]。 */
data class PackageChangeEvent(
    val action: String,
    val packageName: String,
    /** 卸载但带 EXTRA_REPLACING：多数 ROM 的更新会先 REMOVED(replacing) 再 ADDED。 */
    val replacing: Boolean = false,
)

object PackageChangeReceiver {

    private var registered = false
    private val listeners = mutableListOf<() -> Unit>()
    private val eventListeners = mutableListOf<(PackageChangeEvent) -> Unit>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val event = intent.toPackageChangeEvent() ?: return
            listeners.toList().forEach { it() }
            eventListeners.toList().forEach { it(event) }
        }
    }

    fun register(context: Context, onPackageChanged: () -> Unit) {
        listeners.add(onPackageChanged)
        ensureRegistered(context)
    }

    /** 事件监听：回调带 action + packageName；与旧的无参监听可同时存在。 */
    fun registerForEvents(context: Context, onPackageChanged: (PackageChangeEvent) -> Unit) {
        eventListeners.add(onPackageChanged)
        ensureRegistered(context)
    }

    fun unregister(context: Context, onPackageChanged: () -> Unit) {
        listeners.remove(onPackageChanged)
        unregisterIfIdle(context)
    }

    fun unregisterForEvents(context: Context, onPackageChanged: (PackageChangeEvent) -> Unit) {
        eventListeners.remove(onPackageChanged)
        unregisterIfIdle(context)
    }

    private fun ensureRegistered(context: Context) {
        if (registered) return
        registered = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        // 永远挂在 applicationContext 上：首个注册者可能是 Activity（QuickSearchActivity），
        // 挂它身上会把实例钉到进程结束；且注销可能来自别的 context，跨 context 注销会抛。
        context.applicationContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    private fun unregisterIfIdle(context: Context) {
        if (listeners.isEmpty() && eventListeners.isEmpty() && registered) {
            runCatching { context.applicationContext.unregisterReceiver(receiver) }
            registered = false
        }
    }

    private fun Intent.toPackageChangeEvent(): PackageChangeEvent? {
        val packageName = data?.schemeSpecificPart?.takeIf { it.isNotBlank() } ?: return null
        val replacing = getBooleanExtra(Intent.EXTRA_REPLACING, false)
        val action = when (action) {
            Intent.ACTION_PACKAGE_ADDED ->
                if (replacing) return null else hunoia.luno.core.domain.PackageChangeAction.ADDED

            Intent.ACTION_PACKAGE_REMOVED ->
                if (replacing) return null else hunoia.luno.core.domain.PackageChangeAction.REMOVED

            Intent.ACTION_PACKAGE_REPLACED -> hunoia.luno.core.domain.PackageChangeAction.REPLACED
            Intent.ACTION_PACKAGE_CHANGED -> hunoia.luno.core.domain.PackageChangeAction.CHANGED
            else -> return null
        }
        return PackageChangeEvent(action, packageName, replacing)
    }
}

fun PackageManager.queryIntentActivitiesCompat(intent: Intent, flags: Int): List<ResolveInfo> {
    return this.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
}
