package hunoia.luno.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build

object PackageChangeReceiver {

    private var registered = false
    private val listeners = mutableListOf<() -> Unit>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            listeners.toList().forEach { it() }
        }
    }

    fun register(context: Context, onPackageChanged: () -> Unit) {
        listeners.add(onPackageChanged)
        if (registered) return
        registered = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter)
    }

    fun unregister(context: Context, onPackageChanged: () -> Unit) {
        listeners.remove(onPackageChanged)
        if (listeners.isEmpty() && registered) {
            runCatching { context.unregisterReceiver(receiver) }
            registered = false
        }
    }
}

fun PackageManager.queryIntentActivitiesCompat(intent: Intent, flags: Int): List<ResolveInfo> {
    return this.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
}
