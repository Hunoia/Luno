package hunoia.luno.gridpanel.internal

import hunoia.luno.config.model.PowerStatsWidgetParams
import hunoia.luno.core.domain.JsonSerializer
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 应用耗电统计的纯函数层：解析 `dumpsys batterystats` 的耗电段、排序、格式化。
 *
 * 口径说明（重要，UI 文案要跟着这个走）：
 *  - batterystats 的 mAh 是**框架按功耗模型估算**出来的，不是电表读数。同一份 dump 里
 *    `Computed drain` 与 `actual drain` 本来就允许有差；我们展示的是 `Computed drain`
 *    口径下各 uid 的分项，因此只能叫「估算」。
 *  - 统计窗口是 `dumpsys batterystats --charged`，即**上次充满以来**。不提供重置：
 *    `--reset` 会清掉全系统统计，属于破坏性操作，不该由面板部件触发。
 *  - 段内 `Global` 与各 `(on battery, screen on/off)` 子段是整机汇总，不是应用条目，
 *    解析时按缩进跳过：只认顶层两空格缩进的 `UID` 行。
 */

/** 解析出的单个 uid 耗电条目（mAh + 前后台拆分 + 组件分项）。 */
internal data class PowerUidEntry(
    val uid: Int,
    val powerMah: Float,
    val fgMah: Float = 0f,
    val bgMah: Float = 0f,
    val fgsMah: Float = 0f,
    val cachedMah: Float = 0f,
    val fgMs: Long = 0L,
    val fgsMs: Long = 0L,
    val cachedMs: Long = 0L,
    /** 组件名（cpu / screen / audio / wifi / wakelock …）→ mAh。只取总项，不含 `:fg` 等拆分。 */
    val componentsMah: Map<String, Float> = emptyMap(),
)

/** 整机耗电构成的一项（来自 `Global` 段）。 */
internal data class PowerComponent(
    /** 原始英文键（cpu / screen / audio …），本地化由 UI 映射。 */
    val name: String,
    val powerMah: Float,
    /** 其中应用占用的部分（`apps:`）；整机项本身不参与排行。 */
    val appsMah: Float = 0f,
    val durationMs: Long = 0L,
)

/** 一次 dump 解析出的完整快照。 */
internal data class PowerStatsSnapshot(
    /** 电池标称容量（mAh）；读不到为 0。 */
    val capacityMah: Float,
    /** 本次放电的估算总量（mAh），各 uid 分项之和应接近它。 */
    val computedDrainMah: Float,
    /** 系统给的实测放电量（mAh）；格式异常或缺失为 null。 */
    val actualDrainMah: Float?,
    /** 整机耗电构成；老 ROM 没有该段时为空。 */
    val components: List<PowerComponent> = emptyList(),
    val entries: List<PowerUidEntry>,
)

/** 段头：`  Estimated power use (mAh):`。 */
private val SectionHeader = Regex("""^\s*Estimated power use \(mAh\)""")

/** 容量行：`    Capacity: 4680, Computed drain: 433, actual drain: 433`。 */
private val CapacityLine = Regex("""Capacity:\s*([0-9.]+)""")
private val ComputedLine = Regex("""Computed drain:\s*([0-9.]+)""")
private val ActualLine = Regex("""actual drain:\s*([0-9.]+)(?:\s*-\s*([0-9.]+))?""")

/** `Global` 段的一项：`    cpu: 365 apps: 364 duration: 1h 7m 48s 220ms`。 */
private val GlobalComponentLine =
    Regex("""^ {4}([a-z_]+):\s*([0-9.]+)\s+apps:\s*([0-9.]+)(?:\s+duration:\s*(.*?))?\s*$""")

/**
 * uid 行：顶层两空格缩进 + `UID`，冒号后是总耗电，随后可带 fg/bg/fgs/cached 拆分。
 *
 * 真机（Android 14 / HyperOS）实测形如：
 * ```
 *   UID u0a496: 91.3 fg: 0.0977 (3s 119ms) bg: 6.72 fgs: 84.5 (53m 37s 260ms)
 *   UID 1000: 108 bg: 107
 * ```
 * 旧版本/部分 ROM 写作 `Uid`，因此大小写不敏感。子段行缩进 6 空格，不会命中。
 */
private val UidLine = Regex("""^ {2}[Uu][Ii][Dd]\s+(\S+):\s*([0-9.]+)(.*)$""")

