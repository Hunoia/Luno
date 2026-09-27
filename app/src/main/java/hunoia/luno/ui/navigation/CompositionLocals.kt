package hunoia.luno.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController
import hunoia.luno.config.model.ActionLibraryEntry



val LocalNavController = staticCompositionLocalOf<NavController> { error("No NavController provided") }

val LocalActionLibraryEntries = staticCompositionLocalOf<List<ActionLibraryEntry>> { emptyList() }
