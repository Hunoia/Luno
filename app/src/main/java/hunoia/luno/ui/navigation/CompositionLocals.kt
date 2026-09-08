package hunoia.luno.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController



val LocalNavController = staticCompositionLocalOf<NavController> { error("No NavController provided") }
