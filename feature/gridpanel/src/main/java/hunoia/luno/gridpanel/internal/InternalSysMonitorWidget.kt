package hunoia.luno.gridpanel.internal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.SortByAlpha
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.imageLoader
import hunoia.luno.bridge.system.AppProcessUsage
import hunoia.luno.bridge.system.DeviceStats
import hunoia.luno.bridge.system.MemoryStats
import hunoia.luno.bridge.system.ProcessStats
import hunoia.luno.bridge.system.ProcessStatsSampler
import hunoia.luno.bridge.system.SystemStatsReader
import hunoia.luno.bridge.system.AppIcons
import hunoia.luno.core.domain.DispatchOrigin
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.gridpanel.R
import hunoia.luno.gridpanel.geometry.ItemCorner
import hunoia.luno.gridpanel.ui.gridPanelOnCardColor
import hunoia.luno.runtime.surface.SurfaceDispatch
import hunoia.luno.shizuku.ShizukuManager
import hunoia.luno.ui.component.pressClickable
import hunoia.luno.ui.icon.rememberAppDrawable
import hunoia.luno.ui.theme.containerShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 资源监控部件：内存 / CPU 汇总（含曲线与每核占用）+ 各应用进程级占用列表。
 *
 * 数据来源与刷新节奏：
 *  - 内存 / GPU / 频率 / 温度 / 电池 / 网速 / 自身占用：1s。全部是廉价读取，不需要权限
 *    （见 `:core:bridge` 的 `SystemStatsReader`）。整机「已用」= `MemTotal − MemAvailable`，
 *    与 `free` / 系统一致；不能用 `availMem`（只是 `MemFree + Cached`，会把已用算大）。
 *  - CPU + 各应用：2s，需要 Shizuku（应用读不了 `/proc/stat` 与其它 uid 的 `/proc/<pid>`）。
 *    行内内存优先用 AMS 的 PSS（与系统任务管理器同口径），拿不到才退回 VmRSS。
 *    进入组合后先补一帧 0.4s 的快速采样，让 CPU 立刻有差值；未授权时只显示自身占用。
 *  - 两条循环都绑在组合生命周期上：面板关闭 / 翻到别的页就停，不驻留后台。
 *
 * 交互：
 *  - 点「内存 / CPU」标题 = 按该口径排序（单功能面板写回 params，网格项只在本地生效）。
 *  - 列表头 A→Z 切换名称排序，Android 图标切换是否含系统应用。
 *  - 点应用行 = 走 `app.details` 动作（SurfaceDispatch，不在部件里直接起 Activity）。
 */

/** 内存 / 设备指标刷新间隔。 */
private const val MemoryRefreshMs = 1000L

/** 进程级采样间隔；shell 一次读全量，太密没有收益。 */
private const val ProcRefreshMs = 2000L

/** 首帧补采样延迟：CPU 差值需要一个 tick 窗口，0.4s 够用又不至于等两秒。 */
private const val FirstSampleDelayMs = 400L

/** 低于该宽 / 高切紧凑排版：去掉曲线与列表头按钮，只留两条细条 + 列表。 */
private val CompactMaxWidth = 210.dp
private val CompactMaxHeight = 190.dp

/** 可点行 / 控件的最小高：密排自绘命中区，取 36dp 档（ui-guidelines §1.6 例外②）。 */
private val SysRowH = 36.dp
private val SysIconSize = 18.dp

/** 应用行右侧两个数值列的固定宽：等宽右对齐，数值变化时不左右跳。 */
private val SysCpuColW = 30.dp
private val SysMemColW = 46.dp

