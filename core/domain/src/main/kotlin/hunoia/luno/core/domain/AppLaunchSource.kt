package hunoia.luno.core.domain

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * Luno 触发应用启动的来源。只用于统计诊断/后续权重，不参与启动决策。
 *
 * 住在契约层：启动能力库、搜索、链接分发、执行器都要给它，但都不该互相借类型。
 * 找不到明确来源的旧链路统一记 [UNKNOWN]，不要为了填 source 去改 IPC。
 */
@Serializable
@Keep
enum class AppLaunchSource {
    QUICK_LAUNCHER,
    QUICK_SEARCH,
    /** 网格面板的应用抽屉部件：与快速启动器共用列表，但统计上分开。 */
    GRID_PANEL,
    ACTION_DISPATCH,
    SHORTCUT,
    PREVIOUS_APP,
    UNKNOWN,
}
