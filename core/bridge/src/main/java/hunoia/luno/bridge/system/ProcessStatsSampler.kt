package hunoia.luno.bridge.system

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Process
import hunoia.luno.bridge.ui.assertNotMainThread
import hunoia.luno.core.LunoLog
import hunoia.luno.shizuku.ShizukuManager

/** 一帧 CPU 快照；[hasSample] 为 false 时两个百分比都不可用。 */
data class CpuStats(
    /** 整机 CPU 占用百分比（0..100）。 */
    val totalPct: Float = 0f,
    /** 每个逻辑核的占用百分比，按核序号升序；没有基线时为空。 */
    val perCorePct: List<Float> = emptyList(),
    val hasSample: Boolean = false,
)

/** 一个应用（按 uid 聚合）的进程级占用。 */
data class AppProcessUsage(
    val uid: Int,
    val packageName: String,
    val label: String,
    /** 0 = 主用户；分身 = 1 起（uid / 100000）。 */
    val userId: Int,
    val isSystem: Boolean,
    /** 占整机 CPU 容量的百分比（0..100）。 */
    val cpuPct: Float,
    /**
     * 进程内存（kB）：优先 AMS 的 PSS，读不到时退回 `VmRSS`。
     *
     * PSS 按共享页的使用者数均摊，可跨进程相加，和系统任务管理器同口径；
     * RSS 会把 libc / dex / 共享内存重复计进每个进程，列表合计明显偏大。
     */
    val memKb: Long,
)

data class ProcessStats(
    val cpu: CpuStats,
    val apps: List<AppProcessUsage>,
    /** 本帧读到的进程数（含被过滤掉的系统 / 内核进程）。 */
    val processCount: Int,
)

/**
 * 进程级采样器：一次 shell 读全量 `/proc`，应用侧按 uid 聚合。
 *
 * 数据源与基线口径：
 *  - 必须 Shizuku：应用自己读不了 `/proc/stat` 与其它 uid 的 `/proc/<pid>/` 下的文件（Permission denied）。
 *  - 一次 shell 进程内读完 `/proc/stat`（总 + 每核）与每个进程的 `uid / VmRSS / utime+stime / starttime`，
 *    不逐核 / 逐文件 fork。
 *  - **进程内存优先 PSS**：shell 读不了别的 uid 的 smaps（ptrace 拒绝），改由
 *    [ShizukuManager.processPssByPid] 经 AMS `getProcessPss` 由 system_server 代读；
 *    不可用时退回本帧的 VmRSS（列表合计会偏大，但不缺行）。
 *  - **基线按 `pid + starttime`**：同一 uid 的进程重启后 ticks 归零不会把上一轮的累计值算进这一帧；
 *    新出现的进程以当前 ticks 为基线（首帧 0%），不再出现「刚启动就 100%」。
 *  - 采样失败（shell 失败 / 输出缺 `TOTAL`）返回 null 且**不推进任何基线**，
 *    调用方保留上一帧，指标不会闪成 0。
 *
 * 只读，不改系统状态；采样必须在 IO 线程调用。
 */
class ProcessStatsSampler(context: Context) {

    private val appContext = context.applicationContext
    private val pm = appContext.packageManager

    private var lastTotalTicks = -1L
    private var lastIdleTicks = -1L
    private val lastCoreTicks = HashMap<Int, LongArray>()
    private val lastProcTicks = HashMap<PidKey, Long>()
    private val uidMeta = HashMap<Int, AppMeta?>()

    /** 授权状态翻转时清基线：降级 / 全量两套数据不能混算差值。 */
    fun resetBaseline() {
        lastTotalTicks = -1L
        lastIdleTicks = -1L
        lastCoreTicks.clear()
        lastProcTicks.clear()
    }

