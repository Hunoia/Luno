package hunoia.luno.runtime.environment

import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import hunoia.luno.bridge.system.PackageChangeReceiver
import hunoia.luno.bridge.system.queryIntentActivitiesCompat

private const val PREVIOUS_APP_HISTORY_MAX = 16

class PreviousAppTracker(
    private val context: Context,
    private val packageManager: android.content.pm.PackageManager,
    private val scope: CoroutineScope,
    private val startActivity: (Intent) -> Unit,
    private val rootInActiveWindowPackageName: () -> String?,
    /** 自身包名：面板/设置等 overlay 窗口的 a11y 事件不入历史（否则部件会闪现本应用图标、
     * 且 currPackageName 被置成本应用会破坏 previousApp 语义）。 */
    private val ownPackageName: String,
) {
    private val history = ArrayDeque<String>()
    private var currPackageName: String? = null

    /**
     * 可启动包名集合：一次 MAIN/LAUNCHER 全量解析（单次 Binder）在 IO 算好、包变化时刷新。
     * 此前 hasLaunchIntent 在 a11y 事件（主线程）里逐包同步 getLaunchIntentForPackage，
     * 新装/冷启场景每次窗口切换都可能卡主线程；现在退化为集合查询，零 IPC。
     * null = 首次查询未就绪：这一小段时间窗口事件不入历史（服务刚起，历史本来就要重建）。
     */
    @Volatile
    private var launchablePackages: Set<String>? = null

    private val onPackageChanged: () -> Unit = { refreshLaunchablePackages() }

    init {
        refreshLaunchablePackages()
        PackageChangeReceiver.register(context, onPackageChanged)
    }

    private fun refreshLaunchablePackages() {
        scope.launch(Dispatchers.IO) {
            launchablePackages = runCatching {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                packageManager.queryIntentActivitiesCompat(intent, 0)
                    .asSequence()
                    .mapNotNull { it.activityInfo?.applicationInfo?.packageName }
                    .toSet()
            }.getOrDefault(emptySet())
        }
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
                if (packageName != null && packageName != ownPackageName) {
                    RecentAppsHub.updateForeground(packageName)
                }
                if (packageName != null &&
                    packageName != ownPackageName &&
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
        PackageChangeReceiver.unregister(context, onPackageChanged)
        history.clear()
        RecentAppsHub.clear()
        currPackageName = null
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
        return key in (launchablePackages ?: return false)
    }
}
