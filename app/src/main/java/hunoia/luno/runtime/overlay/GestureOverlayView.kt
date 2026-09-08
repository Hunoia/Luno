package hunoia.luno.runtime.overlay

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.bridge.WallpaperChangedEvent
import hunoia.luno.runtime.settings.SettingsState
import hunoia.luno.ui.component.container.SideGestureContainer
import hunoia.luno.core.Events
import hunoia.luno.core.Events.SubscribeEvent
import hunoia.luno.ui.theme.SideGestureTheme
import kotlinx.coroutines.flow.StateFlow

@Composable
fun GestureOverlayView(
    callbacks: GestureOverlayCallbacks,
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
        Box(modifier = Modifier.fillMaxSize()) {
            SideGestureContainer(
                modifier = Modifier.matchParentSize(),
                buttons = state.gestureButtons,
                onSubGestureModeChanged = { inSubGesture, center, radiusPx ->
                    callbacks.onSubGestureModeChanged(inSubGesture, center, radiusPx)
                },
                onActionPanelOverlayChanged = { show ->
                    callbacks.onActionPanelOverlayChanged(show)
                },
                onAction = { action, sourceButton, sourceOverride ->
                    callbacks.onAction(action, sourceButton, sourceOverride)
                },
                onPointerStart = { settings ->
                    callbacks.onPointerStart(settings)
                },
                onPointerShow = { settings ->
                    callbacks.onPointerShow(settings)
                },
                onPointerEnd = {
                    callbacks.onPointerEnd()
                },
                onPointerActionAtPosition = { x, y, keepActive ->
                    callbacks.onPointerActionAtPosition(x, y, keepActive)
                },
                actionSettings = state.actionSettings,
                advancedSettings = state.advancedSettings,
                gestureSettings = state.gestureSettings,
                subGestureSettings = state.subGestureSettings,
            )
        }
    }
}
