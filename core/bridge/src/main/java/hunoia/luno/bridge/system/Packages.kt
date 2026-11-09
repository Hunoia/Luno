package hunoia.luno.bridge.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

object PackageChangeReceiver {

    private var registered = false
    private val listeners = mutableListOf<() -> Unit>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.data?.schemeSpecificPart.isNullOrBlank()) return
            val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            // ADDED / REMOVED 带 EXTRA_REPLACING 是更新过程的一半，等 REPLACED 再通知，避免重复刷新。
            if (replacing && (intent.action == Intent.ACTION_PACKAGE_ADDED ||
                    intent.action == Intent.ACTION_PACKAGE_REMOVED)
            ) {
                return
            }
            listeners.toList().forEach { it() }
        }
    }

    fun register(context: Context, onPackageChanged: () -> Unit) {
        listeners.add(onPackageChanged)
        ensureRegistered(context)
    }

    fun unregister(context: Context, onPackageChanged: () -> Unit) {
        listeners.remove(onPackageChanged)
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
        if (listeners.isEmpty() && registered) {
            runCatching { context.applicationContext.unregisterReceiver(receiver) }
            registered = false
        }
    }
}

fun PackageManager.queryIntentActivitiesCompat(intent: Intent, flags: Int): List<ResolveInfo> {
    return this.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
}
