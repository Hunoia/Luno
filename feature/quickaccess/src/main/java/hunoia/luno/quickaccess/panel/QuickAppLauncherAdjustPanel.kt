package hunoia.luno.quickaccess.panel
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.config.model.QuickAppLauncherSettings
import hunoia.luno.config.model.QuickLauncherGeometry
import hunoia.luno.quickaccess.R
import hunoia.luno.ui.component.input.LunoTextSlider
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 设置页使用的快速启动内容设置：与面板内调整页共用同一份控件与持久化。
 */
@Composable
fun QuickLauncherSettingsContent() {
    QuickAppLauncherAdjustPanel(onSettingsChanged = {})
}

/**
 * 快速启动内容设置：只调内容项（候选行 / 搜索列数 / 键高 / 短按小窗）。
 * 面板外框宽高与位置归承载它的 `GridPanel`，这里不再出现第二套面板几何。
 */
@Composable
internal fun QuickAppLauncherAdjustPanel(
    onSettingsChanged: (QuickAppLauncherSettings) -> Unit,
) {
    val settings by hunoia.luno.domain.search.SearchCenter.launcher
        .collectAsState(initial = QuickAppLauncherSettings())
    val coroutineScope = rememberCoroutineScope()
    // 当前朝向：横屏下调的就是横屏那份内容布局，实时跟随旋转。
    val landscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val pending = remember(settings, landscape) { mutableStateOf(settings.layout(landscape)) }
    fun preview(next: QuickLauncherGeometry) {
        pending.value = next
        onSettingsChanged(settings.withLayout(landscape) { next })
    }
    fun commit() {
        coroutineScope.launch {
            hunoia.luno.domain.search.SearchCenter.updateLauncherLayout(pending.value, landscape)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
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
                .padding(horizontal = 12.dp, vertical = 12.dp),
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
                        hunoia.luno.domain.search.SearchCenter.updateLauncher {
                            it.copy(tapOpensMiniWindow = checked)
                        }
                    }
                },
            )
        }
    }
}
