package hunoia.luno.gridpanel.internal

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import coil3.imageLoader
import hunoia.luno.bridge.system.AppIcons
import hunoia.luno.bridge.system.BatteryPower
import hunoia.luno.bridge.system.BatteryPowerReader
import hunoia.luno.bridge.system.BatteryStatsReader
import hunoia.luno.bridge.system.UsageForegroundEntry
import hunoia.luno.bridge.system.UsageStatsPowerReader
import hunoia.luno.config.model.BatteryHistory
import hunoia.luno.config.model.BatteryPowerStatus
import hunoia.luno.config.model.BatterySample
import hunoia.luno.config.model.TrendEntry
import hunoia.luno.core.domain.DispatchOrigin
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.domain.gridpanel.BatteryHistoryCenter
import hunoia.luno.gridpanel.R
import hunoia.luno.gridpanel.geometry.ItemCorner
import hunoia.luno.gridpanel.trend.TrendMiniChart
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

/**
 * 应用耗电统计部件：本次充电以来各应用耗电排行 + 前后台拆分 + 整机组件构成 + 电量曲线。
 *
 * 数据来源与口径（UI 必须如实反映，不能把估算伪装成实测）：
 *  - **实测**：有 Shizuku 时读 `dumpsys batterystats --charged` 的耗电段。这是框架按功耗
 *    模型算出来的估算值（`Computed drain` 本身与 `actual drain` 允许有区间差），但它是系统
 *    自己的口径，与设置里的电池页同源，因此标「实测」，并展示 mAh。
 *  - **估算**：没有 Shizuku 但有「使用情况访问」时，用前台时长 × 经验系数换算。这条路径
 *    没有能量计量，只回答「谁在前台待得久」，因此**只展示相对占比**，不展示 mAh / 总计。
 *  - 两者都没有：显示授权提示行，不出任何数字。
 *
 * 刷新节奏：dump 60s 一次；瞬时功率 5s；电量曲线由 `:service` 的电池广播写入，部件只读。
 * **绝不秒级 dump**——一次 dump 要几百毫秒到几秒，秒级刷新会白烧电，也违背这个部件本身的意义。
 */

/** dump 刷新间隔：60s。耗电统计是累计量，不需要更密。 */
private const val RefreshIntervalMs = 60_000L

/** 瞬时功率刷新间隔：5s。 */
private const val PowerRefreshMs = 5_000L

/** 低于该宽 / 高切紧凑排版：只留前几行 + 总计。 */
private val CompactMaxWidth = 210.dp
private val CompactMaxHeight = 190.dp

/** 紧凑排版下最多显示几行。 */
private const val CompactRowCount = 3

/** 组件条形图最多显示几项。 */
private const val ComponentLimit = 6

/** 行高 / 图标尺寸与资源监控部件同口径（ui-guidelines §1.6 例外②）。 */
private val PowerRowH = 36.dp
private val PowerIconSize = 18.dp

/** 右侧数值列的固定宽：等宽右对齐，数值变化时不左右跳。 */
private val PowerMahColW = 62.dp
private val PowerPctColW = 34.dp
private val PowerTimeColW = 54.dp

/** 电量曲线高度。 */
private val PowerCurveH = 34.dp

/** 数据来源，决定表头徽标文案与数字口径。 */
private enum class PowerSource { MEASURED, ESTIMATED, NONE }

