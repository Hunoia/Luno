package hunoia.luno.quicklaunch.panel
import hunoia.luno.ui.theme.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.config.model.QuickAppLauncherSettings
import hunoia.luno.config.model.QuickLauncherGeometry
import hunoia.luno.domain.search.SearchCenter
import hunoia.luno.ui.component.input.LunoTextSlider
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun QuickAppLauncherAdjustPanel(
    onSettingsChanged: (QuickAppLauncherSettings) -> Unit,
) {
    val settings by SearchCenter.launcher.collectAsState(initial = QuickAppLauncherSettings())
    val coroutineScope = rememberCoroutineScope()
    // 当前朝向：横屏下调的就是横屏那份几何，实时跟随旋转
    val landscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val pending = remember(settings, landscape) { mutableStateOf(settings.layout(landscape)) }
    fun preview(next: QuickLauncherGeometry) {
        pending.value = next
        onSettingsChanged(settings.withLayout(landscape) { next })
    }
    fun commit() {
        coroutineScope.launch { SearchCenter.updateLauncherLayout(pending.value, landscape) }
    }

    val screenH = LocalConfiguration.current.screenHeightDp
    val kd = pending.value.keyHeightDp.dp
    val minPanel = requiredContentHeight(kd, pending.value.candidateRows, CandidateMinHeight)
    val maxPanel = requiredContentHeight(kd, pending.value.candidateRows, CandidateMaxHeight)
    val heightMin = (minPanel / screenH.dp).coerceIn(0.25f, 0.95f)
    val heightMax = (maxPanel / screenH.dp).coerceIn(0.25f, 0.95f)

    Column(modifier = Modifier.fillMaxWidth()) {
        LunoTextSlider(
            value = pending.value.contentHeightFraction,
            onValueChange = { preview(pending.value.copy(contentHeightFraction = it)) },
            onValueChangeFinished = { commit() },
            text = stringResource(R.string.quick_app_launcher_content_height),
            valueDisplay = { "${(it * 100).roundToInt()}%" },
            valueRange = heightMin..heightMax,
        )
        LunoTextSlider(
            value = pending.value.panelWidthFraction,
            onValueChange = { preview(pending.value.copy(panelWidthFraction = it)) },
            onValueChangeFinished = { commit() },
            text = stringResource(R.string.quick_app_launcher_panel_width),
            valueDisplay = { "${(it * 100).roundToInt()}%" },
            valueRange = 0.65f..1.0f,
        )
        LunoTextSlider(
            value = pending.value.panelHeightFraction,
            onValueChange = { preview(pending.value.copy(panelHeightFraction = it)) },
            onValueChangeFinished = { commit() },
            text = stringResource(R.string.quick_app_launcher_panel_height),
            valueDisplay = { "${(it * 100).roundToInt()}%" },
            valueRange = 0.05f..0.9f,
        )
        LunoTextSlider(
            value = pending.value.panelHorizontalBias,
            onValueChange = { preview(pending.value.copy(panelHorizontalBias = it)) },
            onValueChangeFinished = { commit() },
            text = stringResource(R.string.quick_app_launcher_horizontal_bias),
            valueDisplay = { "${(it * 100).roundToInt()}%" },
            valueRange = 0.0f..1.0f,
        )
        LunoTextSlider(
            value = pending.value.candidateRows.toFloat(),
            onValueChange = { preview(pending.value.copy(candidateRows = it.roundToInt().coerceIn(1, 3))) },
            onValueChangeFinished = { commit() },
            text = stringResource(R.string.quick_app_launcher_candidate_rows),
            valueDisplay = { it.roundToInt().toString() },
            valueRange = 1f..3f,
        )
        LunoTextSlider(
            value = pending.value.gridColumns.toFloat(),
            onValueChange = { preview(pending.value.copy(gridColumns = it.roundToInt().coerceIn(3, 5))) },
            onValueChangeFinished = { commit() },
            text = stringResource(R.string.quick_app_launcher_grid_columns),
            valueDisplay = { it.roundToInt().toString() },
            valueRange = 3f..5f,
        )
        LunoTextSlider(
            value = pending.value.keyHeightDp.toFloat(),
            onValueChange = { preview(pending.value.copy(keyHeightDp = it.roundToInt().coerceIn(36, 80))) },
            onValueChangeFinished = { commit() },
            text = stringResource(R.string.quick_app_launcher_key_height),
            valueDisplay = { "${it.roundToInt()}dp" },
            valueRange = 36f..80f,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 12.dp,
                    vertical = 12.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.tap_mini_window),
                style = MaterialTheme.typography.titleMedium,
            )
            Switch(
                checked = settings.tapOpensMiniWindow,
                onCheckedChange = { checked ->
                    coroutineScope.launch {
                        SearchCenter.updateLauncher {
                            it.copy(tapOpensMiniWindow = checked)
                        }
                    }
                },
            )
        }
    }
}
