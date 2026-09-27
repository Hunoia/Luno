package hunoia.luno.ui.home

import androidx.lifecycle.viewModelScope
import com.aaron.compose.base.BaseComposeVM
import hunoia.luno.core.AppContext
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.SubGestureSettings
import hunoia.luno.bridge.feedback.showToast
import hunoia.luno.keepalive.KeepAliveUseCase
import hunoia.luno.permission.PermissionStateUseCase
import hunoia.luno.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

abstract class HomeVMBase : BaseComposeVM<UiState, UiEvent>() {

    fun onKeepAliveChange(enabled: Boolean) {
        viewModelScope.launch {
            val changed = KeepAliveUseCase.setEnabled(
                context = AppContext.get(),
                enabled = enabled,
                onPermissionRequired = { showToast(it) },
            )
            if (changed) {
                updateUiState { it.copy(isKeepAliveEnabled = enabled).withRuntimeStatus() }
            }
        }
    }

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

    fun requestShizukuPermission() {
        viewModelScope.launch(
            CoroutineExceptionHandler { _, _ ->
                refreshShizukuStatus()
            }
        ) {
            ShizukuManager.requestPermission()
            refreshShizukuStatus()
        }
    }

    fun reset() {
        viewModelScope.launch {
            ConfigProvider.resetAll()
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
                ConfigProvider.updateGestureButtons { uiState.gestureButtons }
            }
            launch {
                ConfigProvider.updateSubGestureSettings {
                    SubGestureSettings(subGestures = uiState.subGestures)
                }
            }
        }
    }

    protected fun saveGestureSwitchEnabled(enabled: Boolean) {
        viewModelScope.launch {
            ConfigProvider.updateInitialSettings {
                it.copy(gestureEnabled = enabled)
            }
        }
    }

    protected fun loadData() {
        viewModelScope.launch {
            val gestureData = combine(
                ConfigProvider.initialSettings,
                ConfigProvider.gestureButtons,
                ConfigProvider.subGestureSettings,
            ) { initial, buttons, subGestureSettings ->
                HomeGestureData(
                    initialSettings = initial,
                    gestureButtons = buttons,
                    subGestureSettings = subGestureSettings,
                )
            }
            val runtimeData = combine(
                ConfigProvider.gestureSettings,
                ConfigProvider.advancedSettings,
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