/**
 * UID 明细行里的组件分项：`cpu=58.7 ... cpu:fg=0.0780 ...`。
 *
 * 负向后顾 `(?<![:\w])` 用来拒绝 `cpu:fg=…` 这类拆分项——它们的键前有冒号，
 * 只认没有前缀的总项（`cpu=` / `screen=` / `audio=` …）。
 */
private val ComponentToken = Regex("""(?<![:\w])([a-z_]+)=([0-9.]+)""")

/** 时长 token：`1h 7m 48s 220ms` / `53m 46s 464ms` / `38ms`。`ms` 必须排在 `m` 前。 */
private val DurationToken = Regex("""(\d+)\s*(ms|h|m|s)""")

/**
 * 反解 batterystats 的 uid 字符串。对应框架 `BatteryStats.formatUid` 的三类形式：
 *  - 纯数字：内核 / system uid（`1000`、`0`、`2000`）。
 *  - `u<userId>a<appId>`：普通应用，真实 uid = userId*100000 + 10000 + appId。
 *  - `u<userId>s<isolatedId>`：isolated 进程（真机 dump 里确有 `u999s1000`），
 *    真实 uid = userId*100000 + 90000 + isolatedId。
 *
 * isolated uid 查不到包名，上层会自然过滤掉；解析层不丢数据，也不替上层做判断。
 */
internal fun parseBatteryStatsUid(token: String): Int? {
    val raw = token.trim()
    if (raw.isEmpty()) return null
    raw.toIntOrNull()?.let { return it }
    val app = Regex("""^u(\d+)a(\d+)$""").matchEntire(raw)
    if (app != null) {
        val userId = app.groupValues[1].toIntOrNull() ?: return null
        val appId = app.groupValues[2].toIntOrNull() ?: return null
        return userId * 100_000 + 10_000 + appId
    }
    val isolated = Regex("""^u(\d+)s(\d+)$""").matchEntire(raw) ?: return null
    val userId = isolated.groupValues[1].toIntOrNull() ?: return null
    val isolatedId = isolated.groupValues[2].toIntOrNull() ?: return null
    return userId * 100_000 + 90_000 + isolatedId
}

/**
 * 解析耗电段。段缺失、容量行缺失、一个 uid 行都解析不出来时返回 null，
 * 由调用方走降级路径——解析失败不该抛异常，也不该显示半张空表。
 */
internal fun parseBatteryStatsPower(text: String): PowerStatsSnapshot? {
    if (text.isBlank()) return null
    val lines = text.lineSequence().toList()
    val headerIndex = lines.indexOfFirst { SectionHeader.containsMatchIn(it) }
    if (headerIndex < 0) return null

    var capacity = 0f
    var computed = 0f
    var actual: Float? = null
    val components = ArrayList<PowerComponent>()
    val entries = ArrayList<PowerUidEntry>()
    var pendingUidIndex = -1

    for (index in headerIndex until lines.size) {
        val line = lines[index]
        // 段结束：出现下一个顶层段（两空格缩进、非 UID、非 Global/子段）。
        if (index > headerIndex && isSectionBoundary(line)) break

        if (capacity == 0f) {
            CapacityLine.find(line)?.groupValues?.get(1)?.toFloatOrNull()?.let { capacity = it }
        }
        if (computed == 0f) {
            ComputedLine.find(line)?.groupValues?.get(1)?.toFloatOrNull()?.let { computed = it }
        }
        if (actual == null) {
            actual = ActualLine.find(line)?.let { match ->
                val low = match.groupValues[1].toFloatOrNull() ?: return@let null
                val high = match.groupValues[2].toFloatOrNull()
                // 区间取中点：系统给的是「实测落在 [low, high]」，取中点比取上界更中性。
                if (high != null) (low + high) / 2f else low
            }
        }

        // 整机组件汇总：只认 4 空格缩进的 `name: value apps: value` 行。
        GlobalComponentLine.find(line)?.let { match ->
            val name = match.groupValues[1]
            val powerMah = match.groupValues[2].toFloatOrNull() ?: 0f
            val appsMah = match.groupValues[3].toFloatOrNull() ?: 0f
            if (powerMah.isFinite() && powerMah >= 0f) {
                components.add(
                    PowerComponent(
                        name = name,
                        powerMah = powerMah,
                        appsMah = appsMah,
                        durationMs = parseDurationMs(match.groupValues[4]),
                    ),
                )
            }
            return@let
        }

        val uidMatch = UidLine.find(line)
        if (uidMatch != null) {
            val uid = parseBatteryStatsUid(uidMatch.groupValues[1]) ?: continue
            val power = uidMatch.groupValues[2].toFloatOrNull() ?: continue
            if (!power.isFinite() || power < 0f) continue
            val tail = uidMatch.groupValues[3]
            entries.add(
                PowerUidEntry(
                    uid = uid,
                    powerMah = power,
                    fgMah = fieldPower(tail, "fg"),
                    bgMah = fieldPower(tail, "bg"),
                    fgsMah = fieldPower(tail, "fgs"),
                    cachedMah = fieldPower(tail, "cached"),
                    fgMs = fieldDuration(tail, "fg"),
                    fgsMs = fieldDuration(tail, "fgs"),
                    cachedMs = fieldDuration(tail, "cached"),
                ),
            )
            pendingUidIndex = entries.lastIndex
            continue
        }

        // UID 子段标题（`(on battery, screen on)`）：跳过，并作废待解析的明细行，
        // 避免把「屏幕亮时」的子段组件误当成整段总项。
        if (pendingUidIndex >= 0 && line.startsWith("      ")) {
            if (line.trimStart().startsWith("(")) {
                pendingUidIndex = -1
                continue
            }
        }

        // UID 明细行：6 空格、且不是 `(on battery, …)` 子段标题。只取第一条（总项）。
        if (pendingUidIndex >= 0 && line.startsWith("      ") && !line.trimStart().startsWith("(")) {
            val parsed = LinkedHashMap<String, Float>()
            ComponentToken.findAll(line).forEach { match ->
                val name = match.groupValues[1]
                val value = match.groupValues[2].toFloatOrNull() ?: return@forEach
                if (value.isFinite() && value >= 0f) parsed[name] = value
            }
            if (parsed.isNotEmpty()) {
                entries[pendingUidIndex] = entries[pendingUidIndex].copy(componentsMah = parsed)
            }
            pendingUidIndex = -1
        }
    }

    if (entries.isEmpty()) return null
    // Computed drain 缺失时用分项之和兜底：百分比仍然能算，只是分母口径略窄。
    val total = if (computed > 0f) computed else entries.sumOf { it.powerMah.toDouble() }.toFloat()
    return PowerStatsSnapshot(
        capacityMah = capacity,
        computedDrainMah = total,
        actualDrainMah = actual,
        components = components,
        entries = entries,
    )
}

