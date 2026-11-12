package hunoia.luno.action.model

import hunoia.luno.config.defaults.ActionSettingsDefaults.RecentsPickerIconSizeDp
import hunoia.luno.config.defaults.ActionSettingsDefaults.RecentsPickerSensitivityPercent
import hunoia.luno.config.model.RecentsPickerConfig
import hunoia.luno.core.domain.StoredAction
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * `internal.recentsPicker` 的参数解码（读不到落默认值）。
 *
 * 归 `:feature:actions`：参数形态与默认值都是动作侧知识；手势面板/选择页只调用它。
 */
fun StoredAction.recentsPickerConfigOrDefault(): RecentsPickerConfig {
    if (typeId != "internal.recentsPicker") return RecentsPickerConfig()
    val layout = (params["layout"] as? JsonPrimitive)?.contentOrNull
    return RecentsPickerConfig(
        layout = when (layout) {
            "HORIZONTAL" -> RecentsPickerLayout.HORIZONTAL
            else -> RecentsPickerLayout.VERTICAL
        },
        blankScrollSensitivityPercent =
            (params["sensitivityPercent"] as? JsonPrimitive)?.contentOrNull
                ?.toIntOrNull() ?: RecentsPickerSensitivityPercent,
        iconSizeDp = (params["iconSizeDp"] as? JsonPrimitive)?.contentOrNull
            ?.toIntOrNull() ?: RecentsPickerIconSizeDp,
    )
}
