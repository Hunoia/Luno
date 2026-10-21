package hunoia.luno.runtime.environment

import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREVIOUS_APP_HISTORY_MAX = 16

class PreviousAppTracker(
    private val packageManager: android.content.pm.PackageManager,
    private val startActivity: (Intent) -> Unit,
    private val rootInActiveWindowPackageName: () -> String?,
) {
    private val history = ArrayDeque<String>()
    private var currPackageName: String? = null
    private val launchablePackageCache = object : LinkedHashMap<String, Boolean>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean = size > 256
    }

    fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val packageName = event.packageName?.toString()
                if (packageName != null &&
                    hasLaunchIntent(packageName) &&
                    currPackageName != packageName
                ) {
                    history.remove(packageName)
                    history.addLast(packageName)
                    while (history.size > PREVIOUS_APP_HISTORY_MAX) history.removeFirst()
                    currPackageName = packageName
                    RecentAppsHub.update(history.toList())
                }
            }
            else -> Unit
        }
    }

    suspend fun previousApp(excludePackageNames: List<String>): Boolean {
        val current = currPackageName
        if (current == null || history.isEmpty()) return false

        val excludes = excludePackageNames.toSet()
        val target = history.dropLast(1).lastOrNull { it !in excludes }
        if (target == null) {
            if (currPackageNameError()) return queryLaunchIntentAndStart(current)
            return false
        }
        if (!queryLaunchIntentAndStart(target)) return false
        history.remove(target)
        history.addLast(target)
        currPackageName = target
        return true
    }

    fun onRelease() {
        history.clear()
        RecentAppsHub.clear()
        currPackageName = null
        launchablePackageCache.clear()
    }

    private suspend fun queryLaunchIntentAndStart(packageName: String?): Boolean {
        if (packageName.isNullOrEmpty()) return false
        val intent = withContext(Dispatchers.IO) {
            packageManager.getLaunchIntentForPackage(packageName)
        } ?: return false
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            true
        } catch (_: Exception) {
            // 目标应用可能已被卸载，切换失败是预期结果
            false
        }
    }

    private fun currPackageNameError(): Boolean {
        val pkgName = rootInActiveWindowPackageName()
        return pkgName != currPackageName
    }

    private fun hasLaunchIntent(packageName: String?): Boolean {
        val key = packageName ?: return false
        launchablePackageCache[key]?.let { return it }
        val result = packageManager.getLaunchIntentForPackage(key) != null
        launchablePackageCache[key] = result
        return result
    }
}
