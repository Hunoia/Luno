package hunoia.luno.runtime.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.core.Events.SubscribeEvent
import hunoia.luno.core.WallpaperChangedEvent
import hunoia.luno.runtime.gesturepanel.GestureSessionPipeline
import hunoia.luno.runtime.gesturepanel.SideGestureContainer
import hunoia.luno.runtime.settings.SettingsState
import hunoia.luno.ui.navigation.LocalActionLibraryEntries
import hunoia.luno.ui.navigation.LocalActionPanels
import hunoia.luno.ui.theme.SideGestureTheme
import kotlinx.coroutines.flow.StateFlow

/**
 * 主 overlay 的内容：只负责画。
 *
 * 手势会话状态机在 [GestureSessionPipeline]，输入源在 `touchsource` 包——两者都不由
 * 这个组合决定，所以输入源可以随时切换（无障碍窗口 / 内核触摸流）而窗口不必重建。
 */
@Composable
fun GestureOverlayView(
    pipeline: GestureSessionPipeline,
    settingsState: StateFlow<SettingsState>,
) {
    var lastWallpaperChangeMs by remember { mutableStateOf(0L) }
    SubscribeEvent(eventClass = WallpaperChangedEvent::class) {
        val now = System.currentTimeMillis()
        if (now - lastWallpaperChangeMs < 500L) return@SubscribeEvent
        lastWallpaperChangeMs = now
    }
    val themeKey = lastWallpaperChangeMs.toString()
    val state by settingsState.collectAsStateWithLifecycle()

    SideGestureTheme(wallpaperChangeTrigger = themeKey) {
        CompositionLocalProvider(
            LocalActionLibraryEntries provides state.actionLibrarySettings.entries,
            LocalActionPanels provides state.actionSettings.actionPanels.panels,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                SideGestureContainer(
                    pipeline = pipeline,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }
}
