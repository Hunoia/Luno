package hunoia.luno.ui.home

import androidx.lifecycle.viewModelScope
import com.aaron.compose.base.BaseComposeVM
import hunoia.luno.core.AppContext
import hunoia.luno.domain.backup.BackupCenter
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.SubGestureSettings
import hunoia.luno.permission.PermissionStateUseCase
import hunoia.luno.shizuku.ShizukuManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

abstract class HomeVMBase : BaseComposeVM<UiState, UiEvent>() {

    fun updatePermissionState() {
        viewModelScope.launch {
            val state = PermissionStateUseCase.loadHomePermissionState(AppContext.get())
            updateUiState {
                it.copy(
                    isGestureSwitchEnabled = state.isGestureEnabled,
                    isAccessibilityEnabled = state.isAccessibilityEnabled,
                    isKeepAliveEnabled = state.isKeepAliveEnabled,
                ).withRuntimeStatus()
            }
        }
    }

    fun refreshShizukuStatus() {
        ShizukuManager.updateStatus()
        updateUiState { it.copy(shizukuStatus = ShizukuManager.currentStatus()).withRuntimeStatus() }
    }

    fun reset() {
        viewModelScope.launch {
            BackupCenter.resetAll()
        }
    }

    protected fun observeShizukuStatus() {
        viewModelScope.launch {
            ShizukuManager.statusFlow.collectLatest { status ->
                updateUiState { it.copy(shizukuStatus = status).withRuntimeStatus() }
            }
        }
    }

    protected fun saveSettings() {
        viewModelScope.launch {
            launch {
                GestureConfig.updateButtons { uiState.gestureButtons }
            }
            launch {
                GestureConfig.updateSubGestures {
                    SubGestureSettings(subGestures = uiState.subGestures)
                }
            }
        }
    }

    protected fun saveGestureSwitchEnabled(enabled: Boolean) {
        viewModelScope.launch {
            GestureConfig.updateInitial {
                it.copy(gestureEnabled = enabled)
            }
        }
    }

    protected fun loadData() {
        viewModelScope.launch {
            val gestureData = combine(
                GestureConfig.initial,
                GestureConfig.buttons,
                GestureConfig.subGestures,
            ) { initial, buttons, subGestureSettings ->
                HomeGestureData(
                    initialSettings = initial,
                    gestureButtons = buttons,
                    subGestureSettings = subGestureSettings,
                )
            }
            val runtimeData = combine(
                TuneConfig.gesture,
                TuneConfig.advanced,
            ) { gestureSettings, advancedSettings ->
                HomeRuntimeData(
                    gestureSettings = gestureSettings,
                    advancedSettings = advancedSettings,
                )
            }
            combine(gestureData, runtimeData) { gesture, runtime ->
                uiState.copy(
                    isGestureSwitchEnabled = gesture.initialSettings.gestureEnabled,
                    gestureButtons = gesture.gestureButtons.sortedBy { it.id },
                    subGestures = gesture.subGestureSettings.subGestures,
                    isKeepAliveEnabled = runtime.advancedSettings.keepAliveEnabled,
                ).withRuntimeStatus()
            }.collectLatest { state ->
                updateUiState { state }
            }
        }
    }

    protected fun UiState.withRuntimeStatus(): UiState {
        return copy(
            runtimeStatus = HomeRuntimeStatusMapper.map(
                isAccessibilityEnabled = isAccessibilityEnabled,
                isGestureSwitchEnabled = isGestureSwitchEnabled,
                isKeepAliveEnabled = isKeepAliveEnabled,
                shizukuStatus = shizukuStatus,
            )
        )
    }
}

private data class HomeGestureData(
    val initialSettings: InitialSettings,
    val gestureButtons: List<GestureButton>,
    val subGestureSettings: SubGestureSettings,
)

private data class HomeRuntimeData(
    val gestureSettings: GestureSettings,
    val advancedSettings: AdvancedSettings,
)
