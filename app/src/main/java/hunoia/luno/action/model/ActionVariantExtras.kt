package hunoia.luno.action.model

import hunoia.luno.core.domain.JsonSerializer
import hunoia.luno.quicklaunch.model.qualifiedNameWithIntents
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 依赖数据层/搜索模型的 StoredAction 扩展，暂留动作层。
 *
 * 纯 StoredAction 扩展已下沉 `hunoia.luno.core.domain.ActionVariants.kt`；
 * 这两个分别依赖 `config.model.VolumeScrubConfig` 与 `LauncherInfo.ShortcutInfo`，
 * 等 Phase 2/3 把配置与快捷方式模型归位后再迁。
 */

/** internal.volumeScrub 的配置解码（读不到落默认值）。 */
fun StoredAction.volumeScrubConfigOrDefault(): hunoia.luno.config.model.VolumeScrubConfig {
    if (typeId != "internal.volumeScrub") return hunoia.luno.config.model.VolumeScrubConfig()
    val step = (params["stepThresholdDp"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
    return hunoia.luno.config.model.VolumeScrubConfig(
        stepThresholdDp = step
            ?: hunoia.luno.config.defaults.ActionSettingsDefaults.VolumeScrubStepThresholdDp,
    )
}

/** app.launchShortcut 内嵌的快捷方式标识（解 data JSON，用于去重与比对）。 */
fun StoredAction.shortcutQualifiedName(): String? {
    if (typeId != "app.launchShortcut") return null
    val data = (params["data"] as? JsonPrimitive)?.contentOrNull ?: return null
    return runCatching {
        JsonSerializer.decodeFromString<hunoia.luno.quicklaunch.model.LauncherInfo.ShortcutInfo>(data)
    }.getOrNull()?.qualifiedNameWithIntents
}
