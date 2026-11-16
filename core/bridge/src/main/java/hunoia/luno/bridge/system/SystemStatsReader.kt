package hunoia.luno.bridge.system

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import hunoia.luno.core.LunoLog
import java.io.File

/** 系统内存快照（单位 kB）。 */
data class MemoryStats(
    val totalKb: Long,
    val availKb: Long,
    /** `Cached`（不含 Buffers）；部分 ROM 不给读时为 0。 */
    val cachedKb: Long,
    val swapTotalKb: Long,
    val swapFreeKb: Long,
) {
    val usedKb: Long get() = (totalKb - availKb).coerceAtLeast(0L)
    val swapUsedKb: Long get() = (swapTotalKb - swapFreeKb).coerceAtLeast(0L)
    val usedFraction: Float
        get() = if (totalKb > 0L) usedKb.toFloat() / totalKb.coerceAtLeast(1L) else 0f
}

data class BatteryStats(val levelPct: Int, val temperatureC: Float?)

/**
 * 不需要任何权限的整机附加指标：读不到的字段为 null / 空，由 UI 决定隐藏。
 * 这里只读 `sysfs` 与系统服务，跨 uid 的进程级数据在 [ProcessStatsSampler]。
 */
data class DeviceStats(
    val gpuPct: Float? = null,
    /** SoC 温度（摄氏度）；读不到为 null。 */
    val temperatureC: Float? = null,
    /** 每个逻辑核的当前频率（MHz），下标 = 核序号；某核读不到为 null。 */
    val coreFreqMhz: List<Long?> = emptyList(),
    val battery: BatteryStats? = null,
    val downBytesPerSec: Long = 0L,
    val upBytesPerSec: Long = 0L,
)

/**
 * 整机指标读取器（内存 / GPU / 频率 / 电池 / 网速 / 自身占用）。
 *
 * 数据源与口径：
 *  - 内存：`/proc/meminfo` 为主（`MemTotal` / `MemAvailable`，缺口才用
 *    `ActivityManager.getMemoryInfo` 兜底）。`used = total - MemAvailable`，与 `free`、
 *    系统「已用」同一口径；`ActivityManager.availMem` 只是 `MemFree + Cached`，
 *    漏掉可回收的 slab / 页缓存水位，会把这台机器的已用算大。
 *  - GPU / 频率 / 温度：直接读 `sysfs`。各 ROM 权限不一，读不到就返回 null，
 *    不借 Shizuku 强行读——采集层保持「无权限也能出摘要」的降级路径。
 *  - 网速：`TrafficStats` 总量差分（自身可读）。
 *  - 自身占用：`getProcessMemoryInfo`（PSS）与 `Process.getElapsedCpuTime` 差分。
 *
 * 全部是廉价读取，供 1s 级刷新；进程级重活不在这里。
 */
class SystemStatsReader(context: Context) {

    private val appContext = context.applicationContext
    private val am = appContext.getSystemService(ActivityManager::class.java)
    private val memoryInfo = ActivityManager.MemoryInfo()

    private var cpuFreqFiles: List<File>? = null
    private var gpuPath: String? = null
    private var gpuResolved = false
    private var thermalPath: String? = null
    private var thermalResolved = false

    private var lastRxBytes = -1L
    private var lastTxBytes = -1L
    private var lastNetAtMs = -1L

    private var lastSelfCpuMs = -1L
    private var lastSelfAtMs = -1L

    private var lastBattery: BatteryStats? = null
    private var lastBatteryAtMs = 0L

    /** 系统内存；`/proc/meminfo` 读不到时缓存 / Swap 为 0，total / avail 走 AM 兜底。 */
    fun readMemory(): MemoryStats {
        var totalKb = 0L
        var availKb = 0L
        var cachedKb = 0L
        var swapTotalKb = 0L
        var swapFreeKb = 0L
        runCatching {
            File("/proc/meminfo").forEachLine { line ->
                val key = line.substringBefore(':', missingDelimiterValue = "")
                if (key.isEmpty()) return@forEachLine
                val value = line.substringAfter(':').trim()
                    .substringBefore(' ')
                    .toLongOrNull()
                    ?: return@forEachLine
                when (key) {
                    "MemTotal" -> totalKb = value
                    // 内核的 MemAvailable 已扣除不可回收部分，是「还能给新应用用多少」，
                    // 与系统 / 任务管理器同源。不要换回 MemFree + Cached。
                    "MemAvailable" -> availKb = value
                    "Cached" -> cachedKb = value
                    "SwapTotal" -> swapTotalKb = value
                    "SwapFree" -> swapFreeKb = value
                }
            }
        }.onFailure { LunoLog.w(LOG_TAG, "read /proc/meminfo failed: ${it.message}") }
        if (totalKb == 0L || availKb == 0L) {
            val manager = am
            if (manager != null) {
                runCatching { manager.getMemoryInfo(memoryInfo) }
                    .onSuccess {
                        if (totalKb == 0L) totalKb = memoryInfo.totalMem / 1024L
                        if (availKb == 0L) availKb = memoryInfo.availMem / 1024L
                    }
                    .onFailure { LunoLog.w(LOG_TAG, "getMemoryInfo failed: ${it.message}") }
            }
        }
        return MemoryStats(
            totalKb = totalKb,
            availKb = availKb,
            cachedKb = cachedKb,
            swapTotalKb = swapTotalKb,
            swapFreeKb = swapFreeKb,
        )
    }

