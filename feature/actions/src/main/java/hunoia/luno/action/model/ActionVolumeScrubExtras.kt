package hunoia.luno.action.model

import hunoia.luno.config.defaults.ActionSettingsDefaults.VolumeScrubStepThresholdDp
import hunoia.luno.config.model.VolumeScrubConfig

/**
 * `internal.volumeScrub` 的运行时配置：灵敏度已上提为全局设置，动作本身不再带参数。
 */
fun volumeScrubConfig(stepThresholdDp: Int = VolumeScrubStepThresholdDp): VolumeScrubConfig =
    VolumeScrubConfig(stepThresholdDp = stepThresholdDp)
