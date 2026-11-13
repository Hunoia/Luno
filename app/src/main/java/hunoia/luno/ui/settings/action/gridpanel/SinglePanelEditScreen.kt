package hunoia.luno.ui.settings.action.gridpanel

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.config.model.GridPanelDefaults
import hunoia.luno.config.model.SinglePanel
import hunoia.luno.config.model.SinglePanelIds
import hunoia.luno.domain.gridpanel.GridPanelCenter
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.gridpanel.SinglePanelCatalog
import hunoia.luno.quickaccess.panel.QuickLauncherSettingsContent
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.SurfacePlacementSliders
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.component.screen.LunoScreenLazy
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 单功能面板设置：功能固定，不能改名 / 删除 / 切换功能。
 *
 * 只编辑该面板自己的外观（透明度 / 模糊 / 跟随触发点的边距）与该功能的设置；
 * 快速启动直接复用 [QuickLauncherSettingsContent]，不另写一套。
 */
@Composable
fun SinglePanelEditScreen(
    widgetId: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val landscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var loaded by remember { mutableStateOf(false) }
    var panel by remember(widgetId) { mutableStateOf<SinglePanel?>(null) }
    LaunchedEffect(widgetId) {
        TuneConfig.action.collect { settings ->
            panel = SinglePanelCatalog.resolve(settings, widgetId)
            loaded = true
        }
    }

    val current = panel
    val title = SinglePanelCatalog.label(context, widgetId)
    if (current == null) {
        if (loaded) {
            LunoScreen(
                title = title ?: stringResource(hunoia.luno.gridpanel.R.string.single_panel_function_missing),
                onBack = onBack,
            ) {
                EmptyState(message = stringResource(hunoia.luno.gridpanel.R.string.single_panel_function_missing))
            }
        }
        return
    }

    fun update(transform: (SinglePanel) -> SinglePanel) {
        scope.launch {
            runCatching { GridPanelCenter.updateSinglePanel(widgetId, transform) }
        }
    }

    LunoScreenLazy(
        title = title ?: stringResource(hunoia.luno.gridpanel.R.string.single_panel_function_missing),
        onBack = onBack,
        state = rememberLazyListState(),
    ) {
        item {
            LunoGroup {
                var localAlpha by remember(current.cardAlpha) { mutableFloatStateOf(current.cardAlpha) }
                LunoTextSlider(
                    value = localAlpha,
                    onValueChange = { value -> localAlpha = value.percentStep() },
                    onValueChangeFinished = { update { it.copy(cardAlpha = localAlpha) } },
                    text = stringResource(R.string.grid_panel_card_alpha),
                    valueDisplay = { "${(it * 100).roundToInt()}%" },
                    valueRange = GridPanelDefaults.MinCardAlpha..GridPanelDefaults.MaxCardAlpha,
                    steps = 0,
                    shape = segmentedShape(0, 5),
                )
                var localBlur by remember(current.blurRadiusDp) {
                    mutableFloatStateOf(current.blurRadiusDp)
                }
                val blurOffLabel = stringResource(R.string.grid_panel_blur_off)
                LunoTextSlider(
                    value = localBlur,
                    onValueChange = { value -> localBlur = value.roundToInt().toFloat() },
                    onValueChangeFinished = { update { it.copy(blurRadiusDp = localBlur) } },
                    text = stringResource(R.string.grid_panel_blur_radius),
                    valueDisplay = { v ->
                        if (v <= 0f) blurOffLabel else "${v.roundToInt()}dp"
                    },
                    valueRange = GridPanelDefaults.MinBlurRadiusDp..GridPanelDefaults.MaxBlurRadiusDp,
                    steps = 0,
                    shape = segmentedShape(1, 5),
                )
                SurfacePlacementSliders(
                    placement = current.geometry(landscape).placement,
                    shapeAt = { index -> segmentedShape(index + 2, 5) },
                    onCommit = { next ->
                        update {
                            if (landscape) {
                                it.copy(landscape = it.landscape.copy(placement = next))
                            } else {
                                it.copy(portrait = it.portrait.copy(placement = next))
                            }
                        }
                    },
                )
            }
        }
        if (widgetId == SinglePanelIds.QUICK_LAUNCH) {
            item {
                LunoGroup(title = stringResource(hunoia.luno.gridpanel.R.string.single_panel_function)) {
                    QuickLauncherSettingsContent()
                }
            }
        }
    }
}

/** 百分比滑杆：连续拖动、按 1% 量化后落盘。 */
private fun Float.percentStep(): Float = (this * 100f).roundToInt() / 100f