    /** GPU / 频率 / 电池 / 网速的一次性采样。 */
    fun readDevice(): DeviceStats {
        // 网速是差分，一帧只能取一次（取两次第二次的窗口≈0）。
        val net = readNetRate()
        return DeviceStats(
            gpuPct = readGpuPct(),
            temperatureC = readTemperatureC(),
            coreFreqMhz = readCoreFreqMhz(),
            battery = readBattery(),
            downBytesPerSec = net.first,
            upBytesPerSec = net.second,
        )
    }

    /** 本进程 PSS（kB），无需权限；失败时用 `Debug.getPss` 兜底。 */
    fun selfPssKb(): Long {
        val manager = am ?: return Debug.getPss()
        val info = runCatching { manager.getProcessMemoryInfo(intArrayOf(Process.myPid())) }
            .getOrNull()
            ?.firstOrNull()
            ?: return Debug.getPss()
        return info.totalPss.toLong()
    }

    /**
     * 本进程 CPU 占「整机容量」的百分比（与进程列表同口径）。
     * 第一次调用没有基线，返回 -1。
     */
    fun selfCpuPct(): Float {
        val now = SystemClock.elapsedRealtime()
        val cpuMs = Process.getElapsedCpuTime()
        val prevAt = lastSelfAtMs
        val prevCpu = lastSelfCpuMs
        lastSelfAtMs = now
        lastSelfCpuMs = cpuMs
        if (prevAt < 0L || now <= prevAt) return -1f
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val pct = (cpuMs - prevCpu).toFloat() / ((now - prevAt) * cores) * 100f
        return pct.coerceIn(0f, 100f)
    }

    // ---- GPU ----

    private fun readGpuPct(): Float? {
        gpuPath?.let { path ->
            readBusyPercent(path)?.let { return it }
            // 节点消失 / 瞬时不可读：丢掉缓存路径，下一帧重新探测，不要永久锁死在 null。
            gpuPath = null
            gpuResolved = false
        }
        if (gpuResolved) return null
        gpuResolved = true
        for (path in GPU_CANDIDATES) {
            val pct = readBusyPercent(path)
            if (pct != null) {
                gpuPath = path
                return pct
            }
        }
        val devfreq = findDevfreqGpuLoad()
        if (devfreq != null) {
            gpuPath = devfreq
            return readBusyPercent(devfreq)
        }
        return null
    }

    /**
     * `sysfs` 的 busy 值有两种形态：百分比单值（`37` / `37%`）与 `busy total` 双值。
     * 双值按百分比换算，单值只在 0..100 内才认。
     */
    private fun readBusyPercent(path: String): Float? {
        val text = runCatching { File(path).readText() }.getOrNull()?.trim().orEmpty()
        if (text.isEmpty()) return null
        val numbers = text.replace("%", " ")
            .split(' ', '\t')
            .mapNotNull { it.toLongOrNull() }
        if (numbers.isEmpty()) return null
        return when {
            numbers.size >= 2 && numbers[1] > 0L ->
                (numbers[0] * 100f / numbers[1]).coerceIn(0f, 100f)
            numbers[0] in 0L..100L -> numbers[0].toFloat()
            else -> null
        }
    }

    private fun findDevfreqGpuLoad(): String? {
        val dirs = runCatching { File("/sys/class/devfreq").listFiles() }.getOrNull().orEmpty()
        for (dir in dirs) {
            val name = dir.name.lowercase()
            if (GPU_NODE_HINTS.none { name.contains(it) }) continue
            val load = File(dir, "load")
            if (load.canRead()) return load.path
        }
        return null
    }

    // ---- CPU 频率 ----

    private fun readCoreFreqMhz(): List<Long?> {
        val files = cpuFreqFiles ?: buildCpuFreqFiles().takeIf { it.isNotEmpty() }?.also { cpuFreqFiles = it }
        if (files.isNullOrEmpty()) return emptyList()
        // 保留 null 占位：下标仍是核序号，某一核瞬时读不到不会让后面的核错位。
        return files.map { file ->
            runCatching { file.readText().trim().toLongOrNull() }.getOrNull()?.div(1000L)
        }
    }

