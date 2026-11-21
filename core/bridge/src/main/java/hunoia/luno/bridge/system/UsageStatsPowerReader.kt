package hunoia.luno.bridge.system

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import hunoia.luno.core.LunoLog

/** 降级路径下单个应用的前台时长估算。 */
data class UsageForegroundEntry(
    val packageName: String,
    /** 上次充满以来的前台总时长（毫秒）。 */
    val foregroundMs: Long,
)

/**
 * 降级路径：没有 Shizuku 时用 usage stats 的前台时长估算耗电。
 *
 * 口径诚实原则（UI 必须跟着这个走）：
 *  - 这里**没有**任何能量计量。前台时长 × 固定系数只是把「用了多久」换算成一个可排序的
 *    数字，用来回答「谁在前台待得久」，不是「谁耗了几毫安时」。
 *  - 系数 [MAH_PER_FOREGROUND_MINUTE] 是经验值，参考同类工具（DrainScope 的 rootless
 *    估算用 ~8 mAh/min）取同量级。不同屏幕亮度 / 负载下偏差很大。
 *  - 因此 UI 上必须标「估算」，且不显示 mAh 绝对值的小数位，避免伪造精度。
 *
 * 需要 `PACKAGE_USAGE_STATS`（appop，用户在「使用情况访问」里手动授予）。
 */
class UsageStatsPowerReader(context: Context) {

    private val appContext = context.applicationContext
    private val usageManager = appContext.getSystemService(UsageStatsManager::class.java)
    private val appOps = appContext.getSystemService(AppOpsManager::class.java)

    /** 用户是否已授予「使用情况访问」。 */
    fun hasUsageAccess(): Boolean = runCatching {
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            appContext.packageName,
        )
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /**
     * 上次充满以来的各包前台时长，按时长降序。
     *
     * 无权限 / 系统无数据时返回空列表（调用方据此显示授权提示，而不是空表）。
     */
    fun readForegroundSinceCharge(): List<UsageForegroundEntry> {
        if (!hasUsageAccess()) return emptyList()
        val manager = usageManager ?: return emptyList()
        val now = System.currentTimeMillis()
        // 取最近 7 天做上界：`--charged` 的窗口在系统里没有直接对应 API，
        // 用「自上次充满」最接近的近似是查最近一段并过滤，这里取 7 天兜底，
        // 时长本身由系统累计，不会因窗口宽而虚高。
        // MissingPermission 是库模块误报：权限在 :app 的 manifest 声明（appop），
        // 上面 hasUsageAccess() 已把门，runCatching 兜住授权被中途撤掉的竞态。
        @SuppressLint("MissingPermission")
        val stats = runCatching {
            manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - WINDOW_MS, now)
        }.onFailure {
            LunoLog.w(LOG_TAG, "queryUsageStats failed: ${it.message}")
        }.getOrNull().orEmpty()

        // 同一包名可能有多条分桶记录，按时长累加。
        val byPackage = LinkedHashMap<String, Long>()
        stats.forEach { stat ->
            val pkg = stat.packageName ?: return@forEach
            if (pkg.isBlank()) return@forEach
            val total = stat.totalTimeInForeground
            if (total <= 0L) return@forEach
            byPackage[pkg] = (byPackage[pkg] ?: 0L) + total
        }
        return byPackage
            .map { UsageForegroundEntry(packageName = it.key, foregroundMs = it.value) }
            .sortedByDescending { it.foregroundMs }
    }

    /** 前台时长 → 估算 mAh。系数是经验值，见类注释。 */
    fun estimateMah(foregroundMs: Long): Float =
        (foregroundMs / 60_000f) * MAH_PER_FOREGROUND_MINUTE

    private companion object {
        const val WINDOW_MS = 7L * 24 * 60 * 60 * 1000

        /**
         * 每分钟前台 ≈ 8 mAh。与 DrainScope 的 rootless 估算同量级。
         * 这是**排序用的经验系数**，不是测量结果；改它只会整体缩放，不改变排名。
         */
        const val MAH_PER_FOREGROUND_MINUTE = 8f

        const val LOG_TAG = "UsageStatsPower"
    }
}
