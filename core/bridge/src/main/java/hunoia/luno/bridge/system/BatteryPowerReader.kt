package hunoia.luno.bridge.system

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import hunoia.luno.config.model.BatteryPowerStatus
import hunoia.luno.config.model.BatterySample
import kotlin.math.abs

/**
 * 瞬时电池功率读取（无需权限）。
 *
 * 计算方式：`BATTERY_PROPERTY_CURRENT_NOW × BATTERY_PROPERTY_VOLTAGE`。
 * 两个坑必须在这里收口：
 *  - **单位不一致**：API 规定 `CURRENT_NOW` 是 µA，但部分 ROM（含部分 HyperOS 机型）实际返回 mA。
 *    这里同时算两种解释，取落在合理功率区间的那一个，见 [normalizePowerMw]。
 *  - **不支持**：设备/内核不支持时返回 `Long.MIN_VALUE`；一律当作 null，绝不用 0 假装有值。
 *
 * 只做平台读取与单位归一，不碰历史、不碰 UI。
 */
class BatteryPowerReader(context: Context) {

    private val appContext = context.applicationContext
    private val batteryManager = appContext.getSystemService(BatteryManager::class.java)

    /** 一次瞬时读数。无电池广播时返回 null。 */
    fun read(): BatteryPower? {
        val sticky = runCatching {
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull() ?: return null

        val level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val levelPct = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else -1

        val status = statusOf(sticky)
        val voltageV = readVoltageV(sticky)
        val currentUa = readCurrentUa()
        val powerMw = normalizePowerMw(currentUa, voltageV)

        return BatteryPower(
            levelPct = levelPct,
            status = status,
            voltageV = voltageV,
            currentMa = currentUa?.let { abs(it) / 1000f },
            powerMw = powerMw,
        )
    }

    /** 历史采样用：读数存在时转成 [BatterySample]；无电池广播返回 null。 */
    fun readSample(): BatterySample? {
        val power = read() ?: return null
        if (power.levelPct < 0) return null
        return BatterySample(
            atMillis = System.currentTimeMillis(),
            levelPct = power.levelPct,
            status = power.status,
            powerMw = power.powerMw,
        )
    }

    private fun readCurrentUa(): Long? {
        val now = readLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (now != null && now != 0L) return now
        // CURRENT_NOW 为 0 在部分 ROM 上等价于不可用：退回平均值。
        return readLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
    }

    private fun readLongProperty(property: Int): Long? {
        val manager = batteryManager ?: return null
        val value = runCatching { manager.getLongProperty(property) }.getOrNull() ?: return null
        return if (value == Long.MIN_VALUE || value == Int.MIN_VALUE.toLong()) null else value
    }

    private fun readVoltageV(sticky: Intent): Float? {
        // BatteryManager 没有 VOLTAGE 属性；电压只从 sticky 广播的 EXTRA_VOLTAGE（mV）取。
        val mV = sticky.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        if (mV <= 0) return null
        val volts = mV / 1000f
        return volts.takeIf { it in 2f..6f }
    }

    private fun statusOf(sticky: Intent): BatteryPowerStatus {
        val status = sticky.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val plugged = sticky.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        return when {
            status == BatteryManager.BATTERY_STATUS_FULL -> BatteryPowerStatus.FULL
            status == BatteryManager.BATTERY_STATUS_CHARGING || plugged != 0 -> BatteryPowerStatus.CHARGING
            status == BatteryManager.BATTERY_STATUS_DISCHARGING || plugged == 0 -> BatteryPowerStatus.DISCHARGING
            else -> BatteryPowerStatus.UNKNOWN
        }
    }
}

/** 判功率是否「像真的」的下限：低于 0.05W 基本是单位解释错了。 */
private const val MinPlausibleW = 0.05f

/** 手机不可能长时间超过 300W；超过说明单位解释错了。 */
private const val MaxPlausibleW = 300f

/** 电压读不到时用于合理性判断的典型值。 */
private const val FallbackVoltageV = 4.0f

/** 一次瞬时读数（供 UI 展示「充电 X W / 功耗 X W」）。 */
data class BatteryPower(
    /** -1 表示无电量信息。 */
    val levelPct: Int,
    val status: BatteryPowerStatus,
    val voltageV: Float?,
    /** 绝对值（mA）；读不到为 null。 */
    val currentMa: Float?,
    /** 绝对值（mW）；读不到为 null。 */
    val powerMw: Float?,
)

/**
 * 原始电流 → 功率（mW），同时兼容 µA / mA 两种 ROM 口径。
 *
 * 纯函数，单测覆盖：
 *  - 正常 µA（如 500_000 → 500mA）直接换算；
 *  - 返回 mA 的 ROM（如 500 → 500mA）按 mA 换算；
 *  - 两种解释都不在 [MinPlausibleW]..[MaxPlausibleW] 时返回 null。
 */
internal fun normalizePowerMw(
    rawCurrent: Long?,
    voltageV: Float?,
    fallbackVoltageV: Float = FallbackVoltageV,
): Float? {
    if (rawCurrent == null || rawCurrent == Long.MIN_VALUE || rawCurrent == 0L) return null
    val volts = voltageV?.takeIf { it.isFinite() && it > 0f } ?: fallbackVoltageV
    val magnitude = abs(rawCurrent)
    if (magnitude == 0L) return null

    // 单位按 µA 解释（API 规定）；若不在合理功率区间，再按 mA 解释（部分 ROM 的口径）。
    val asMicroW = magnitude / 1_000_000f * volts
    val asMilliW = magnitude / 1_000f * volts
    val watts = when {
        asMicroW in MinPlausibleW..MaxPlausibleW -> asMicroW
        asMilliW in MinPlausibleW..MaxPlausibleW -> asMilliW
        else -> return null
    }
    return watts * 1000f
}