@Composable
internal fun SysMonitorCell(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    params: String? = null,
    onUpdateParams: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val onCard = gridPanelOnCardColor
    val accentMem = MaterialTheme.colorScheme.primary
    val accentCpu = MaterialTheme.colorScheme.tertiary
    val scope = rememberCoroutineScope()

    val status by ShizukuManager.statusFlow.collectAsState()
    val privileged = status.isReady
    val reader = remember { SystemStatsReader(context.applicationContext) }
    val sampler = remember { ProcessStatsSampler(context.applicationContext) }

    val stored = remember(params) { parseSysMonitorParams(params) }
    var sort by remember(stored) { mutableStateOf(SysSort.of(stored.sort)) }
    var includeSystem by remember(stored) { mutableStateOf(stored.includeSystem) }

    var memory by remember { mutableStateOf<MemoryStats?>(null) }
    var device by remember { mutableStateOf(DeviceStats()) }
    var frame by remember { mutableStateOf<ProcessStats?>(null) }
    var selfCpu by remember { mutableStateOf(-1f) }
    var selfPss by remember { mutableStateOf(0L) }
    var memHistory by remember { mutableStateOf(emptyList<Float>()) }
    var cpuHistory by remember { mutableStateOf(emptyList<Float>()) }
    val orderState = remember { SysOrderState() }

    fun persist(nextSort: SysSort, nextIncludeSystem: Boolean) {
        onUpdateParams?.invoke(encodeSysMonitorParams(nextSort, nextIncludeSystem))
    }

    // 1s：内存 + 设备指标 + 自身占用（全部无需权限）。
    LaunchedEffect(Unit) {
        while (true) {
            val loaded = withContext(Dispatchers.IO) {
                Quad(reader.readMemory(), reader.readDevice(), reader.selfCpuPct(), reader.selfPssKb())
            }
            memory = loaded.memory
            device = loaded.device
            selfCpu = loaded.selfCpu
            selfPss = loaded.selfPss
            memHistory = pushSample(memHistory, loaded.memory.usedFraction)
            delay(MemoryRefreshMs)
        }
    }

    // 2s：CPU + 各应用（需要 Shizuku）。
    LaunchedEffect(privileged) {
        sampler.resetBaseline()
        if (!privileged) {
            frame = null
            cpuHistory = emptyList()
            return@LaunchedEffect
        }
        var first = true
        while (true) {
            val sampled = withContext(Dispatchers.IO) { sampler.sample() }
            if (sampled != null) {
                frame = sampled
                if (sampled.cpu.hasSample) {
                    cpuHistory = pushSample(cpuHistory, sampled.cpu.totalPct / 100f)
                }
            }
            delay(if (first) FirstSampleDelayMs else ProcRefreshMs)
            first = false
        }
    }

    // 未授权时只有自身一行：内存口径换成 PSS（无权限拿不到全机进程 RSS）。
    val selfRow = AppProcessUsage(
        uid = context.applicationInfo.uid,
        packageName = context.packageName,
        label = stringResource(R.string.grid_panel_sys_self),
        userId = context.applicationInfo.uid / 100000,
        isSystem = false,
        cpuPct = selfCpu.coerceIn(0f, 100f),
        memKb = selfPss,
    )
    val allRows = if (privileged) frame?.apps.orEmpty() else listOf(selfRow)
    val visibleRows = remember(allRows, includeSystem) {
        if (includeSystem) allRows else allRows.filterNot { it.isSystem }
    }
    val totalMemKb = memory?.totalKb ?: 0L
    val sortedRows = remember(visibleRows, sort, totalMemKb) {
        sortAppRows(visibleRows, sort, totalMemKb, orderState.order).also { rows ->
            if (rows.isNotEmpty()) {
                orderState.order = rows.withIndex().associate { it.value.uid to it.index }
            }
        }
    }

    val mem = memory
    val memUsedText = mem?.let { formatKb(it.usedKb) } ?: "—"
    val memTotalText = mem?.let { formatKb(it.totalKb) } ?: "—"
    val memPctText = if (mem != null && mem.totalKb > 0L) {
        "${(mem.usedFraction * 100).roundToInt()}%"
    } else {
        "—"
    }
    val swapText = mem?.takeIf { it.swapUsedKb > 0L }
        ?.let { stringResource(R.string.grid_panel_sys_swap, formatKb(it.swapUsedKb)) }
    val memDetail = stringResource(R.string.grid_panel_sys_mem_detail, memUsedText, memTotalText)

    val cpu = frame?.cpu
    val cpuPctText = if (cpu != null && cpu.hasSample) "${cpu.totalPct.roundToInt()}%" else "—"
    val tempC = device.temperatureC ?: device.battery?.temperatureC
    val coresText = device.coreFreqMhz.takeIf { it.isNotEmpty() }
        ?.let { stringResource(R.string.grid_panel_sys_cores, it.size) }
    val freqText = device.coreFreqMhz.maxOrNull()?.let { formatFreqMhz(it) }
    val tempText = tempC?.let { stringResource(R.string.grid_panel_sys_temp, it.roundToInt()) }
    val cpuDetail = listOfNotNull(coresText, tempText, freqText).joinToString(" · ")

    // 缓存 / Swap 从卡内明细移到卡外，与电池同一层；都属于整机指标，
    // 放卡内会把两张卡撑成不同高，也吃紧条目的可读宽度。
    val deviceParts = listOfNotNull(
        mem?.let { stringResource(R.string.grid_panel_sys_cache, formatKb(it.cachedKb)) },
        swapText,
        device.gpuPct?.let { stringResource(R.string.grid_panel_sys_gpu, it.roundToInt()) },
        device.battery?.let { stringResource(R.string.grid_panel_sys_battery, it.levelPct) },
        device.takeIf { it.downBytesPerSec > 0L || it.upBytesPerSec > 0L }?.let {
            stringResource(
                R.string.grid_panel_sys_net,
                formatBytesPerSec(it.downBytesPerSec),
                formatBytesPerSec(it.upBytesPerSec),
            )
        },
    )

    val summaryDesc = stringResource(
        R.string.grid_panel_sys_desc,
        if (mem != null) "$memPctText $memUsedText/$memTotalText" else "—",
        cpuPctText,
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(containerShape(ItemCorner))
            .padding(WidgetInnerPadding),
    ) {
        val compact = maxWidth < CompactMaxWidth || maxHeight < CompactMaxHeight
        Column(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = summaryDesc },
        ) {
            if (compact) {
                CompactMetricRow(
                    label = stringResource(R.string.grid_panel_sys_mem),
                    fraction = mem?.usedFraction ?: 0f,
                    valueText = memPctText,
                    accent = accentMem,
                    onCard = onCard,
                    selected = sort == SysSort.MEM,
                    enabled = enabled,
                    onClick = {
                        sort = SysSort.MEM
                        persist(SysSort.MEM, includeSystem)
                    },
                )
                Spacer(Modifier.height(2.dp))
                CompactMetricRow(
                    label = stringResource(R.string.grid_panel_sys_cpu),
                    fraction = cpu?.takeIf { it.hasSample }?.let { it.totalPct / 100f } ?: 0f,
                    valueText = cpuPctText,
                    accent = accentCpu,
                    onCard = onCard,
                    selected = sort == SysSort.CPU,
                    enabled = enabled,
                    onClick = {
                        sort = SysSort.CPU
                        persist(SysSort.CPU, includeSystem)
                    },
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MetricCard(
                        label = stringResource(R.string.grid_panel_sys_mem),
                        valueText = memPctText,
                        detail = memDetail,
                        history = memHistory,
                        accent = accentMem,
                        onCard = onCard,
                        selected = sort == SysSort.MEM,
                        corePct = emptyList(),
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            sort = SysSort.MEM
                            persist(SysSort.MEM, includeSystem)
                        },
                    )
                    MetricCard(
                        label = stringResource(R.string.grid_panel_sys_cpu),
                        valueText = cpuPctText,
                        detail = cpuDetail,
                        history = cpuHistory,
                        accent = accentCpu,
                        onCard = onCard,
                        selected = sort == SysSort.CPU,
                        corePct = cpu?.perCorePct.orEmpty(),
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            sort = SysSort.CPU
                            persist(SysSort.CPU, includeSystem)
                        },
                    )
                }
            }

            if (deviceParts.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                // 逐段换行：GPU / 电池 / 缓存 / 网速都是一个整体，不在段中间截断。
                // compact 也显示（不隐藏），最多占两行。
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    maxLines = 2,
                ) {
                    deviceParts.forEach { part ->
                        Text(
                            text = part,
                            style = MaterialTheme.typography.labelSmall,
                            color = onCard.copy(alpha = 0.55f),
                            maxLines = 1,
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            AppListHeader(
                count = sortedRows.size,
                sort = sort,
                includeSystem = includeSystem,
                onCard = onCard,
                accent = accentMem,
                enabled = enabled,
                onToggleNameSort = {
                    val next = if (sort == SysSort.NAME) SysSort.MEM else SysSort.NAME
                    sort = next
                    persist(next, includeSystem)
                },
                onToggleSystem = {
                    includeSystem = !includeSystem
                    persist(sort, includeSystem)
                },
            )

            if (!privileged) {
                GrantHintRow(
                    enabled = enabled,
                    accent = accentMem,
                    onCard = onCard,
                    onClick = { scope.launch { ShizukuManager.requestPermission() } },
                )
            }

            val scrollState = rememberScrollState()
            val flingGate = remember(scrollState) {
                PanelFlingGate { scrollState.maxValue > 0 }
            }
            val loading = privileged && frame == null
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // 编辑态把纵向滚动让给层根拖拽（与剪贴板 / 待办部件同口径）。
                    // 非编辑态只拦惯性：拖到列表头后继续拖仍归面板，fling 不再带着面板跑。
                    .nestedScroll(flingGate)
                    .then(if (enabled) Modifier.verticalScroll(scrollState) else Modifier),
            ) {
                when {
                    // 首帧还在等第一次 shell 采样：不先闪一下「暂无应用」。
                    loading -> Unit
                    sortedRows.isEmpty() -> {
                        Box(Modifier.fillMaxWidth().heightIn(min = SysRowH), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.grid_panel_sys_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = onCard.copy(alpha = 0.5f),
                            )
                        }
                    }
                    else -> sortedRows.forEach { app ->
                        AppRow(
                            app = app,
                            accent = accentMem,
                            onCard = onCard,
                            enabled = enabled,
                            onOpenDetails = {
                                SurfaceDispatch.dispatch(
                                    action = StoredAction.of(
                                        "app.details",
                                        "packageName" to app.packageName,
                                        "userId" to app.userId.toString(),
                                    ),
                                    origin = DispatchOrigin.PANEL,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 一次 1s 采样的结果包（避免在协程里拼多个状态回写）。 */
private class Quad(
    val memory: MemoryStats,
    val device: DeviceStats,
    val selfCpu: Float,
    val selfPss: Long,
)

private class SysOrderState {
    var order: Map<Int, Int> = emptyMap()
}

/** 一张指标卡：标题（点按切换排序）+ 大数值 + 迷你曲线 + 明细。 */
@Composable
private fun MetricCard(
    label: String,
    valueText: String,
    detail: String,
    history: List<Float>,
    accent: Color,
    onCard: Color,
    selected: Boolean,
    corePct: List<Float>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = containerShape(ItemCorner - WidgetInnerPadding)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.12f) else onCard.copy(alpha = 0.05f))
            .then(if (enabled) Modifier.pressClickable(onClick = onClick) else Modifier)
            .padding(horizontal = 7.dp, vertical = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) accent else onCard.copy(alpha = 0.65f),
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleSmall,
                color = onCard,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(3.dp))
        Sparkline(
            history = history,
            color = accent,
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp),
        )
        Spacer(Modifier.height(3.dp))
        // 固定槽位：CPU 卡有每核细条、内存卡没有。以前直接 if (corePct.isNotEmpty())
        // 少画 8dp，两张卡并排时高度不等。空态也占同高。
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp),
        ) {
            if (corePct.isNotEmpty()) {
                CoreBars(
                    cores = corePct,
                    color = accent,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = detail.ifEmpty { "—" },
            style = MaterialTheme.typography.labelSmall,
            color = onCard.copy(alpha = 0.55f),
            // 明细长短不一（内存带缓存 / Swap，CPU 带核数 / 频率 / 温度）：固定两行，
            // 两张卡高度始终相等；大字体下也能换行读全，不再单行 Ellipsis。
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 紧凑排版下的一条指标：标题 + 细条 + 百分比，点按切换排序。 */
@Composable
private fun CompactMetricRow(
    label: String,
    fraction: Float,
    valueText: String,
    accent: Color,
    onCard: Color,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SysRowH)
            .clip(containerShape(ItemCorner - WidgetInnerPadding))
            .then(if (selected) Modifier.background(accent.copy(alpha = 0.10f)) else Modifier)
            .then(if (enabled) Modifier.pressClickable(onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) accent else onCard.copy(alpha = 0.7f),
            maxLines = 1,
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
            text = valueText,
            style = MaterialTheme.typography.labelSmall,
            color = onCard,
            maxLines = 1,
        )
    }
}

/** 迷你曲线：历史百分比折线 + 下方淡填充；不足两个点不画。 */
@Composable
private fun Sparkline(
    history: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        if (history.size < 2) return@Canvas
        val stepX = size.width / (SysHistorySize - 1).coerceAtLeast(1)
        val startIndex = SysHistorySize - history.size
        val points = history.mapIndexed { index, value ->
            Offset(
                x = (startIndex + index) * stepX,
                y = size.height - value.coerceIn(0f, 1f) * size.height,
            )
        }
        val area = Path().apply {
            moveTo(points.first().x, size.height)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, size.height)
            close()
        }
        drawPath(area, color.copy(alpha = 0.12f))
        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            path = line,
            color = color,
            style = Stroke(
                width = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        // 当前值圆点跟着最新一个样本（而不是按单值画，避免和曲线错位）。
        drawCircle(color = color, radius = 1.5.dp.toPx(), center = points.last())
    }
}

/** 每核占用：一组竖向细条，高度 = 该核占用。 */
@Composable
private fun CoreBars(
    cores: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        cores.forEach { pct ->
            val level = pct.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.16f)),
            ) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(level)
                        .background(color.copy(alpha = 0.85f)),
                )
            }
        }
    }
}

