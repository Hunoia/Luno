package hunoia.luno.ui.home

import androidx.lifecycle.viewModelScope
import com.aaron.compose.base.BaseComposeVM
import hunoia.luno.R
import hunoia.luno.core.AppContext
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.FrozenAppSettings
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.SubGestureSettings
import hunoia.luno.freeze.FreezeUseCase
import hunoia.luno.bridge.feedback.showToast
import hunoia.luno.keepalive.KeepAliveUseCase
import hunoia.luno.permission.PermissionStateUseCase
import hunoia.luno.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

abstract class HomeVMBase : BaseComposeVM<UiState, UiEvent>() {

    fun onPointerChange(value: GestureSettings.Pointer) {
        updateUiState { it.copy(pointer = value).withRuntimeStatus() }
    }

    fun savePointerSettings() {
        viewModelScope.launch {
            ConfigProvider.updateGestureSettings { it.copy(pointer = uiState.pointer) }
        }
    }

    fun onPointerContinuousModeChange(value: Boolean) {
        onPointerChange(uiState.pointer.copy(continuousMode = value))
        savePointerSettings()
    }

    fun onPointerContinuousModeTimeoutChange(value: Long) {
        onPointerChange(uiState.pointer.copy(continuousModeTimeoutMs = value))
    }

    fun oneKeyFreeze() {
        viewModelScope.launch(
            CoroutineExceptionHandler { _, _ ->
                toast(R.string.bulk_freeze_failed)
            }
        ) {
            val result = FreezeUseCase.oneKeyFreeze()
            updateUiState { it.copy(frozenAppCount = result.totalAfter).withRuntimeStatus() }
            showToast(AppContext.get().getString(R.string.bulk_frozen_count, result.changed))
        }
    }

    fun oneKeyUnfreeze() {
        viewModelScope.launch(
            CoroutineExceptionHandler { _, _ ->
                toast(R.string.bulk_unfreeze_failed)
            }
        ) {
            val result = FreezeUseCase.oneKeyUnfreeze()
            updateUiState { it.copy(frozenAppCount = result.totalAfter).withRuntimeStatus() }
            showToast(AppContext.get().getString(R.string.bulk_unfrozen_count, result.changed))
        }
    }

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

    protected fun loadFrozenCount() {
        viewModelScope.launch {
            val count = FreezeUseCase.queryFrozenCount()
            updateUiState { it.copy(frozenAppCount = count).withRuntimeStatus() }
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
                ConfigProvider.frozenAppSettings,
            ) { gestureSettings, advancedSettings, frozenAppSettings ->
                HomeRuntimeData(
                    gestureSettings = gestureSettings,
                    advancedSettings = advancedSettings,
                    frozenAppSettings = frozenAppSettings,
                )
            }
            combine(gestureData, runtimeData) { gesture, runtime ->
                uiState.copy(
                    isGestureSwitchEnabled = gesture.initialSettings.gestureEnabled,
                    gestureButtons = gesture.gestureButtons.sortedBy { it.id },
                    subGestures = gesture.subGestureSettings.subGestures,
                    pointer = runtime.gestureSettings.pointer,
                    excludedAppCount = runtime.advancedSettings.excludeApps.size,
                    selectedFrozenAppCount = runtime.frozenAppSettings.oneKeyPackageNames.size,
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
    val frozenAppSettings: FrozenAppSettings,
)
