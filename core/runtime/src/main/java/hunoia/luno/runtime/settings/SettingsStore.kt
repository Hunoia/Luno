package hunoia.luno.runtime.settings

import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.GlobalVariables
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SettingsStore(
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private var started = false
    private var job: Job? = null

    fun start() {
        if (started) return
        started = true
        job = scope.launch(Dispatchers.Main.immediate) {
            val runtimeSettings = combine(
                ConfigProvider.gestureButtons,
                ConfigProvider.advancedSettings,
                ConfigProvider.gestureSettings,
                ConfigProvider.actionSettings,
                ConfigProvider.automationVariables,
            ) { gestureButtons, advancedSettings, gestureSettings, actionSettings, globalVariables ->
                RuntimeSettingsCore(
                    gestureButtons = gestureButtons,
                    advancedSettings = advancedSettings,
                    gestureSettings = gestureSettings,
                    actionSettings = actionSettings,
                    globalVariables = globalVariables,
                )
            }

            combine(
                runtimeSettings,
                ConfigProvider.initialSettings,
                ConfigProvider.subGestureSettings,
                ConfigProvider.actionLibrarySettings,
                ConfigProvider.automationRules,
            ) { runtime, initialSettings, subGestureSettings, actionLibrarySettings, automationRules ->
                SettingsState(
                    gestureButtons = runtime.gestureButtons,
                    advancedSettings = runtime.advancedSettings,
                    gestureSettings = runtime.gestureSettings,
                    actionSettings = runtime.actionSettings,
                    initialSettings = initialSettings,
                    subGestureSettings = subGestureSettings,
                    actionLibrarySettings = actionLibrarySettings,
                    automationRules = automationRules,
                    globalVariables = runtime.globalVariables,
                )
            }.collectLatest { _state.value = it }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        started = false
    }

    fun snapshot(): SettingsState = _state.value

    /**
     * 全局变量写入后立即反映到运行时快照：DataStore 回流有延迟，
     * 同一条规则里「SET_VAR 全局 → 立即 runRule/IF」不能读到旧值。
     * 调用方仍需负责真正落盘（ConfigProvider.setGlobalVariable）。
     */
    fun applyGlobalVariable(name: String, value: String): GlobalVariables {
        var updated = _state.value.globalVariables
        _state.update { state ->
            updated = state.globalVariables.put(name, value)
            state.copy(globalVariables = updated)
        }
        return updated
    }
}

private data class RuntimeSettingsCore(
    val gestureButtons: List<GestureButton>,
    val advancedSettings: AdvancedSettings,
    val gestureSettings: GestureSettings,
    val actionSettings: ActionSettings,
    val globalVariables: GlobalVariables = GlobalVariables(),
)
