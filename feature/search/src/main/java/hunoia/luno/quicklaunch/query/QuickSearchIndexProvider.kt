package hunoia.luno.quicklaunch.query

import android.content.Context
import android.os.SystemClock
import hunoia.luno.bridge.system.PackageChangeReceiver

/**
 * 进程级快速搜索索引缓存。QuickSearchActivity 是 noHistory：每个实例都 new 一个 QuickSearchIndex
 * 会让每次打开都重新全量查 PackageManager + 全部快捷方式（几百毫秒 CPU/Binder），故在 :service
 * 进程内复用同一份索引。
 * - 包变化（装/卸/覆盖）只标记过期，不在后台立刻重扫（避免搜索关着时白耗电）；面板打开期间的
 *   实时刷新由 QuickSearchActivity 自己的包监听负责。
 * - 10 分钟 TTL 兜住「切换默认桌面/语言变化」等不广播的情况。
 */
object QuickSearchIndexProvider {

    private const val TTL_MS = 10 * 60 * 1000L

    private val lock = Any()
    private var cached: QuickSearchIndex? = null
    private var cachedAtMs = 0L
    private var packageObserverRegistered = false

    fun get(context: Context): QuickSearchIndex {
        val appContext = context.applicationContext ?: context
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            cached?.let { existing ->
                if (now - cachedAtMs < TTL_MS) return existing
            }
            val fresh = QuickSearchIndex(appContext)
            cached = fresh
            cachedAtMs = now
            if (!packageObserverRegistered) {
                packageObserverRegistered = true
                PackageChangeReceiver.register(appContext) {
                    synchronized(lock) { cached }?.markAppsStale()
                }
            }
            return fresh
        }
    }
}
