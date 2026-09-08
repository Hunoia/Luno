package hunoia.luno.ui.home.sheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aaron.compose.component.UDFComponent
import hunoia.luno.R
import hunoia.luno.ui.component.TopBar
import hunoia.luno.ui.home.HomeVM
import hunoia.luno.ui.home.PointerSettingsContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointerSettingsScreen(onBack: () -> Unit) {
    val vm: HomeVM = viewModel()
    val scrollState = rememberScrollState()

    UDFComponent(component = vm.udfComponent, onEvent = { }) { uiState ->
        Scaffold(
            topBar = {
                TopBar(
                    onBack = onBack,
                    title = stringResource(R.string.pointer_settings)
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                PointerSettingsContent(
                    pointer = uiState.pointer,
                    vm = vm,
                    scrollState = scrollState
                )
            }
        }
    }
}
