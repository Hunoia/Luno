package hunoia.luno.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController
import hunoia.luno.action.model.NewActionLibraryEntry



val LocalNavController = staticCompositionLocalOf<NavController> { error("No NavController provided") }

val LocalActionLibraryEntries = staticCompositionLocalOf<List<NewActionLibraryEntry>> { emptyList() }
