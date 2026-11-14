package hunoia.luno.ui.settings.action.gridpanel

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.config.model.PanelAppearance
import hunoia.luno.config.model.SinglePanel
import hunoia.luno.config.model.SinglePanelIds
import hunoia.luno.domain.gridpanel.GridPanelCenter
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.gridpanel.SinglePanelCatalog
import hunoia.luno.quickaccess.panel.QuickLauncherSettingsContent
import hunoia.luno.ui.component.feedback.EmptyState
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.component.screen.LunoScreenLazy
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
    var globalAppearance by remember { mutableStateOf(PanelAppearance()) }
    LaunchedEffect(widgetId) {
        TuneConfig.action.collect { settings ->
            panel = SinglePanelCatalog.resolve(settings, widgetId)
            globalAppearance = settings.panelAppearance
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
                val ownAppearance = current.appearance
                val appearanceRows = if (ownAppearance == null) 1 else 6
                PanelAppearanceControls(
                    appearance = ownAppearance,
                    global = globalAppearance,
                    landscape = landscape,
                    shapeAt = { index -> segmentedShape(index, appearanceRows) },
                    onFollowGlobalChange = { follow ->
                        update { panel ->
                            panel.copy(
                                appearance = if (follow) null else globalAppearance,
                            )
                        }
                    },
                    onCommit = { next -> update { it.copy(appearance = next) } },
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