/** 列表头：应用数 + 名称排序 / 含系统应用 两个 36dp 图标按钮。 */
@Composable
private fun AppListHeader(
    count: Int,
    sort: SysSort,
    includeSystem: Boolean,
    onCard: Color,
    accent: Color,
    enabled: Boolean,
    onToggleNameSort: () -> Unit,
    onToggleSystem: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SysRowH),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.grid_panel_sys_apps),
            style = MaterialTheme.typography.labelSmall,
            color = onCard.copy(alpha = 0.55f),
            maxLines = 1,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = onCard.copy(alpha = 0.4f),
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        IconToggle(
            icon = Icons.Default.SortByAlpha,
            description = stringResource(R.string.grid_panel_sys_sort_name),
            active = sort == SysSort.NAME,
            onCard = onCard,
            accent = accent,
            enabled = enabled,
            onClick = onToggleNameSort,
        )
        IconToggle(
            icon = Icons.Default.Android,
            description = stringResource(R.string.grid_panel_sys_show_system),
            active = includeSystem,
            onCard = onCard,
            accent = accent,
            enabled = enabled,
            onClick = onToggleSystem,
        )
    }
}

@Composable
private fun IconToggle(
    icon: ImageVector,
    description: String,
    active: Boolean,
    onCard: Color,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(SysRowH)
            .clip(CircleShape)
            .then(if (enabled) Modifier.pressClickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) accent else onCard.copy(alpha = 0.4f),
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 未授权提示：说明「应用占用需要 Shizuku」并给出授权入口。 */
@Composable
private fun GrantHintRow(
    enabled: Boolean,
    accent: Color,
    onCard: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SysRowH)
            .then(if (enabled) Modifier.pressClickable(onClick = onClick) else Modifier)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Speed,
            contentDescription = null,
            tint = accent.copy(alpha = 0.8f),
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.grid_panel_sys_need_shizuku),
            style = MaterialTheme.typography.labelSmall,
            color = onCard.copy(alpha = 0.7f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.grid_panel_sys_grant),
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            maxLines = 1,
        )
    }
}

