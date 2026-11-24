package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 动作触发动效：动作执行成功后，按动作（或动作库条目）播一份预设动效。
 *
 * - 开关默认关；强度 / 速度是动作动效**独立**的一套，不复用到达提示的旋钮。
 * - [bindings] 的 key 有两种：
 *   - `type:<typeId>`：动作选择页里的动作（按类型，音量 +/− 这类变体共用一条）；
 *   - `entry:<entryId>`：动作库条目（按条目，删除条目时由 `LibraryCleaner` 清理）。
 * 两类互不回退：经动作库引用触发的动作只认条目绑定，直接触发的只认类型绑定。
 * - 值是被选中的 [AlertPreset.id]（内置或素材库）；缺失时静默不播，页面显示「预设缺失」。
 */
@Serializable
@Keep
data class ActionAlertSettings(
    val enabled: Boolean = false,
    val intensityPercent: Int = 100,
    val speedPercent: Int = 100,
    val bindings: Map<String, String> = emptyMap(),
) {
    /** 有效强度倍率（与到达提示同一套 UI 区间）。 */
    val intensityScale: Float
        get() = intensityPercent.coerceIn(
            AlertLimits.IntensityMin,
            AlertLimits.IntensityMax,
        ) / 100f

    /** 有效速度倍率。 */
    val speedScale: Float
        get() = speedPercent.coerceIn(AlertLimits.SpeedMin, AlertLimits.SpeedMax) / 100f

    /**
     * 取本次触发该用的预设。
     *
     * [entryId] 非空 = 原请求是动作库引用，只查条目绑定；否则按动作类型查。
     * 不做类型回退：库类型（应启动 / 打开链接 / Shell）本就不在选择页里，回退只会串台。
     */
    fun presetFor(typeId: String?, entryId: String?): String? =
        if (entryId != null) bindings[entryKey(entryId)] else typeId?.let { bindings[typeKey(it)] }

    /** 写入 / 清除一条绑定（null = 清除）。 */
    fun withBinding(typeId: String?, entryId: String?, presetId: String?): ActionAlertSettings {
        val key = if (entryId != null) entryKey(entryId) else typeId?.let { typeKey(it) } ?: return this
        val next = bindings.toMutableMap()
        if (presetId == null) next.remove(key) else next[key] = presetId
        return copy(bindings = next)
    }

    /** 清掉所有指向 [presetId] 的绑定（删预设时用）。 */
    fun withoutPreset(presetId: String): ActionAlertSettings =
        copy(bindings = bindings.filterValues { it != presetId })

    /** 清掉某条动作库条目的绑定（删条目时用）。 */
    fun withoutEntry(entryId: String): ActionAlertSettings =
        copy(bindings = bindings - entryKey(entryId))

    companion object {
        fun typeKey(typeId: String): String = "type:$typeId"

        fun entryKey(entryId: String): String = "entry:$entryId"
    }
}