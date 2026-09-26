package hunoia.luno.runtime.settings

import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SettingsStore(
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch(Dispatchers.Main.immediate) {
            val runtimeSettings = combine(
                ConfigProvider.gestureButtons,
                ConfigProvider.advancedSettings,
                ConfigProvider.gestureSettings,
                ConfigProvider.actionSettings,
            ) { gestureButtons, advancedSettings, gestureSettings, actionSettings ->
                RuntimeSettingsCore(
                    gestureButtons = gestureButtons,
                    advancedSettings = advancedSettings,
                    gestureSettings = gestureSettings,
                    actionSettings = actionSettings,
                )
            }

            combine(
                runtimeSettings,
                ConfigProvider.initialSettings,
                ConfigProvider.subGestureSettings,
            ) { runtime, initialSettings, subGestureSettings ->
                SettingsState(
                    gestureButtons = runtime.gestureButtons,
                    advancedSettings = runtime.advancedSettings,
                    gestureSettings = runtime.gestureSettings,
                    actionSettings = runtime.actionSettings,
                    initialSettings = initialSettings,
                    subGestureSettings = subGestureSettings,
                )
            }.collectLatest { _state.value = it }
        }
    }

    fun snapshot(): SettingsState = _state.value
}

private data class RuntimeSettingsCore(
    val gestureButtons: List<GestureButton>,
    val advancedSettings: AdvancedSettings,
    val gestureSettings: GestureSettings,
    val actionSettings: ActionSettings,
)
