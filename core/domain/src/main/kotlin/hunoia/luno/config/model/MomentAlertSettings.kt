package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 一次性“时刻”动效（目前是锁屏）：开关 + 单一预设 + 独立强度 / 速度。
 *
 * 与到达提示 / 动作动效共用同一个预设库；播放策略跟着预设走，不在这里另存。
 * [presetId] 为 null = 不播，设置页显示空状态。
 */
@Serializable
@Keep
data class MomentAlertSettings(
    val enabled: Boolean = false,
    val presetId: String? = null,
    /** 随机预设：每次播放从素材库里随机挑一个（忽略 [presetId]）。 */
    val randomize: Boolean = false,
    val intensityPercent: Int = 100,
    val speedPercent: Int = 100,
) {
    val intensityScale: Float
        get() = intensityPercent.coerceIn(
            AlertLimits.IntensityMin,
            AlertLimits.IntensityMax,
        ) / 100f

    val speedScale: Float
        get() = speedPercent.coerceIn(AlertLimits.SpeedMin, AlertLimits.SpeedMax) / 100f

    val playable: Boolean get() = enabled && (randomize || !presetId.isNullOrBlank())

    fun withoutPreset(presetId: String): MomentAlertSettings =
        if (this.presetId == presetId) copy(presetId = null) else this
}

/**
 * 触钮动效：按下即播；每个触钮单独绑一个预设（镜像钮共用源钮 id）。
 *
 * 强度 / 速度是触钮这一类的独立一套，与锁屏 / 到达 / 动作互不影响。
 */
@Serializable
@Keep
data class TouchButtonAlertSettings(
    val enabled: Boolean = false,
    val intensityPercent: Int = 100,
    val speedPercent: Int = 100,
    val bindings: Map<String, String> = emptyMap(),
) {
    val intensityScale: Float
        get() = intensityPercent.coerceIn(
            AlertLimits.IntensityMin,
            AlertLimits.IntensityMax,
        ) / 100f

    val speedScale: Float
        get() = speedPercent.coerceIn(AlertLimits.SpeedMin, AlertLimits.SpeedMax) / 100f

    fun presetFor(buttonId: String): String? = bindings[buttonId]

    fun withBinding(buttonId: String, presetId: String?): TouchButtonAlertSettings {
        val next = bindings.toMutableMap()
        if (presetId == null) next.remove(buttonId) else next[buttonId] = presetId
        return copy(bindings = next)
    }

    fun withoutPreset(presetId: String): TouchButtonAlertSettings =
        copy(bindings = bindings.filterValues { it != presetId })

    /** 删触钮 / 清理孤儿时摘掉不再存在的按钮绑定。 */
    fun retainButtons(buttonIds: Set<String>): TouchButtonAlertSettings =
        copy(bindings = bindings.filterKeys { it in buttonIds })
}