    /** 返回 null = 本帧失败，调用方应保留上一帧。 */
    suspend fun sample(): ProcessStats? {
        assertNotMainThread("sysMonitor.sample")
        val result = ShizukuManager.executeShell(SHIZUKU_SCRIPT, maxChars = ShellMaxChars)
        if (!result.isSuccess) {
            if (result.errorMessage.isNotEmpty()) {
                LunoLog.w(LOG_TAG, "sample failed: ${result.errorMessage}")
            }
            return null
        }
        val frame = parse(result.stdout) ?: return null
        if (frame.totalTicks <= lastTotalTicks) {
            // /proc/stat 还没走完一个 tick（或时钟粒度不足）：不算差值，也不动基线。
            return ProcessStats(CpuStats(hasSample = false), lastApps, frame.processCount)
        }

        val totalDelta = frame.totalTicks - lastTotalTicks
        val prevIdle = lastIdleTicks
        val idleDelta = if (prevIdle >= 0L) (frame.idleTicks - prevIdle).coerceAtLeast(0L) else 0L
        val hasSample = prevIdle >= 0L && totalDelta > 0L
        val totalPct = if (hasSample) {
            ((totalDelta - idleDelta).toFloat() / totalDelta * 100f).coerceIn(0f, 100f)
        } else {
            0f
        }
        val perCore = if (hasSample) {
            frame.cores.mapNotNull { core ->
                val prev = lastCoreTicks[core.index] ?: return@mapNotNull null
                val delta = core.totalTicks - prev[0]
                if (delta <= 0L) return@mapNotNull null
                val idle = (core.idleTicks - prev[1]).coerceAtLeast(0L)
                ((delta - idle).toFloat() / delta * 100f).coerceIn(0f, 100f)
            }
        } else {
            emptyList()
        }

        // PSS 走 AMS（system_server 代读 smaps）；拿不到就整帧回退 VmRSS。
        val pssByPid = ShizukuManager.processPssByPid()
        val aggByUid = LinkedHashMap<Int, FloatArray>()
        val currentTicks = HashMap<PidKey, Long>(frame.procs.size * 2)
        for (proc in frame.procs) {
            val key = PidKey(proc.pid, proc.startTicks)
            val prev = lastProcTicks[key]
            val delta = if (prev != null) (proc.cpuTicks - prev).coerceAtLeast(0L) else 0L
            val pct = if (hasSample) delta.toFloat() / totalDelta * 100f else 0f
            val agg = aggByUid.getOrPut(proc.uid) { FloatArray(2) }
            agg[0] += pct
            agg[1] += (pssByPid?.get(proc.pid) ?: proc.rssKb).toFloat()
            currentTicks[key] = proc.cpuTicks
        }

        val apps = ArrayList<AppProcessUsage>(aggByUid.size)
        for ((uid, agg) in aggByUid) {
            val meta = metaForUid(uid) ?: continue
            apps.add(
                AppProcessUsage(
                    uid = uid,
                    packageName = meta.packageName,
                    label = meta.label,
                    userId = meta.userId,
                    isSystem = meta.isSystem,
                    cpuPct = agg[0].coerceIn(0f, 100f),
                    memKb = agg[1].toLong(),
                )
            )
        }

        // 成功的一帧才推进基线（失败 / 无进展时保留旧值）。
        lastTotalTicks = frame.totalTicks
        lastIdleTicks = frame.idleTicks
        lastCoreTicks.clear()
        for (core in frame.cores) lastCoreTicks[core.index] = longArrayOf(core.totalTicks, core.idleTicks)
        lastProcTicks.clear()
        lastProcTicks.putAll(currentTicks)
        lastApps = apps

        return ProcessStats(
            cpu = CpuStats(totalPct = totalPct, perCorePct = perCore, hasSample = hasSample),
            apps = apps,
            processCount = frame.processCount,
        )
    }