/** 取 UID 行里的某个 mAh 字段（`fg: 0.0977` / `bg: 6.72`）。 */
private fun fieldPower(text: String, key: String): Float =
    Regex("""\b${Regex.escape(key)}:\s*([0-9.]+)""")
        .find(text)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f

/** 取 UID 行里某个字段括号中的时长（`fg: 0.0977 (3s 119ms)`）。 */
private fun fieldDuration(text: String, key: String): Long =
    Regex("""\b${Regex.escape(key)}:\s*[0-9.]+\s*\(([^)]*)\)""")
        .find(text)?.groupValues?.get(1)?.let(::parseDurationMs) ?: 0L

/** 解析 batterystats 的时长串为毫秒；无法解析的 token 忽略，非负。 */
internal fun parseDurationMs(text: String?): Long {
    if (text.isNullOrBlank()) return 0L
    var total = 0L
    DurationToken.findAll(text).forEach { match ->
        val value = match.groupValues[1].toLongOrNull() ?: return@forEach
        total += when (match.groupValues[2]) {
            "h" -> value * 3_600_000L
            "m" -> value * 60_000L
            "s" -> value * 1_000L
            "ms" -> value
            else -> 0L
        }
    }
    return total.coerceAtLeast(0L)
}

/**
 * 段边界判定：两空格缩进、且不是 `UID` 行、也不是 `Global` / 括号子段 / 指标行。
 *
 * 真机段内的顶层行只有 `Global` 与 `(on battery, ...)` 两种，其余带 `:` 的两空格行
 * 都是下一个段头（如 `  Per-app mobile ms per packet:`）。
 */
private fun isSectionBoundary(line: String): Boolean {
    if (!line.startsWith("  ")) return false
    if (line.startsWith("   ")) return false
    val trimmed = line.trimStart()
    if (trimmed.startsWith("UID ") || trimmed.startsWith("Uid ")) return false
    if (trimmed == "Global") return false
    if (trimmed.startsWith("(")) return false
    // 指标行形如 `cpu: 363 apps: 362 duration: ...`，段头形如 `Per-app mobile ms per packet:`。
    // 指标行冒号后还有内容；段头冒号在行尾。
    return trimmed.endsWith(":")
}

