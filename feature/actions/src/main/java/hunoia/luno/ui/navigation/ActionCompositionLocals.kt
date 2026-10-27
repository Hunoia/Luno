package hunoia.luno.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import hunoia.luno.config.model.ActionPanel
import hunoia.luno.config.model.GridPanel
import hunoia.luno.core.domain.ActionLibraryEntry

/**
 * 动作展示需要的配置快照（动作库条目、具名面板）。
 *
 * 归 `:feature:actions`：这些是「动作怎么显示」的知识；`:app` 在组装时用
 * `CompositionLocalProvider` 注入同一份配置快照，主进程与 `:service` 运行时都提供。
 */

val LocalActionLibraryEntries = staticCompositionLocalOf<List<ActionLibraryEntry>> { emptyList() }

/** 全局动作面板列表。用于把 `internal.actionPanel` 显示成具名面板而非类型名。 */
val LocalActionPanels = staticCompositionLocalOf<List<ActionPanel>> { emptyList() }

/** 全局网格面板列表。用于把 `internal.gridPanel` 显示成具名面板而非类型名。 */
val LocalGridPanels = staticCompositionLocalOf<List<GridPanel>> { emptyList() }