    /** uid → 包名 / 标签 / 是否系统应用；无包名（isolated 等）返回 null 并缓存。 */
    private fun metaForUid(uid: Int): AppMeta? {
        if (uidMeta.containsKey(uid)) return uidMeta[uid]
        val meta = runCatching {
            val pkg = pm.getPackagesForUid(uid)?.firstOrNull { it.isNotBlank() }
                ?: return@runCatching null
            val info = pm.getApplicationInfo(pkg, 0)
            val label = runCatching { pm.getApplicationLabel(info).toString() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: pkg
            val systemFlag = (info.flags and (
                ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
                )) != 0
            AppMeta(
                packageName = pkg,
                label = label,
                userId = uid / 100000,
                isSystem = systemFlag || uid < Process.FIRST_APPLICATION_UID,
            )
        }.onFailure {
            LunoLog.w(LOG_TAG, "resolve uid $uid failed: ${it.message}")
        }.getOrNull()
        uidMeta[uid] = meta
        return meta
    }

    private data class PidKey(val pid: Int, val startTicks: Long)

    private data class AppMeta(
        val packageName: String,
        val label: String,
        val userId: Int,
        val isSystem: Boolean,
    )

    private class ParsedProc(
        val pid: Int,
        val uid: Int,
        val cpuTicks: Long,
        /** `/proc/<pid>/status` 的 VmRSS；仅当 AMS PSS 不可用时作为回退。 */
        val rssKb: Long,
        val startTicks: Long,
    )

    private class ParsedCore(val index: Int, val totalTicks: Long, val idleTicks: Long)

    private class ParsedFrame(
        val totalTicks: Long,
        val idleTicks: Long,
        val cores: List<ParsedCore>,
        val procs: List<ParsedProc>,
        val processCount: Int,
    )

    private companion object {
        val LOG_TAG = "SystemStats"

        /** shell 输出上限保护（数百进程 × 一行约 30B）。 */
        const val ShellMaxChars = 200_000

        /**
         * 解析 shell 输出。格式：
         * ```
         * TOTAL <total_ticks> <idle_ticks>
         * CORE <index> <total_ticks> <idle_ticks>
         * <pid> <uid> <cpu_ticks> <rss_kb> <start_ticks>
         * ```
         */
        fun parse(stdout: String): ParsedFrame? {
            var totalTicks = -1L
            var idleTicks = -1L
            val cores = ArrayList<ParsedCore>(16)
            val procs = ArrayList<ParsedProc>(256)
            for (line in stdout.lineSequence()) {
                val parts = line.trim().split(' ')
                when (parts[0]) {
                    "TOTAL" -> {
                        totalTicks = parts.getOrNull(1)?.toLongOrNull() ?: -1L
                        idleTicks = parts.getOrNull(2)?.toLongOrNull() ?: -1L
                    }
                    "CORE" -> {
                        val index = parts.getOrNull(1)?.toIntOrNull() ?: continue
                        val total = parts.getOrNull(2)?.toLongOrNull() ?: continue
                        val idle = parts.getOrNull(3)?.toLongOrNull() ?: continue
                        cores.add(ParsedCore(index, total, idle))
                    }
                    "" -> Unit
                    else -> {
                        val pid = parts[0].toIntOrNull() ?: continue
                        val uid = parts.getOrNull(1)?.toIntOrNull() ?: continue
                        val cpu = parts.getOrNull(2)?.toLongOrNull() ?: 0L
                        val rss = parts.getOrNull(3)?.toLongOrNull() ?: 0L
                        val start = parts.getOrNull(4)?.toLongOrNull() ?: 0L
                        procs.add(ParsedProc(pid, uid, cpu, rss, start))
                    }
                }
            }
            if (totalTicks < 0L || idleTicks < 0L) return null
            return ParsedFrame(
                totalTicks = totalTicks,
                idleTicks = idleTicks,
                cores = cores.sortedBy { it.index },
                procs = procs,
                processCount = procs.size,
            )
        }

        /**
         * 一次 shell 读全量（mksh 内建，无外部进程）：
         * `/proc/stat` 取总 / 每核，`/proc/<pid>/status` 取 uid 与 VmRSS，
         * `/proc/<pid>/stat` 取 utime+stime 与 starttime（重启识别）。
         *
         * 位置参数口径：`stat` 去掉 `pid (comm) ` 之后，第 n 个位置 = 内核 stat 的第 n+2 个字段，
         * 因此 utime / stime / starttime 分别是 12 / 13 / 20。`set -- $t 0 0 …` 补齐尾部，
         * 保证进程刚好在读取瞬间退出时 `${20}` 仍存在。
         *
         * `§` 是 `$` 的占位符（规避 Kotlin 原始字符串插值），最后整体替换。
         */
        val SHIZUKU_SCRIPT = """
while read -r k a b c d e f g h i; do
  case "§k" in
    cpu) echo "TOTAL §((a+b+c+d+e+f+g+h)) §((d+e))" ;;
    cpu[0-9]*) echo "CORE §{k#cpu} §((a+b+c+d+e+f+g+h)) §((d+e))" ;;
  esac
done < /proc/stat
for p in /proc/[0-9]*; do
  pid=§{p##*/}
  uid=0; rss=0
  while read -r k v r; do
    case "§k" in
      Uid:) uid=§v ;;
      VmRSS:) rss=§v ;;
    esac
  done < /proc/§pid/status
  if [ "§uid" -lt 1000 ]; then continue; fi
  if ! read -r s2 < /proc/§pid/stat; then continue; fi
  t=§{s2##*)}
  set -- §t 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
  cpu=§(( §{12} + §{13} ))
  start=§{20}
  echo "§pid §uid §cpu §rss §start"
done
""".trimIndent().replace("§", "$")
    }

    /** 上一帧的应用列表：采样失败 / 无 tick 进展时原样返回，避免列表闪空。 */
    private var lastApps: List<AppProcessUsage> = emptyList()
}
