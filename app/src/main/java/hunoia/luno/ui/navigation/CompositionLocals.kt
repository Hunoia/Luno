package hunoia.luno.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController
import hunoia.luno.action.model.NewActionLibraryEntry
import hunoia.luno.config.model.ActionPanel



val LocalNavController = staticCompositionLocalOf<NavController> { error("No NavController provided") }

val LocalActionLibraryEntries = staticCompositionLocalOf<List<NewActionLibraryEntry>> { emptyList() }

/** 全局动作面板列表。用于把 `internal.actionPanel` 显示成具名面板而非类型名。 */
val LocalActionPanels = staticCompositionLocalOf<List<ActionPanel>> { emptyList() }
