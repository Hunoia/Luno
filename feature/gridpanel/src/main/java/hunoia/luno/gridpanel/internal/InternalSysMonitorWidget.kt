package hunoia.luno.gridpanel.internal

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.imageLoader
import hunoia.luno.bridge.system.AppIcons
import hunoia.luno.gridpanel.R
import hunoia.luno.gridpanel.geometry.ItemCorner
import hunoia.luno.gridpanel.ui.gridPanelOnCardColor
import hunoia.luno.shizuku.ShizukuManager
import hunoia.luno.ui.icon.rememberAppDrawable
import hunoia.luno.ui.icon.rememberAppLabel
import hunoia.luno.ui.theme.containerShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 资源监控部件：内存/CPU 系统占用 + 各应用进程级占用列表。
 *
 * 数据源与权限（真机实测，HyperOS `/proc` 策略极严）：
 *  - 系统内存：`ActivityManager.getMemoryInfo`（瞬时值，无需任何权限）。
 *  - 系统 CPU + 各应用 CPU/RSS：**必须 Shizuku**。应用自身读不了 `/proc/stat` 与
 *    其它应用进程的 `/proc/<pid>/` 下的文件（Permission denied）；借 Shizuku 的 shell 身份
 *    一次性跑脚本读全量，输出 `TOTAL` + 每进程一行，应用侧再聚合。
 *  - PSS（`smaps_rollup`）连 shell 都读不了（Permission denied），内存用 RSS(VmRSS) 兜底。
 *  - Shizuku 未授权时降级：仅显示系统内存汇总 + 「点此授权」提示，CPU/应用列表不可用。
 *
 * 进程范围：uid ≥ [Process.FIRST_APPLICATION_UID] 的用户应用（含本应用自身），
 * 排除系统守护与内核线程；按 uid 聚合成应用行（共享 uid 取首个包名）。
 */

/** 1s 刷新间隔；CPU 差值采样需 ≥1s 才有意义。 */
private const val SysRefreshMs = 1000L

/** shell 输出的行数上限保护（约数百进程 × 一行 20B，200K 富余）。 */
private const val ShellMaxChars = 200_000

/** 列表行高：固定高度，图标 + 文字 + 双数值。 */
private val SysRowH = 30.dp
private val SysIconSize = 20.dp

/** 一个应用（按 uid 聚合）的占用快照。 */
data class AppUsage(
    val packageName: String,
    /** 占系统总 CPU 容量的百分比（0..100，所有应用合计 ≤ 系统 CPU%）。 */
    val cpuPct: Float,
    /** RSS（VmRSS，常驻集）kB；PSS 在目标 ROM 拿不到，用 RSS 作为主显内存口径。 */
    val rssKb: Long,
)

/** 一帧完整快照；应用列表已按综合分（内存优先 70/30）降序。 */
data class SysMonitorSnapshot(
    val totalMemKb: Long,
    val usedMemKb: Long,
    val cpuPct: Float,
    /** 首帧无 CPU 基线时为 false，UI 显示「—」。 */
    val hasCpuSample: Boolean,
    val apps: List<AppUsage>,
)

/** 单个 uid 的进程聚合。 */
private class UidAgg {
    var cpuTicks = 0L
    var rssKb = 0L
}

/**
 * 采集器：只在 IO 线程调用。持有上一帧 CPU 基线（总 + 按 uid）做差值。
 * 基线在每次采样后推进，保证连续帧间隔恒为刷新周期。
 */
private class SysMonitorSampler(private val context: Context) {
    private val pm = context.packageManager
    private val am = context.getSystemService(ActivityManager::class.java)
    private var lastTotalCpu = -1L
    private var lastIdleCpu = -1L
    private val lastCpuByUid = HashMap<Int, Long>()
    /** uid → 代表包名（跨进程 Binder 查询缓存；uid 稳定不增长）。 */
    private val uidToPackage = HashMap<Int, String?>()

    /** 授权状态翻转时清基线，避免降级/全量两套 pid 集合并出的差值跳变。 */
    fun resetCpuBaseline() {
        lastTotalCpu = -1L
        lastIdleCpu = -1L
        lastCpuByUid.clear()
    }