    private fun buildCpuFreqFiles(): List<File> {
        val cpuDirs = runCatching { File("/sys/devices/system/cpu").listFiles() }
            .getOrNull()
            .orEmpty()
            .filter { it.isDirectory && CPU_DIR_REGEX.matches(it.name) }
            .sortedBy { it.name.removePrefix("cpu").toIntOrNull() ?: Int.MAX_VALUE }
        return cpuDirs.mapNotNull { dir ->
            File(dir, "cpufreq/scaling_cur_freq").takeIf { it.canRead() }
                ?: File(dir, "cpufreq/cpuinfo_cur_freq").takeIf { it.canRead() }
        }
    }

    // ---- 温度 ----

    /**
     * SoC 温度（摄氏度）：只认 `type` 含 soc / cpu / gpu / tsens 等关键字的 thermal zone，
     * 读不到返回 null。电池温度在 [BatteryStats] 里单独给。
     */
    fun readTemperatureC(): Float? {
        thermalPath?.let { path ->
            readTempC(path)?.let { return it }
            // 同上：zone 被移除 / 瞬时不可读时清缓存重新探测。
            thermalPath = null
            thermalResolved = false
        }
        if (thermalResolved) return null
        thermalResolved = true
        val zones = runCatching { File("/sys/class/thermal").listFiles() }
            .getOrNull()
            .orEmpty()
            .filter { it.isDirectory && it.name.startsWith("thermal_zone") }
        for (zone in zones) {
            val type = runCatching { File(zone, "type").readText() }.getOrNull()
                ?.trim()
                ?.lowercase()
                ?: continue
            if (SOC_ZONE_HINTS.none { type.contains(it) }) continue
            val path = File(zone, "temp").path
            val value = readTempC(path) ?: continue
            thermalPath = path
            return value
        }
        return null
    }

    private fun readTempC(path: String): Float? {
        val raw = runCatching { File(path).readText().trim().toLongOrNull() }.getOrNull() ?: return null
        val celsius = when {
            raw > 1000L -> raw / 1000f
            raw > 200L -> raw / 10f
            else -> raw.toFloat()
        }
        return celsius.takeIf { it in 5f..120f }
    }

    // ---- 电池 ----

    private fun readBattery(): BatteryStats? {
        // 电量 / 电池温度变化远慢于 1s 刷新：sticky 广播的跨进程拷贝没必要每秒做一次。
        val now = SystemClock.elapsedRealtime()
        if (lastBatteryAtMs != 0L && now - lastBatteryAtMs < BATTERY_REFRESH_MS) return lastBattery
        lastBatteryAtMs = now
        val sticky = runCatching {
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        if (sticky == null) {
            lastBattery = null
            return null
        }
        val level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) {
            lastBattery = null
            return null
        }
        val tempTenths = sticky.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        return BatteryStats(
            levelPct = (level * 100 / scale).coerceIn(0, 100),
            temperatureC = if (tempTenths == Int.MIN_VALUE) null else tempTenths / 10f,
        ).also { lastBattery = it }
    }

    // ---- 网速 ----

    private fun readNetRate(): Pair<Long, Long> {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val now = SystemClock.elapsedRealtime()
        val prevRx = lastRxBytes
        val prevTx = lastTxBytes
        val prevAt = lastNetAtMs
        lastRxBytes = rx
        lastTxBytes = tx
        lastNetAtMs = now
        if (rx < 0L || tx < 0L) return 0L to 0L
        if (prevRx < 0L || prevAt < 0L || now <= prevAt) return 0L to 0L
        val seconds = (now - prevAt) / 1000f
        if (seconds <= 0f) return 0L to 0L
        val down = ((rx - prevRx).coerceAtLeast(0L) / seconds).toLong()
        val up = ((tx - prevTx).coerceAtLeast(0L) / seconds).toLong()
        return down to up
    }

    private companion object {
        val LOG_TAG = "SystemStats"
        val CPU_DIR_REGEX = Regex("cpu[0-9]+")

        /** 电池 sticky 广播的复用窗口；电量只按 1% 跳变，10s 足够。 */
        const val BATTERY_REFRESH_MS = 10_000L

        /** Adreno / Mali / Exynos 常见节点；顺序即优先级。 */
        val GPU_CANDIDATES = listOf(
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
            "/sys/class/kgsl/kgsl-3d0/gpubusy",
            "/sys/class/kgsl/kgsl-3d0/gpu_busy",
            "/sys/class/misc/mali0/device/utilization",
            "/sys/class/misc/mali0/device/gpu_busy_percentage",
            "/proc/mali/utilization",
            "/sys/kernel/gpu/gpu_busy",
            "/sys/kernel/gpu/gpu_busy_percentage",
        )

        val GPU_NODE_HINTS = listOf("gpu", "kgsl", "mali", "midgard", "panfrost")

        /** thermal zone 的 `type` 关键字（避开电池 / 充电 IC / 皮肤温度）。 */
        val SOC_ZONE_HINTS = listOf("soc", "cpu", "gpu", "tsens", "ap-", "cluster", "core")
    }
}
