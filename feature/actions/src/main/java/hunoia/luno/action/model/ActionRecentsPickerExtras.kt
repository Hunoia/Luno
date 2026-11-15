package hunoia.luno.action.model

import hunoia.luno.config.defaults.ActionSettingsDefaults.RecentsPickerIconSizeDp
import hunoia.luno.config.defaults.ActionSettingsDefaults.RecentsPickerSensitivityPercent
import hunoia.luno.config.model.RecentsPickerConfig
import hunoia.luno.core.domain.StoredAction
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * `internal.recentsPicker` 的运行时配置：布局来自动作预设，灵敏度 / 图标大小来自全局设置。
 *
 * 旧动作里可能残留 `sensitivityPercent` / `iconSizeDp` 参数，按「不考虑旧数据兼容」直接忽略。
 */
fun StoredAction.recentsPickerConfigOrDefault(
    sensitivityPercent: Int = RecentsPickerSensitivityPercent,
    iconSizeDp: Int = RecentsPickerIconSizeDp,
): RecentsPickerConfig {
    if (typeId != "internal.recentsPicker") {
        return RecentsPickerConfig(
            blankScrollSensitivityPercent = sensitivityPercent,
            iconSizeDp = iconSizeDp,
        )
    }
    val layout = (params["layout"] as? JsonPrimitive)?.contentOrNull
    return RecentsPickerConfig(
        layout = when (layout) {
            "HORIZONTAL" -> RecentsPickerLayout.HORIZONTAL
            else -> RecentsPickerLayout.VERTICAL
        },
        blankScrollSensitivityPercent = sensitivityPercent,
        iconSizeDp = iconSizeDp,
    )
}
