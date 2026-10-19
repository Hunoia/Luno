package hunoia.luno.ui.settings.action.gridpanel

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.config.model.GridPanel
import hunoia.luno.config.model.GridPanelDefaults
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.PageGutter
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 网格面板属性编辑：改名 + 列数 + 宽高比。项的管理在面板内（长按空白进编辑态）。
 *
 * 装载沿用 ActionPanelEditScreen 的模式：显式 collect、拿到 DataStore 真实值前
 * 不做「面板不存在」判断（首帧空配置会 miss，见那边注释）。
 */
@Composable
fun GridPanelEditScreen(
    panelId: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(false) }
    var panel by remember(panelId) { mutableStateOf<GridPanel?>(null) }
    LaunchedEffect(panelId) {
        TuneConfig.action.collect { settings ->
            panel = settings.actionGridPanels.panelById(panelId)
            loaded = true
        }
    }

    val current = panel
    if (current == null) {
        if (loaded) {
            LunoScreen(title = stringResource(R.string.grid_panel_unnamed), onBack = onBack) {
                EmptyState(message = stringResource(R.string.action_panel_missing))
            }
        }
        return
    }

    fun update(transform: (GridPanel) -> GridPanel) {
        scope.launch {
            TuneConfig.updateAction { settings ->
                val latest = settings.actionGridPanels.panelById(panelId) ?: return@updateAction settings
                settings.copy(actionGridPanels = settings.actionGridPanels.withPanel(transform(latest)))
            }
        }
    }

    LunoScreenLazy(
        title = current.name.ifBlank { stringResource(R.string.grid_panel_unnamed) },
        onBack = onBack,
        state = rememberLazyListState(),
    ) {
        item {
            OutlinedTextField(
                value = current.name,
                onValueChange = { name -> update { it.copy(name = name) } },
                label = { Text(stringResource(R.string.action_panel_name_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter, vertical = CardInnerSpacing),
                shape = LargeShape,
            )
        }
        item {
            LunoGroup {
                LunoTextSlider(
                    value = current.columnCount.toFloat(),
                    onValueChange = { value -> update { it.copy(columnCount = value.toInt()) } },
                    text = stringResource(R.string.grid_panel_column_count),
                    valueDisplay = { "${it.toInt()}" },
                    valueRange = GridPanelDefaults.MinColumnCount.toFloat()..GridPanelDefaults.MaxColumnCount.toFloat(),
                    steps = GridPanelDefaults.MaxColumnCount - GridPanelDefaults.MinColumnCount - 1,
                    shape = segmentedShape(0, 2),
                )
                LunoTextSlider(
                    value = current.aspectRatio,
                    onValueChange = { value -> update { it.copy(aspectRatio = value) } },
                    text = stringResource(R.string.grid_panel_aspect_ratio),
                    valueDisplay = { String.format(Locale.ROOT, "%.2f", it) },
                    valueRange = GridPanelDefaults.MinAspectRatio..GridPanelDefaults.MaxAspectRatio,
                    shape = segmentedShape(1, 2),
                )
            }
        }
    }
}