    /** Shizuku 已授权：以 shell 身份读 /proc 全量，覆盖所有用户应用。 */
    suspend fun sample(): SysMonitorSnapshot {
        val result = ShizukuManager.executeShell(SHIZUKU_SCRIPT, maxChars = ShellMaxChars)
        val (totalCpu, idleCpu, aggByUid) = if (result.isSuccess) {
            parseShellOutput(result.stdout)
        } else {
            Triple(-1L, -1L, emptyMap())
        }
        return build(totalCpu, idleCpu, aggByUid)
    }

    /** Shizuku 未授权：只有系统内存；CPU 与应用列表不可用。 */
    fun sampleMemoryOnly(): SysMonitorSnapshot = build(-1L, -1L, emptyMap())

    private fun build(totalCpu: Long, idleCpu: Long, aggByUid: Map<Int, UidAgg>): SysMonitorSnapshot {
        val memInfo = am?.let {
            runCatching { ActivityManager.MemoryInfo().also(it::getMemoryInfo) }.getOrNull()
        }
        val totalMemKb = (memInfo?.totalMem ?: 0L) / 1024L
        val usedMemKb = (totalMemKb - (memInfo?.availMem ?: 0L) / 1024L).coerceAtLeast(0L)

        val hasSample = lastTotalCpu >= 0 && totalCpu > lastTotalCpu
        val totalDelta = if (hasSample) totalCpu - lastTotalCpu else 0L
        val idleDelta = if (hasSample) idleCpu - lastIdleCpu else 0L
        val sysCpuPct = if (hasSample && totalDelta > 0) {
            ((totalDelta - idleDelta).toFloat() / totalDelta) * 100f
        } else {
            0f
        }

        val apps = ArrayList<AppUsage>(aggByUid.size)
        for ((uid, agg) in aggByUid) {
            val pkg = packageForUid(uid) ?: continue
            val cpuPct = if (hasSample && totalDelta > 0) {
                val prev = lastCpuByUid[uid] ?: 0L
                // 进程重启/消失后 ticks 可能回退，钳到 0。
                ((agg.cpuTicks - prev).coerceAtLeast(0L).toFloat() / totalDelta * 100f)
                    .coerceIn(0f, 100f)
            } else {
                0f
            }
            apps.add(AppUsage(pkg, cpuPct, agg.rssKb))
        }
        // 综合分：内存优先 70/30（rss 占系统内存比例 + cpu 占比）。
        val memTotal = totalMemKb.toFloat().coerceAtLeast(1f)
        apps.sortByDescending { 0.7f * (it.rssKb.toFloat() / memTotal) + 0.3f * (it.cpuPct / 100f) }

        // 推进基线供下一帧差值。
        lastTotalCpu = totalCpu
        lastIdleCpu = idleCpu
        lastCpuByUid.clear()
        for ((uid, agg) in aggByUid) lastCpuByUid[uid] = agg.cpuTicks

        return SysMonitorSnapshot(
            totalMemKb = totalMemKb,
            usedMemKb = usedMemKb,
            cpuPct = sysCpuPct,
            hasCpuSample = hasSample,
            apps = apps,
        )
    }

    /** uid → 代表包名；无包名（isolated 等）返回 null 并缓存，避免每帧重复 Binder。 */
    private fun packageForUid(uid: Int): String? = uidToPackage.getOrPut(uid) {
        runCatching { pm.getPackagesForUid(uid) }
            .getOrNull()
            ?.firstOrNull { it.isNotBlank() }
    }
}

/**
 * 解析 shell 脚本输出：
 * ```
 * TOTAL <total_cpu_ticks> <idle_ticks>
 * <pid> <uid> <cpu_ticks> <rss_kb>
 * ```
 */
