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

    /** 最近应用黑名单：历史记录源头过滤，最近应用部件与上一个应用动作共用。 */
    @Volatile
    private var blocklist: Set<String> = emptySet()

    /**
     * 黑名单更新：修剪已记录历史并重算 [RecentAppsHub]，改设置当下生效。
     * 黑名单应用若正是当前前台，保留为 curr（只过滤「可切过去/可展示」的历史）。
     */
    fun onBlocklistChanged(next: Set<String>) {
        blocklist = next
        if (history.removeAll(next)) {
            RecentAppsHub.update(history.toList())
        }
    }

    fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val packageName = event.packageName?.toString()
                if (packageName != null &&
                    packageName !in blocklist &&
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

    suspend fun previousApp(): Boolean {
        val current = currPackageName
        if (current == null || history.isEmpty()) return false

        // 历史末尾含当前前台，取目标时要去掉；但黑名单应用作前台时不入历史，
        // 此时末尾就是真正的「上一个」。
        val candidates = if (history.lastOrNull() == current) history.dropLast(1) else history.toList()
        val target = candidates.lastOrNull()
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
