package hunoia.luno.runtime.environment

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import hunoia.luno.bridge.system.PackageChangeReceiver
import hunoia.luno.bridge.system.queryIntentActivitiesCompat
import hunoia.luno.core.domain.RecentApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val PREVIOUS_APP_HISTORY_MAX = 16

/**
 * 最近应用 / 上一个应用的唯一数据源。
 *
 * a11y 事件只给包名；同一个包可能同时有主应用与分身（只有 userId 不同），
 * 所以新前台先按主用户记一条，再异步解析当前实例的 userId 回填：
 * 历史按包名去重、保留**最近一次使用的实例**，黑名单按 `pkg#userId` 精确匹配。
 */
class PreviousAppTracker(
    private val context: Context,
    private val packageManager: PackageManager,
    private val scope: CoroutineScope,
    /** 统一启动口（`:app` 注入）：带 userId 直达分身实例。 */
    private val launchPackage: suspend (RecentApp) -> Boolean,
    /** 前台实例 userId 解析（仅当包有分身时才会真正查询任务栈，由 `:app` 注入）。 */
    private val resolveUserId: suspend (String) -> Int,
    private val rootInActiveWindowPackageName: () -> String?,
    /** 自身包名：面板/设置等 overlay 窗口的 a11y 事件不入历史（否则部件会闪现本应用图标、
     * 且 currApp 被置成本应用会破坏 previousApp 语义）。 */
    private val ownPackageName: String,
) {
    private var history: List<RecentApp> = emptyList()
    private var currApp: RecentApp? = null

    /** 前台切换令牌：异步解析 userId 期间又切走了就丢弃结果，避免历史顺序错乱。 */
    private var foregroundToken = 0

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
     * 按 `pkg#userId` 精确匹配：拉黑分身不影响同一包的主应用。
     * 黑名单应用若正是当前前台，保留为竞态兜底，只过滤「可切过去/可展示」的历史。
     */
    fun onBlocklistChanged(next: Set<String>) {
        blocklist = next
        val filtered = history.filterNot { it.blockKey in next }
        if (filtered.size != history.size) {
            history = filtered
            RecentAppsHub.update(history)
        }
    }

    fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == ownPackageName) return

        val known = currApp
        if (known?.packageName == packageName) {
            // 同包名（同实例；主↔分身直接互切时 a11y 无法区分）：只刷新前台，不重复记历史。
            RecentAppsHub.updateForeground(known)
            return
        }

        val optimistic = RecentApp(packageName)
        currApp = optimistic
        RecentAppsHub.updateForeground(optimistic)
        if (!hasLaunchIntent(packageName)) return

        // 先按主用户入历史保证实时性；有分身时再解析实际实例并替换。
        if (optimistic.blockKey !in blocklist) record(optimistic)
        val token = ++foregroundToken
        scope.launch {
            val userId = runCatching { resolveUserId(packageName) }.getOrDefault(0)
            if (token != foregroundToken) return@launch
            val resolved = RecentApp(packageName, userId)
            currApp = resolved
            RecentAppsHub.updateForeground(resolved)
            // 每个包只保留最近一次使用的实例；解析成被拉黑的分身时整条移出历史。
            history = history.filterNot { it.packageName == packageName }
            if (resolved.blockKey !in blocklist) history = history + resolved
            history = history.takeLast(PREVIOUS_APP_HISTORY_MAX)
            RecentAppsHub.update(history)
        }
    }

    suspend fun previousApp(): Boolean {
        val current = currApp ?: return false
        if (history.isEmpty()) return false

        // 历史按包名去重：去掉当前前台那一条，末尾就是真正的「上一个」。
        // 前台被拉黑时它不在历史里，此时历史末尾也是「上一个」。
        val candidates = history.filterNot { it.packageName == current.packageName }
        val target = candidates.lastOrNull()
        if (target == null) {
            if (currPackageNameError()) return launchPackage(current)
            return false
        }
        if (!launchPackage(target)) return false
        record(target)
        currApp = target
        return true
    }

    fun onRelease() {
        PackageChangeReceiver.unregister(context, onPackageChanged)
        history = emptyList()
        RecentAppsHub.clear()
        currApp = null
        foregroundToken++
    }

    private fun record(app: RecentApp) {
        history = (history.filterNot { it.packageName == app.packageName } + app)
            .takeLast(PREVIOUS_APP_HISTORY_MAX)
        RecentAppsHub.update(history)
    }

    private fun currPackageNameError(): Boolean {
        val pkgName = rootInActiveWindowPackageName()
        return pkgName != currApp?.packageName
    }

    private fun hasLaunchIntent(packageName: String?): Boolean {
        val key = packageName ?: return false
        return key in (launchablePackages ?: return false)
    }
}