/** 每实例参数。 */
internal fun parsePowerStatsParams(params: String?): PowerStatsWidgetParams =
    params?.takeIf { it.isNotBlank() }
        ?.let { runCatching { JsonSerializer.decodeFromString<PowerStatsWidgetParams>(it) }.getOrNull() }
        ?.normalized()
        ?: PowerStatsWidgetParams()

internal fun encodePowerStatsParams(
    sort: PowerSort,
    includeSystem: Boolean,
    range: PowerRange,
): String = JsonSerializer.encodeToString(
    PowerStatsWidgetParams(
        sort = sort.key,
        includeSystem = includeSystem,
        chartRange = range.key,
    ),
)

/** 排行排序口径。 */
internal enum class PowerSort(val key: String) {
    POWER(PowerStatsWidgetParams.SORT_POWER),
    NAME(PowerStatsWidgetParams.SORT_NAME),
    ;

    companion object {
        fun of(key: String?): PowerSort = entries.firstOrNull { it.key == key } ?: POWER
    }
}

/** 曲线时间范围。 */
internal enum class PowerRange(val key: String, val durationMs: Long) {
    H24(PowerStatsWidgetParams.RANGE_24H, 24L * 60 * 60 * 1000),
    D7(PowerStatsWidgetParams.RANGE_7D, 7L * 24 * 60 * 60 * 1000),
    ;

    companion object {
        fun of(key: String?): PowerRange = entries.firstOrNull { it.key == key } ?: H24
    }
}

/** 排序用的行视图：模型层不碰 PackageManager，标签由调用方喂进来。 */
internal data class PowerRow(
    val uid: Int,
    /** 稳定键：实测用 `uid:`、估算用 `pkg:`；排序档内稳定与图标缓存都用它。 */
    val stableKey: String = "uid:$uid",
    val userId: Int = uid / 100_000,
    val label: String,
    val packageName: String,
    val isSystem: Boolean,
    val powerMah: Float,
    /** 占本次总放电的百分比（0..100）。 */
    val sharePct: Float,
    val fgMah: Float = 0f,
    val bgMah: Float = 0f,
    val fgsMah: Float = 0f,
    val cachedMah: Float = 0f,
    val fgMs: Long = 0L,
    val fgsMs: Long = 0L,
    val cachedMs: Long = 0L,
    val componentsMah: Map<String, Float> = emptyMap(),
)

/**
 * 排序：按百分比 1% 为一档，同档内保持上一帧相对顺序。
 *
 * 不做精确值重排的理由与资源监控一致：60s 刷新一次，若每帧按精确值排序，
 * 相邻行会整体洗牌，用户读不稳也点不到。
 */
internal fun sortPowerRows(
    rows: List<PowerRow>,
    sort: PowerSort,
    previousOrder: Map<String, Int>,
): List<PowerRow> = when (sort) {
    PowerSort.NAME -> rows.sortedWith(
        compareBy<PowerRow> { it.label.lowercase(Locale.ROOT) }.thenBy { it.packageName },
    )
    PowerSort.POWER -> rows.sortedWith(
        compareByDescending<PowerRow> { powerBucket(it) }
            .thenBy { previousOrder[it.stableKey] ?: Int.MAX_VALUE }
            .thenBy { it.label.lowercase(Locale.ROOT) },
    )
}

/** 1% 为一档。 */
internal fun powerBucket(row: PowerRow): Int = row.sharePct.toInt()

/** mAh → 可读文本：≥10 取整，≥1 一位小数，否则两位小数。 */
internal fun formatMah(mah: Float): String = when {
    !mah.isFinite() || mah < 0f -> "—"
    mah >= 10f -> "${mah.roundToInt()} mAh"
    mah >= 1f -> String.format(Locale.US, "%.1f mAh", mah)
    else -> String.format(Locale.US, "%.2f mAh", mah)
}

/** 百分比 → `33%`；分母为 0 时返回 `—`。 */
internal fun formatSharePct(sharePct: Float): String =
    if (!sharePct.isFinite() || sharePct <= 0f) "—" else "${sharePct.roundToInt()}%"

/** 功率（mW）→ `2.1 W` / `850 mW`；读不到返回 `—`。 */
internal fun formatPowerMw(mw: Float?): String = when {
    mw == null || !mw.isFinite() || mw < 0f -> "—"
    mw >= 1000f -> String.format(Locale.US, "%.1f W", mw / 1000f)
    else -> String.format(Locale.US, "%.0f mW", mw)
}

/** 毫秒 → `1h 7m` / `53m` / `24s`；非正数返回 `—`。 */
internal fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "—"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}