@Composable
internal fun PowerStatsCell(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    params: String? = null,
    onUpdateParams: ((String) -> Unit)? = null,
    isGridItem: Boolean = true,
) {
    val context = LocalContext.current
    val onCard = gridPanelOnCardColor
    val accent = MaterialTheme.colorScheme.primary
    val accentService = MaterialTheme.colorScheme.tertiary
    val accentBackground = MaterialTheme.colorScheme.secondary
    val scope = rememberCoroutineScope()

    val status by ShizukuManager.statusFlow.collectAsState()
    val privileged = status.isReady
    val dumpReader = remember { BatteryStatsReader(context.applicationContext) }
    val usageReader = remember { UsageStatsPowerReader(context.applicationContext) }
    val powerReader = remember { BatteryPowerReader(context.applicationContext) }
    val packageCache = remember { PowerPackageCache(context.applicationContext) }

    val history by BatteryHistoryCenter.batteryHistory.collectAsState(initial = BatteryHistory())

    val stored = remember(params) { parsePowerStatsParams(params) }
    var sort by remember(stored) { mutableStateOf(PowerSort.of(stored.sort)) }
    var includeSystem by remember(stored) { mutableStateOf(stored.includeSystem) }
    var range by remember(stored) { mutableStateOf(PowerRange.of(stored.chartRange)) }

    var source by remember { mutableStateOf(PowerSource.NONE) }
    var rows by remember { mutableStateOf(emptyList<PowerRow>()) }
    var components by remember { mutableStateOf(emptyList<PowerComponent>()) }
    var split by remember { mutableStateOf(PowerSplit()) }
    var totalMah by remember { mutableFloatStateOf(0f) }
    var capacityMah by remember { mutableFloatStateOf(0f) }
    var loaded by remember { mutableStateOf(false) }
    var usageAccess by remember { mutableStateOf(false) }
    var batteryPower by remember { mutableStateOf<BatteryPower?>(null) }
    var expandedKey by remember { mutableStateOf<String?>(null) }
    val orderState = remember { PowerOrderState() }

    fun persist(nextSort: PowerSort, nextIncludeSystem: Boolean, nextRange: PowerRange) {
        onUpdateParams?.invoke(encodePowerStatsParams(nextSort, nextIncludeSystem, nextRange))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(privileged, lifecycleOwner) {
        // 数据源变了（授权 / 撤销）：先把旧快照收起来，避免用旧口径闪一帧。
        loaded = false
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val snapshot = withContext(Dispatchers.IO) {
                    loadPowerSnapshot(dumpReader, usageReader, packageCache)
                }
                source = snapshot.source
                usageAccess = snapshot.usageAccess
                rows = snapshot.rows
                components = snapshot.components
                split = snapshot.split
                totalMah = snapshot.totalMah
                capacityMah = snapshot.capacityMah
                loaded = true
                delay(RefreshIntervalMs)
            }
        }
    }

    // 瞬时功率 + 顺手补一条历史采样（只在电量 / 状态变化时写，5s 循环本身不落盘）。
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var lastLevel = Int.MIN_VALUE
            var lastStatus: BatteryPowerStatus? = null
            while (true) {
                val reading = withContext(Dispatchers.IO) { powerReader.read() }
                batteryPower = reading
                if (reading != null && reading.levelPct >= 0 &&
                    (reading.status != lastStatus || reading.levelPct != lastLevel)
                ) {
                    lastLevel = reading.levelPct
                    lastStatus = reading.status
                    runCatching {
                        BatteryHistoryCenter.record(
                            BatterySample(
                                atMillis = System.currentTimeMillis(),
                                levelPct = reading.levelPct,
                                status = reading.status,
                                powerMw = reading.powerMw,
                            ),
                        )
                    }
                }
                delay(PowerRefreshMs)
            }
        }
    }

    val measured = source == PowerSource.MEASURED
    val visibleRows = remember(rows, includeSystem) {
        if (includeSystem) rows else rows.filterNot { it.isSystem }
    }
    val sortedRows = remember(visibleRows, sort) {
        sortPowerRows(visibleRows, sort, orderState.order)
    }
    SideEffect {
        if (sortedRows.isNotEmpty()) {
            orderState.order = sortedRows.withIndex().associate { it.value.stableKey to it.index }
        }
    }
    val shownSum = remember(visibleRows) { visibleRows.sumOf { it.powerMah.toDouble() }.toFloat() }
    val otherMah = if (measured) (totalMah - shownSum).coerceAtLeast(0f) else 0f
    val showOther = otherMah > 0.01f
    val otherLabel = stringResource(
        if (includeSystem) R.string.grid_panel_power_other_only else R.string.grid_panel_power_other,
    )

    val sourceLabel = when (source) {
        PowerSource.MEASURED -> stringResource(R.string.grid_panel_power_source_measured)
        PowerSource.ESTIMATED -> stringResource(R.string.grid_panel_power_source_estimated)
        PowerSource.NONE -> null
    }
    // 估算路径没有能量计量：不显示 mAh / 总计，只显示相对占比。
    val totalText = if (measured && totalMah > 0f) formatMah(totalMah) else null
    val summaryDesc = when {
        !loaded -> stringResource(R.string.grid_panel_power_title)
        source == PowerSource.NONE -> stringResource(R.string.grid_panel_power_need_source)
        else -> stringResource(R.string.grid_panel_power_desc, totalText ?: "—", sortedRows.size)
    }

    val powerText = powerSummaryText(batteryPower, onCard, accent)
    val capacityText = if (measured && capacityMah > 0f && totalText != null) {
        stringResource(R.string.grid_panel_power_capacity, totalText, formatMah(capacityMah))
    } else {
        null
    }

    val maxComponent = components.maxOfOrNull { it.powerMah } ?: 0f
    val componentBars = components
        .filter { it.powerMah > 0f }
        .sortedByDescending { it.powerMah }
        .take(ComponentLimit)
        .map { component ->
            PowerComponentBar(
                label = componentLabel(context, component.name),
                fraction = if (maxComponent > 0f) component.powerMah / maxComponent else 0f,
                valueText = formatMah(component.powerMah),
                color = accent,
            )
        }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(containerShape(ItemCorner))
            .padding(WidgetInnerPadding),
    ) {
        val compact = isGridItem && (maxWidth < CompactMaxWidth || maxHeight < CompactMaxHeight)
        // 窄单面板（2 列）放不下「名称 + 时长 + mAh + 占比」四列：时长列先让位。
        val showTime = !isGridItem && maxWidth >= 260.dp
        Column(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = summaryDesc },
        ) {
            PowerHeader(
                sourceLabel = sourceLabel,
                sort = sort,
                includeSystem = includeSystem,
                range = range,
                onCard = onCard,
                accent = accent,
                enabled = enabled,
                compact = compact,
                onToggleNameSort = {
                    val next = if (sort == PowerSort.NAME) PowerSort.POWER else PowerSort.NAME
                    sort = next
                    persist(next, includeSystem, range)
                },
                onToggleSystem = {
                    includeSystem = !includeSystem
                    persist(sort, includeSystem, range)
                },
                onToggleRange = {
                    val next = if (range == PowerRange.H24) PowerRange.D7 else PowerRange.H24
                    range = next
                    persist(sort, includeSystem, next)
                },
            )

            if (source != PowerSource.NONE) {
                if (!compact) {
                    PowerSummary(
                        powerText = powerText,
                        totalText = totalText,
                        capacityText = capacityText,
                        onCard = onCard,
                    )
                    PowerCurve(
                        history = history,
                        range = range,
                        color = accent,
                        onCard = onCard,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(PowerCurveH)
                            .padding(vertical = 2.dp),
                    )
                }
                if (!isGridItem && measured) {
                    if (componentBars.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.grid_panel_power_components),
                            style = MaterialTheme.typography.labelSmall,
                            color = onCard.copy(alpha = 0.55f),
                            modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
                        )
                        PowerComponentChart(
                            rows = componentBars,
                            onCard = onCard,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(5.dp))
                    }
                    if (!split.isEmpty) {
                        PowerSplitBar(
                            split = split,
                            colors = listOf(accent, accentService, accentBackground, onCard.copy(alpha = 0.45f)),
                            onCard = onCard,
                        )
                        SplitLegend(
                            split = split,
                            colors = listOf(accent, accentService, accentBackground, onCard.copy(alpha = 0.45f)),
                            onCard = onCard,
                            measured = measured,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                        Spacer(Modifier.height(5.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
            } else {
                Spacer(Modifier.height(4.dp))
            }

            if (loaded && source == PowerSource.NONE) {
                GrantHint(
                    enabled = enabled,
                    accent = accent,
                    onCard = onCard,
                    onRequestShizuku = { scope.launch { ShizukuManager.requestPermission() } },
                    onOpenUsageAccess = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                    },
                )
                return@Column
            }

            val scrollState = rememberScrollState()
            val flingGate = remember(scrollState) { PanelFlingGate { scrollState.maxValue > 0 } }
            val shown = if (compact) sortedRows.take(CompactRowCount) else sortedRows
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // 编辑态把纵向滚动让给层根拖拽；非编辑态只拦惯性。
                    .nestedScroll(flingGate)
                    .then(if (enabled) Modifier.verticalScroll(scrollState) else Modifier),
            ) {
                when {
                    // 首帧还在等第一次 dump：不先闪一下「暂无数据」。
                    !loaded -> Unit
                    shown.isEmpty() && !showOther -> {
                        Box(
                            Modifier.fillMaxWidth().heightIn(min = PowerRowH),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.grid_panel_power_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = onCard.copy(alpha = 0.5f),
                            )
                        }
                    }
                    else -> {
                        shown.forEach { row ->
                            PowerAppRow(
                                row = row,
                                measured = measured,
                                fullPage = !isGridItem,
                                showTime = showTime,
                                accent = accent,
                                onCard = onCard,
                                enabled = enabled,
                                expanded = expandedKey == row.stableKey,
                                onToggle = {
                                    expandedKey = if (expandedKey == row.stableKey) null else row.stableKey
                                },
                                onOpenDetails = {
                                    SurfaceDispatch.dispatch(
                                        action = StoredAction.of(
                                            "app.details",
                                            "packageName" to row.packageName,
                                            "userId" to row.userId.toString(),
                                        ),
                                        origin = DispatchOrigin.PANEL,
                                    )
                                },
                            )
                        }
                        if (showOther) {
                            PowerAppRow(
                                row = PowerRow(
                                    uid = -1,
                                    stableKey = "other",
                                    userId = 0,
                                    label = otherLabel,
                                    packageName = "",
                                    isSystem = true,
                                    powerMah = otherMah,
                                    sharePct = if (totalMah > 0f) otherMah / totalMah * 100f else 0f,
                                ),
                                measured = measured,
                                fullPage = !isGridItem,
                                showTime = showTime,
                                accent = accent,
                                onCard = onCard,
                                enabled = false,
                                expanded = false,
                                onToggle = {},
                                onOpenDetails = null,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 一次刷新的结果包。 */
private class PowerSnapshot(
    val source: PowerSource,
    val usageAccess: Boolean,
    val rows: List<PowerRow>,
    val totalMah: Float,
    val capacityMah: Float,
    val components: List<PowerComponent>,
    val split: PowerSplit,
)

private class PowerOrderState {
    var order: Map<String, Int> = emptyMap()
}

/**
 * 加载一次快照：Shizuku 实测优先，失败退回 usage stats 估算，都没有则 NONE。
 *
 * 必须在 IO 线程调用。
 */
private suspend fun loadPowerSnapshot(
    dumpReader: BatteryStatsReader,
    usageReader: UsageStatsPowerReader,
    cache: PowerPackageCache,
): PowerSnapshot {
    val dump = dumpReader.readPowerSection()
    if (dump != null) {
        val parsed = parseBatteryStatsPower(dump)
        if (parsed != null) {
            // uid 分项之和可能略高于 Computed drain（框架分项存在重叠）。取两者较大值做分母，
            // 保证列表 + 「系统与其它」的占比合计回到 100%，而不是 103%。
            val entriesSum = parsed.entries.sumOf { it.powerMah.toDouble() }.toFloat()
            val denominator = maxOf(parsed.computedDrainMah, entriesSum)
            val rows = resolvePowerRows(parsed.entries, denominator, cache)
            if (rows.isNotEmpty()) {
                return PowerSnapshot(
                    source = PowerSource.MEASURED,
                    // 实测路径不显示授权提示，不白查一次 appop。
                    usageAccess = false,
                    rows = rows,
                    totalMah = denominator,
                    capacityMah = parsed.capacityMah,
                    components = parsed.components,
                    split = parsed.split(),
                )
            }
        }
    }

    // 降级：usage stats 前台时长估算。只用于相对占比，不产生 mAh。
    val usageAccess = usageReader.hasUsageAccess()
    if (usageAccess) {
        val foreground = usageReader.readForegroundSinceCharge()
        if (foreground.isNotEmpty()) {
            val estimated = foreground.map { usageReader.estimateMah(it.foregroundMs) }
            val total = estimated.sum()
            val rows = resolveEstimatedRows(foreground, estimated, total, cache)
            if (rows.isNotEmpty()) {
                return PowerSnapshot(
                    source = PowerSource.ESTIMATED,
                    usageAccess = true,
                    rows = rows,
                    totalMah = total,
                    capacityMah = 0f,
                    components = emptyList(),
                    // 估算没有能量计量，四段拆分只保留「前台」一段。
                    split = PowerSplit(fgMah = total),
                )
            }
        }
    }

    return PowerSnapshot(
        source = PowerSource.NONE,
        usageAccess = usageAccess,
        rows = emptyList(),
        totalMah = 0f,
        capacityMah = 0f,
        components = emptyList(),
        split = PowerSplit(),
    )
}

/** 整机四段拆分：把每个 uid 的 fg/bg/fgs/cached 汇总。 */
private fun PowerStatsSnapshot.split(): PowerSplit = PowerSplit(
    fgMah = entries.sumOf { it.fgMah.toDouble() }.toFloat(),
    fgsMah = entries.sumOf { it.fgsMah.toDouble() }.toFloat(),
    bgMah = entries.sumOf { it.bgMah.toDouble() }.toFloat(),
    cachedMah = entries.sumOf { it.cachedMah.toDouble() }.toFloat(),
)

/** uid 条目 → 带标签的行。查不到包名的 uid（isolated / 内核）由「系统与其它」行兜底。 */
private fun resolvePowerRows(
    entries: List<PowerUidEntry>,
    totalMah: Float,
    cache: PowerPackageCache,
): List<PowerRow> {
    val denominator = if (totalMah > 0f) totalMah else entries.sumOf { it.powerMah.toDouble() }.toFloat()
    return entries.mapNotNull { entry ->
        val pkg = cache.packageForUid(entry.uid) ?: return@mapNotNull null
        val meta = cache.meta(pkg) ?: return@mapNotNull null
        val isSystem = meta.isSystem || entry.uid < Process.FIRST_APPLICATION_UID
        PowerRow(
            uid = entry.uid,
            stableKey = "uid:${entry.uid}",
            userId = entry.uid / 100_000,
            label = meta.label,
            packageName = pkg,
            isSystem = isSystem,
            powerMah = entry.powerMah,
            sharePct = if (denominator > 0f) entry.powerMah / denominator * 100f else 0f,
            fgMah = entry.fgMah,
            bgMah = entry.bgMah,
            fgsMah = entry.fgsMah,
            cachedMah = entry.cachedMah,
            fgMs = entry.fgMs,
            fgsMs = entry.fgsMs,
            cachedMs = entry.cachedMs,
            componentsMah = entry.componentsMah,
        )
    }
}

/** 估算路径：包名 → 行。userId 固定 0；图标与「应用信息」都按真实用户 0 处理。 */
private fun resolveEstimatedRows(
    foreground: List<UsageForegroundEntry>,
    estimatedMah: List<Float>,
    totalMah: Float,
    cache: PowerPackageCache,
): List<PowerRow> = foreground.mapIndexedNotNull { index, entry ->
    val meta = cache.meta(entry.packageName) ?: return@mapIndexedNotNull null
    val mah = estimatedMah.getOrElse(index) { 0f }
    PowerRow(
        uid = 0,
        stableKey = "pkg:${entry.packageName}",
        userId = 0,
        label = meta.label,
        packageName = entry.packageName,
        isSystem = meta.isSystem,
        powerMah = mah,
        sharePct = if (totalMah > 0f) mah / totalMah * 100f else 0f,
        fgMah = mah,
        fgMs = entry.foregroundMs,
    )
}

/** 包名 / 标签 / 是否系统应用的进程级缓存：60s 刷新不再重复打几百次 PackageManager。 */
private data class AppMeta(val label: String, val isSystem: Boolean)

private class PowerPackageCache(context: Context) {

    private val pm = context.packageManager
    private val uidPackages = HashMap<Int, String?>()
    private val packageMeta = HashMap<String, AppMeta?>()

    fun packageForUid(uid: Int): String? {
        if (uidPackages.containsKey(uid)) return uidPackages[uid]
        val pkg = runCatching {
            pm.getPackagesForUid(uid)?.firstOrNull { it.isNotBlank() }
        }.getOrNull()
        uidPackages[uid] = pkg
        return pkg
    }

    fun meta(packageName: String): AppMeta? {
        if (packageMeta.containsKey(packageName)) return packageMeta[packageName]
        val meta = runCatching {
            val info = pm.getApplicationInfo(packageName, 0)
            AppMeta(
                label = pm.getApplicationLabel(info).toString().takeIf { it.isNotBlank() } ?: packageName,
                isSystem = (info.flags and (
                    ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
                    )) != 0,
            )
        }.getOrNull()
        packageMeta[packageName] = meta
        return meta
    }
}

/** 表头：标题 + 口径徽标 + 时间范围 chip + 名称排序 / 含系统应用两个 36dp 图标按钮。 */
@Composable
private fun PowerHeader(
    sourceLabel: String?,
    sort: PowerSort,
    includeSystem: Boolean,
    range: PowerRange,
    onCard: Color,
    accent: Color,
    enabled: Boolean,
    compact: Boolean,
    onToggleNameSort: () -> Unit,
    onToggleSystem: () -> Unit,
    onToggleRange: () -> Unit,
) {
    val rangeDesc = stringResource(R.string.grid_panel_power_range)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PowerRowH),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.grid_panel_power_title),
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.labelSmall,
                color = onCard.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (sourceLabel != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = sourceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent.copy(alpha = 0.85f),
                    maxLines = 1,
                )
            }
        }
        if (!compact) {
            Text(
                text = stringResource(
                    if (range == PowerRange.H24) R.string.grid_panel_power_range_24h
                    else R.string.grid_panel_power_range_7d,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                maxLines = 1,
                modifier = Modifier
                    .clip(CircleShape)
                    .then(if (enabled) Modifier.pressClickable(onClick = onToggleRange) else Modifier)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .semantics { contentDescription = rangeDesc },
            )
            PowerIconToggle(
                icon = Icons.Default.SortByAlpha,
                description = stringResource(R.string.grid_panel_power_sort_name),
                active = sort == PowerSort.NAME,
                onCard = onCard,
                accent = accent,
                enabled = enabled,
                onClick = onToggleNameSort,
            )
            PowerIconToggle(
                icon = Icons.Default.Android,
                description = stringResource(R.string.grid_panel_power_show_system),
                active = includeSystem,
                onCard = onCard,
                accent = accent,
                enabled = enabled,
                onClick = onToggleSystem,
            )
        }
    }
}

@Composable
private fun PowerIconToggle(
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
            .size(PowerRowH)
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

/** 一行摘要：当前功率 + 总计；有容量时再补一条「已耗 / 容量」。 */
@Composable
private fun PowerSummary(
    powerText: String,
    totalText: String?,
    capacityText: String?,
    onCard: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = powerText,
            style = MaterialTheme.typography.labelSmall,
            color = onCard.copy(alpha = 0.75f),
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        if (totalText != null) {
            Text(
                text = stringResource(R.string.grid_panel_power_total, totalText),
                style = MaterialTheme.typography.labelSmall,
                color = onCard.copy(alpha = 0.55f),
                maxLines = 1,
            )
        }
    }
    if (capacityText != null) {
        Text(
            text = capacityText,
            style = MaterialTheme.typography.labelSmall,
            color = onCard.copy(alpha = 0.45f),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

/** 电量-时间曲线；不足两个点不画空白图，给一句提示。 */
@Composable
private fun PowerCurve(
    history: BatteryHistory,
    range: PowerRange,
    color: Color,
    onCard: Color,
    modifier: Modifier = Modifier,
) {
    val now = remember(history, range) { System.currentTimeMillis() }
    val entries = remember(history, range, now) {
        history.since(now - range.durationMs).map {
            TrendEntry(atMillis = it.atMillis, value = it.levelPct.toDouble())
        }
    }
    if (entries.size < 2) {
        Box(modifier, contentAlignment = Alignment.CenterStart) {
            Text(
                text = stringResource(R.string.grid_panel_power_no_history),
                style = MaterialTheme.typography.labelSmall,
                color = onCard.copy(alpha = 0.4f),
            )
        }
    } else {
        TrendMiniChart(entries = entries, color = color, modifier = modifier)
    }
}

/** 四段图例：前台 / 前台服务 / 后台 / 缓存。 */
@Composable
private fun SplitLegend(
    split: PowerSplit,
    colors: List<Color>,
    onCard: Color,
    measured: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!measured) return
    val labels = listOf(
        R.string.grid_panel_power_split_fg to split.fgMah,
        R.string.grid_panel_power_split_fgs to split.fgsMah,
        R.string.grid_panel_power_split_bg to split.bgMah,
        R.string.grid_panel_power_split_cached to split.cachedMah,
    )
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        labels.forEachIndexed { index, (labelRes, value) ->
            if (value > 0f) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(colors.getOrElse(index) { onCard }),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "${stringResource(labelRes)} ${formatMah(value)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = onCard.copy(alpha = 0.6f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 授权提示：两条数据源各自可点，文案与实际动作一致。 */
@Composable
private fun GrantHint(
    enabled: Boolean,
    accent: Color,
    onCard: Color,
    onRequestShizuku: () -> Unit,
    onOpenUsageAccess: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.BatteryChargingFull,
                contentDescription = null,
                tint = accent.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.grid_panel_power_need_source),
                style = MaterialTheme.typography.labelSmall,
                color = onCard.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = stringResource(R.string.grid_panel_power_grant_shizuku),
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            maxLines = 1,
            modifier = Modifier
                .padding(start = 22.dp)
                .then(if (enabled) Modifier.pressClickable(onClick = onRequestShizuku) else Modifier),
        )
        Text(
            text = stringResource(R.string.grid_panel_power_grant_usage),
            style = MaterialTheme.typography.labelSmall,
            color = accent.copy(alpha = 0.75f),
            maxLines = 1,
            modifier = Modifier
                .padding(start = 22.dp)
                .then(if (enabled) Modifier.pressClickable(onClick = onOpenUsageAccess) else Modifier),
        )
    }
}

/** 一行应用：图标 + 名称 + 时长 + mAh + 占比；点按展开分项，展开区里可进系统应用详情。 */
@Composable
private fun PowerAppRow(
    row: PowerRow,
    measured: Boolean,
    fullPage: Boolean,
    showTime: Boolean,
    accent: Color,
    onCard: Color,
    enabled: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenDetails: (() -> Unit)?,
) {
    val context = LocalContext.current
    val icon = if (row.packageName.isNotBlank()) {
        rememberAppDrawable("app/${row.userId}/${row.packageName}") {
            AppIcons.drawable(context, row.packageName, userId = row.userId)
        }
    } else {
        null
    }
    val mahText = if (measured) formatMah(row.powerMah) else null
    val pctText = formatSharePct(row.sharePct)
    val rowDesc = stringResource(
        R.string.grid_panel_power_row_desc,
        row.label,
        mahText ?: "—",
        pctText,
    )
    val expandable = row.packageName.isNotBlank()

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PowerRowH)
                .then(if (enabled && expandable) Modifier.pressClickable(onClick = onToggle) else Modifier)
                .semantics { contentDescription = rowDesc }
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                AsyncImage(
                    model = icon,
                    contentDescription = null,
                    modifier = Modifier.size(PowerIconSize),
                    imageLoader = context.imageLoader,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Android,
                    contentDescription = null,
                    tint = onCard.copy(alpha = 0.6f),
                    modifier = Modifier.size(PowerIconSize),
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = row.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = onCard,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showTime) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = formatDuration(row.fgMs),
                    modifier = Modifier.width(PowerTimeColW),
                    style = MaterialTheme.typography.labelSmall,
                    color = onCard.copy(alpha = 0.45f),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }
            if (measured && mahText != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = mahText,
                    modifier = Modifier.width(PowerMahColW),
                    style = MaterialTheme.typography.labelSmall,
                    color = onCard.copy(alpha = if (mahText == "—") 0.35f else 0.8f),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = pctText,
                modifier = Modifier.width(PowerPctColW),
                style = MaterialTheme.typography.labelSmall,
                color = onCard,
                textAlign = TextAlign.End,
                maxLines = 1,
            )
        }
        if (fullPage) {
            PowerRowBar(
                fraction = row.sharePct / 100f,
                color = accent,
                modifier = Modifier.padding(start = 26.dp, end = 2.dp),
            )
        }
        if (expanded && expandable) {
            PowerAppDetail(
                row = row,
                measured = measured,
                accent = accent,
                onCard = onCard,
                onOpenDetails = onOpenDetails,
            )
        }
    }
}

