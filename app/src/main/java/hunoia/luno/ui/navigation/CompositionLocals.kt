package hunoia.luno.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController
import hunoia.luno.action.model.ActionLibraryEntry
import hunoia.luno.config.model.ActionPanel
import hunoia.luno.config.model.GridPanel

val LocalNavController = staticCompositionLocalOf<NavController> { error("No NavController provided") }

val LocalActionLibraryEntries = staticCompositionLocalOf<List<ActionLibraryEntry>> { emptyList() }

/** 全局动作面板列表。用于把 `internal.actionPanel` 显示成具名面板而非类型名。 */
val LocalActionPanels = staticCompositionLocalOf<List<ActionPanel>> { emptyList() }

/** 全局网格面板列表。用于把 `internal.gridPanel` 显示成具名面板而非类型名。 */
val LocalGridPanels = staticCompositionLocalOf<List<GridPanel>> { emptyList() }