/** 一行应用：图标 + 名称 + CPU% + 内存；点按打开系统应用详情。 */
@Composable
private fun AppRow(
    app: AppProcessUsage,
    accent: Color,
    onCard: Color,
    enabled: Boolean,
    onOpenDetails: () -> Unit,
) {
    val context = LocalContext.current
    val iconKey = "app/${app.userId}/${app.packageName}"
    val icon = rememberAppDrawable(iconKey) {
        AppIcons.drawable(context, app.packageName, userId = app.userId)
    }
    val isSelf = app.uid == android.os.Process.myUid()
    val cpuText = if (app.cpuPct >= 0.5f) "${app.cpuPct.roundToInt()}%" else "—"
    val memText = formatKb(app.memKb)
    val rowDesc = stringResource(R.string.grid_panel_sys_row_desc, app.label, cpuText, memText)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SysRowH)
            .then(if (enabled) Modifier.pressClickable(onClick = onOpenDetails) else Modifier)
            .semantics { contentDescription = rowDesc }
            .padding(horizontal = 2.dp),
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
        Spacer(Modifier.width(6.dp))
        Text(
            text = app.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = if (isSelf) accent else onCard,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = cpuText,
            modifier = Modifier.width(SysCpuColW),
            style = MaterialTheme.typography.labelSmall,
            color = onCard.copy(alpha = if (cpuText == "—") 0.35f else 0.8f),
            textAlign = TextAlign.End,
            maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = memText,
            modifier = Modifier.width(SysMemColW),
            style = MaterialTheme.typography.labelSmall,
            color = onCard,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}
