package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * Luno 触发应用启动的来源。只用于统计诊断/后续权重，不参与启动决策。
 * 找不到明确来源的旧链路统一记 [UNKNOWN]，不要为了填 source 去改 IPC。
 */
@Serializable
@Keep
enum class AppLaunchSource {
    QUICK_LAUNCHER,
    QUICK_SEARCH,
    ACTION_DISPATCH,
    SHORTCUT,
    PREVIOUS_APP,
    UNKNOWN,
}