/** 展开的分项：前后台 mAh + 时长 + 组件明细 + 应用信息入口。 */
@Composable
private fun PowerAppDetail(
    row: PowerRow,
    measured: Boolean,
    accent: Color,
    onCard: Color,
    onOpenDetails: (() -> Unit)?,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 26.dp, end = 2.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (measured) {
            if (row.fgMah > 0f || row.fgMs > 0L) {
                DetailLine(
                    R.string.grid_panel_power_detail_fg,
                    formatMah(row.fgMah).takeIf { row.fgMah > 0f },
                    formatDuration(row.fgMs).takeIf { row.fgMs > 0L },
                    onCard,
                )
            }
            if (row.fgsMah > 0f || row.fgsMs > 0L) {
                DetailLine(
                    R.string.grid_panel_power_detail_fgs,
                    formatMah(row.fgsMah).takeIf { row.fgsMah > 0f },
                    formatDuration(row.fgsMs).takeIf { row.fgsMs > 0L },
                    onCard,
                )
            }
            if (row.bgMah > 0f) {
                DetailLine(
                    R.string.grid_panel_power_detail_bg,
                    formatMah(row.bgMah),
                    null,
                    onCard,
                )
            }
            if (row.cachedMah > 0f || row.cachedMs > 0L) {
                DetailLine(
                    R.string.grid_panel_power_detail_cached,
                    formatMah(row.cachedMah).takeIf { row.cachedMah > 0f },
                    formatDuration(row.cachedMs).takeIf { row.cachedMs > 0L },
                    onCard,
                )
            }
            val comps = row.componentsMah.entries
                .filter { it.value > 0f }
                .sortedByDescending { it.value }
                .take(ComponentLimit)
            if (comps.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    comps.forEach { (name, mah) ->
                        Text(
                            text = "${componentLabel(context, name)} ${formatMah(mah)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = onCard.copy(alpha = 0.6f),
                            maxLines = 1,
                        )
                    }
                }
            }
        } else {
            DetailLine(
                R.string.grid_panel_power_detail_fg,
                null,
                formatDuration(row.fgMs).takeIf { row.fgMs > 0L },
                onCard,
            )
        }
        if (onOpenDetails != null && row.packageName.isNotBlank()) {
            Text(
                text = stringResource(R.string.grid_panel_power_app_info),
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                modifier = Modifier.pressClickable(onClick = onOpenDetails),
            )
        }
    }
}