private fun parseShellOutput(stdout: String): Triple<Long, Long, Map<Int, UidAgg>> {
    var totalCpu = -1L
    var idleCpu = -1L
    val aggByUid = LinkedHashMap<Int, UidAgg>()
    for (line in stdout.lineSequence()) {
        val parts = line.trim().split(' ')
        if (parts.isEmpty() || parts[0].isEmpty()) continue
        when (parts[0]) {
            "TOTAL" -> {
                totalCpu = parts.getOrNull(1)?.toLongOrNull() ?: -1L
                idleCpu = parts.getOrNull(2)?.toLongOrNull() ?: -1L
            }
            else -> {
                val pid = parts[0].toIntOrNull() ?: continue
                val uid = parts.getOrNull(1)?.toIntOrNull() ?: continue
                if (uid < Process.FIRST_APPLICATION_UID) continue
                val cpu = parts.getOrNull(2)?.toLongOrNull() ?: 0L
                val rss = parts.getOrNull(3)?.toLongOrNull() ?: 0L
                val agg = aggByUid.getOrPut(uid) { UidAgg() }
                agg.cpuTicks += cpu
                agg.rssKb += rss
            }
        }
    }
    return Triple(totalCpu, idleCpu, aggByUid)
}

/**
 * 一次性 shell 脚本（mksh 内建实现，无外部进程开销）：
 * 读 `/proc/stat` 总 CPU + 每进程 uid/cpu/rss，跨 uid 读取依赖 Shizuku 的 shell 身份。
 * `set -- $rest 0 0 ...` 补齐尾部，保证 `${12}`/`${13}`（utime/stime）恒存在。
 * 用 `§` 作 `$` 占位再整体替换，规避 Kotlin 原始字符串的 `$` 插值歧义；
 * 脚本里的字面 `@`（`"$@"`）与占位符不冲突，替换后仍为 `"$@"`。
 */
private val SHIZUKU_SCRIPT = """
read -r s < /proc/stat
set -- §s
if [ §# -gt 0 ]; then shift; fi
total=0; idle=0; i=0
for v in "§@"; do
  total=§((total + v))
  i=§((i + 1))
  if [ "§i" -eq 4 ] || [ "§i" -eq 5 ]; then idle=§((idle + v)); fi
done
echo "TOTAL §total §idle"
for p in /proc/[0-9]*; do
  pid=§{p##*/}
  uid=0; rss=0
  while read -r k v r; do
    case "§k" in
      Uid:) uid=§v ;;
      VmRSS:) rss=§v ;;
    esac
  done < /proc/§pid/status
  if [ "§uid" -lt 10000 ]; then continue; fi
  if ! read -r s2 < /proc/§pid/stat; then continue; fi
  t=§{s2##*)}
  set -- §t 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
  cpu=§(( §{12} + §{13} ))
  echo "§pid §uid §cpu §rss"
done
""".trimIndent().replace("§", "$")

// ---- UI ----

