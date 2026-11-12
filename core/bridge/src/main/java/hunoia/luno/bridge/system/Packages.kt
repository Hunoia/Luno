package hunoia.luno.bridge.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

object PackageChangeReceiver {

    /** 本应用自己的冻结/解冻写入：跨进程通知缓存失效（包广播在部分 ROM 不可靠）。 */
    const val ACTION_LOCAL_PACKAGE_CHANGED = "hunoia.luno.action.LOCAL_PACKAGE_CHANGED"

    private var registered = false
    private val listeners = mutableListOf<() -> Unit>()

    private fun dispatch() {
        listeners.toList().forEach { it() }
    }

    private val localReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = dispatch()
    }

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
            dispatch()
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
        context.applicationContext.registerReceiver(
            localReceiver,
            IntentFilter(ACTION_LOCAL_PACKAGE_CHANGED),
            Context.RECEIVER_NOT_EXPORTED,
        )
    }

    /** 写入成功后由 PackageControl 调用：同一广播会到达主进程与 :service。 */
    fun notifyLocalPackageChanged(context: Context) {
        val intent = Intent(ACTION_LOCAL_PACKAGE_CHANGED).setPackage(context.packageName)
        runCatching {
            context.applicationContext.sendBroadcast(intent)
        }
    }

    private fun unregisterIfIdle(context: Context) {
        if (listeners.isEmpty() && registered) {
            runCatching { context.applicationContext.unregisterReceiver(receiver) }
            runCatching { context.applicationContext.unregisterReceiver(localReceiver) }
            registered = false
        }
    }
}

fun PackageManager.queryIntentActivitiesCompat(intent: Intent, flags: Int): List<ResolveInfo> {
    return this.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
}
