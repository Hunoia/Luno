package hunoia.luno.action.model

import hunoia.luno.config.defaults.ActionSettingsDefaults.VolumeScrubStepThresholdDp
import hunoia.luno.config.model.VolumeScrubConfig
import hunoia.luno.core.domain.StoredAction
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * `internal.volumeScrub` 的参数解码（读不到落默认值）。
 *
 * 归 `:feature:actions`：参数形态与默认值都是动作侧知识；手势面板/选择页只调用它。
 */
fun StoredAction.volumeScrubConfigOrDefault(): VolumeScrubConfig {
    if (typeId != "internal.volumeScrub") return VolumeScrubConfig()
    val step = (params["stepThresholdDp"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
    return VolumeScrubConfig(stepThresholdDp = step ?: VolumeScrubStepThresholdDp)
}