@Composable
internal fun SysMonitorCell(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val onCard = gridPanelOnCardColor
    val accent = MaterialTheme.colorScheme.primary
    val scope = rememberCoroutineScope()
    val status by ShizukuManager.statusFlow.collectAsState()
    val shizukuOn = status.isReady
    val sampler = remember { SysMonitorSampler(context.applicationContext) }
    var snapshot by remember { mutableStateOf<SysMonitorSnapshot?>(null) }

    // 进入组合即采样循环，离开组合自动取消（面板关闭即停，不驻留后台）。
    // 授权状态翻转时清基线并重启循环。
    LaunchedEffect(shizukuOn) {
        if (shizukuOn) sampler.resetCpuBaseline()
        while (true) {
            snapshot = withContext(Dispatchers.IO) {
                if (shizukuOn) sampler.sample() else sampler.sampleMemoryOnly()
            }
            delay(SysRefreshMs)
        }
    }

    val snap = snapshot
    val memText = if (snap != null) {
        val pct = if (snap.totalMemKb > 0) {
            (snap.usedMemKb.toFloat() / snap.totalMemKb * 100).roundToInt()
        } else 0
        "$pct% · ${fmtKb(snap.usedMemKb)}/${fmtKb(snap.totalMemKb)}"
    } else {
        "—"
    }
    val cpuText = if (snap != null && snap.hasCpuSample) "${snap.cpuPct.roundToInt()}%" else "—"
    val desc = stringResource(R.string.grid_panel_sys_desc, memText, cpuText)

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(containerShape(ItemCorner))
            .padding(WidgetInnerPadding)
            .semantics { contentDescription = desc },
    ) {
        // 汇总：内存 + CPU 两条 QS 风格细条。
        SummaryBar(
            label = stringResource(R.string.grid_panel_sys_mem),
            fraction = if (snap != null && snap.totalMemKb > 0) {
                snap.usedMemKb.toFloat() / snap.totalMemKb
            } else 0f,
            text = memText,
            accent = accent,
            onCard = onCard,
        )
        Spacer(Modifier.height(5.dp))
        SummaryBar(
            label = stringResource(R.string.grid_panel_sys_cpu),
            fraction = if (snap != null && snap.hasCpuSample) snap.cpuPct / 100f else 0f,
            text = cpuText,
            accent = accent,
            onCard = onCard,
        )
        Spacer(Modifier.height(7.dp))

        val apps = snap?.apps.orEmpty()
        when {
            !shizukuOn -> {
                // 未授权：点此授权 Shizuku（跨 uid 读 /proc 的唯一途径）。
                GrantHintCell(
                    icon = Icons.Default.Speed,
                    text = stringResource(R.string.grid_panel_sys_need_shizuku),
                    vertical = false,
                    enabled = enabled,
                    onClick = { scope.launch { ShizukuManager.requestPermission() } },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            apps.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.grid_panel_sys_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = onCard.copy(alpha = 0.5f),
                    )
                }
            }
            else -> {
                // 滚动只在运行态启用：编辑态滚动让给层根拖拽（镜像 clipboard/todo）。
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (enabled) Modifier.verticalScroll(scrollState) else Modifier),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    apps.forEach { app ->
                        AppRow(app = app, onCard = onCard)
                    }
                }
            }
        }
    }
}

/** 一条 QS 风格细进度条：label + 轨道 + 数值。 */
@Composable
private fun SummaryBar(
    label: String,
    fraction: Float,
    text: String,
    accent: Color,
    onCard: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = onCard.copy(alpha = 0.6f),
        )
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .weight(1f)
                .height(6.dp)
                .clip(CircleShape)
                .background(onCard.copy(alpha = 0.12f)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .background(accent),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = onCard,
            maxLines = 1,
        )
    }
}

/** 一行应用：图标 + 名称 + CPU% + 内存(RSS)。 */
@Composable
private fun AppRow(app: AppUsage, onCard: Color) {
    val context = LocalContext.current
    // 跨进程 PM 查询：缓存 + IO 异步，组合期不再同步阻塞（见 AppIconCache）。
    val icon = rememberAppDrawable("app/${app.packageName}") {
        AppIcons.drawable(context, app.packageName)
    }
    val label = rememberAppLabel("label/${app.packageName}", app.packageName) {
        runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(app.packageName, 0),
            ).toString()
        }.getOrNull()
    }
    val cpuText = if (app.cpuPct > 0f) "${app.cpuPct.roundToInt()}%" else "0%"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SysRowH),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            AsyncImage(
                model = icon,
                contentDescription = null,
                modifier = Modifier.size(SysIconSize),
                imageLoader = context.imageLoader,
            )
        } else {
            Icon(
                imageVector = Icons.Default.Android,
                contentDescription = null,
                tint = onCard.copy(alpha = 0.6f),
                modifier = Modifier.size(SysIconSize),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = onCard,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = cpuText,
            style = MaterialTheme.typography.labelSmall,
            color = onCard.copy(alpha = 0.8f),
            maxLines = 1,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = fmtKb(app.rssKb),
            style = MaterialTheme.typography.labelSmall,
            color = onCard,
            maxLines = 1,
        )
    }
}

/** kB → 可读尺寸：<1G 用 M，否则 G（一位小数）。 */
private fun fmtKb(kb: Long): String {
    val mb = kb / 1024f
    return if (mb >= 1024f) {
        String.format(Locale.US, "%.1fG", mb / 1024f)
    } else {
        "${mb.roundToInt()}M"
    }
}
