package hunoia.luno.action.model

import hunoia.luno.config.defaults.ActionSettingsDefaults.VolumeScrubStepThresholdDp
import hunoia.luno.config.model.VolumeScrubConfig
import hunoia.luno.core.domain.StoredAction
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 依赖配置默认值的 StoredAction 扩展，暂留 `:app`。
 *
 * `shortcutQualifiedName` 已迁 `:feature:search`（它需要查询侧的 `LauncherInfo`）；
 * 本文件只剩音量刮擦阈值解码，等 `:feature:actions` 允许依赖配置模型后一并迁走。
 */

/** internal.volumeScrub 的配置解码（读不到落默认值）。 */
fun StoredAction.volumeScrubConfigOrDefault(): VolumeScrubConfig {
    if (typeId != "internal.volumeScrub") return VolumeScrubConfig()
    val step = (params["stepThresholdDp"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
    return VolumeScrubConfig(
        stepThresholdDp = step ?: VolumeScrubStepThresholdDp,
    )
}