@Composable
private fun DetailLine(labelRes: Int, value: String?, time: String?, onCard: Color) {
    val parts = listOfNotNull(value, time)
    if (parts.isEmpty()) return
    Text(
        text = "${stringResource(labelRes)} ${parts.joinToString(" · ")}",
        style = MaterialTheme.typography.labelSmall,
        color = onCard.copy(alpha = 0.6f),
        maxLines = 1,
    )
}

/** 当前功率文案：充电 / 功耗 / 已充满 / —。 */
@Composable
private fun powerSummaryText(power: BatteryPower?, onCard: Color, accent: Color): String {
    val label = when (power?.status) {
        BatteryPowerStatus.CHARGING -> stringResource(R.string.grid_panel_power_charging)
        BatteryPowerStatus.FULL -> stringResource(R.string.grid_panel_power_full)
        BatteryPowerStatus.DISCHARGING -> stringResource(R.string.grid_panel_power_discharging)
        else -> stringResource(R.string.grid_panel_power_unknown)
    }
    val value = if (power?.status == BatteryPowerStatus.FULL) null else power?.powerMw
    return if (value == null) label else "$label ${formatPowerMw(value)}"
}

/** 组件名 → 本地化标签；未知键回落原始英文。 */
private fun componentLabel(context: Context, name: String): String {
    val res = when (name) {
        "screen" -> R.string.grid_panel_power_comp_screen
        "cpu" -> R.string.grid_panel_power_comp_cpu
        "audio" -> R.string.grid_panel_power_comp_audio
        "video" -> R.string.grid_panel_power_comp_video
        "wifi" -> R.string.grid_panel_power_comp_wifi
        "mobile_radio", "radio", "phone" -> R.string.grid_panel_power_comp_mobile_radio
        "sensors" -> R.string.grid_panel_power_comp_sensors
        "wakelock" -> R.string.grid_panel_power_comp_wakelock
        "camera" -> R.string.grid_panel_power_comp_camera
        "gnss" -> R.string.grid_panel_power_comp_gnss
        else -> 0
    }
    return if (res != 0) context.getString(res) else name.replace('_', ' ')
}
