package hunoia.luno.ui.home

import hunoia.luno.facade.Facades

import androidx.lifecycle.viewModelScope
import com.aaron.compose.base.BaseComposeVM
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.ColorSpec
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.SubGesture
import hunoia.luno.config.model.SubGestureSettings
import hunoia.luno.config.model.resolveDisplayName
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import hunoia.luno.core.AppContext
import hunoia.luno.R
import hunoia.luno.bridge.system.hasWriteSecureSettingsPermission
import hunoia.luno.shizuku.ShizukuManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeVM(private val facades: Facades) : BaseComposeVM<UiState, UiEvent>() {

    override val initialState: UiState = UiState()

    /**
     * 配置流是否已完成首次同步。首帧之后不再用持久化值覆盖内存中的手势开关，
     * 否则开启链路里 KeepAlive 等配置写入触发 combine 重发时，会把乐观置为 true
     * 的开关短暂改回旧的 false（开关闪一下）。
     */
    private var hasLoadedGestureSwitch = false

    /** 总开关开启链路中跳无障碍设置后的待重试标记。 */
    private var pendingEnableByAccessibility = false
    private var switchJob: Job? = null

    init {
        loadData()
        observeShizukuStatus()
        refreshShizukuStatus()
        viewModelScope.launch {
            ShizukuManager.autoRequestPermissionIfNeeded()
            ShizukuManager.ensureWriteSecureSettings()
        }
    }

    fun addSubGesture(id: String) {
        viewModelScope.launch {
            GestureConfig.updateSubGestures { settings ->
                val newGesture = SubGesture(
                    id = id,
                    name = AppContext.get().getString(R.string.sub_gesture_default_name, settings.subGestures.size + 1),
                    color = android.graphics.Color.argb(255, kotlin.random.Random.nextInt(256), kotlin.random.Random.nextInt(256), kotlin.random.Random.nextInt(256))
                )
                settings.copy(subGestures = settings.subGestures + newGesture)
            }
        }
    }

    fun onSubGestureEnabledChange(gesture: SubGesture, enabled: Boolean) {
        updateUiState {
            val list = it.subGestures
            val index = list.indexOf(gesture)
            if (index < 0) it else {
                it.copy(subGestures = list.mapIndexed { i, g ->
                    if (i == index) g.copy(enabled = enabled) else g
                }).withRuntimeStatus()
            }
        }
        saveSettings()
    }

    fun collapseAll() {
        updateUiState {
            it.withRuntimeStatus()
        }
    }

    fun addGestureButton() {
        if (uiState.gestureButtons.size >= 20) {
            toast(R.string.gesture_button_size_max)
            return
        }
        viewModelScope.launch {
            val maxNum = uiState.gestureButtons.maxOfOrNull { button ->
                parseNumberSuffix(button.name.ifEmpty { button.resolveDisplayName() })
            } ?: 0
            val name = AppContext.get().getString(R.string.gesture_button_name, maxNum + 1)
            GestureConfig.updateButtons {
                it + GestureButton.create(name = name)
            }
        }
    }

    fun updateGestureButtonColor(button: GestureButton, colorSpec: ColorSpec) {
        viewModelScope.launch {
            GestureConfig.updateButtons { buttons ->
                buttons.map {
                    if (it.id == button.id) it.copy(
                        color = colorSpec.customColor,
                        colorSource = colorSpec.source,
                        colorThemeKey = colorSpec.themeKey,
                    ) else it
                }
            }
        }
    }

    fun updateSubGestureColor(gesture: SubGesture, colorSpec: ColorSpec) {
        viewModelScope.launch {
            GestureConfig.updateSubGestures { settings ->
                settings.copy(
                    subGestures = settings.subGestures.map {
                        if (it.id == gesture.id) it.copy(
                            color = colorSpec.customColor,
                            colorSource = colorSpec.source,
                            colorThemeKey = colorSpec.themeKey,
                        ) else it
                    }
                )
            }
        }
    }

    fun onGestureSwitchChange(enabled: Boolean, onAccessibilityNeeded: () -> Unit) {
        if (!enabled) {
            // 手动关闭：清待重试；连带关保活（停 DaemonService），无障碍设置不动。
            pendingEnableByAccessibility = false
            updateUiState {
                it.copy(isGestureSwitchEnabled = false).withRuntimeStatus()
            }
            saveGestureSwitchEnabled(false)
            viewModelScope.launch {
                runCatching { facades.system.setKeepAliveEnabled(AppContext.get(), false) { _ -> } }
            }
            return
        }
        // 乐观弹起；全链路失败再回弹。
        updateUiState {
            it.copy(isGestureSwitchEnabled = true).withRuntimeStatus()
        }
        switchJob?.cancel()
        switchJob = viewModelScope.launch {
            val context = AppContext.get()
            var wss = context.hasWriteSecureSettingsPermission()
            if (!wss) {
                // 先拉起 Shizuku 授权，授权成功顺带 pm grant WRITE_SECURE_SETTINGS。
                val granted = runCatching { ShizukuManager.requestPermission() }.getOrDefault(false)
                if (granted) {
                    runCatching { ShizukuManager.ensureWriteSecureSettings() }
                    wss = context.hasWriteSecureSettingsPermission()
                }
            }
            if (wss) {
                runCatching { facades.system.setKeepAliveEnabled(context, true) { _ -> } }
                val ok = runCatching { facades.system.enableAccessibility(context) }.getOrDefault(false)
                if (ok) {
                    pendingEnableByAccessibility = false
                    commitGestureSwitch(true)
                    return@launch
                }
            }
            // Shizuku 不可用/授权失败/写设置失败：保持弹起，跳无障碍设置，待重试。
            pendingEnableByAccessibility = true
            withContext(Dispatchers.Main) { onAccessibilityNeeded() }
        }
    }

    /** ON_RESUME：待重试时检查无障碍，已开则补完开启；仍关则回弹并提示。 */
    fun onHomeResumed(onRetryFailed: () -> Unit) {
        if (!pendingEnableByAccessibility) return
        pendingEnableByAccessibility = false
        viewModelScope.launch {
            val context = AppContext.get()
            val state = facades.system.loadHomePermissionState(context)
            if (!state.isAccessibilityEnabled) {
                updateUiState { it.copy(isGestureSwitchEnabled = false).withRuntimeStatus() }
                withContext(Dispatchers.Main) { onRetryFailed() }
                return@launch
            }
            runCatching { facades.system.setKeepAliveEnabled(context, true) { _ -> } }
            commitGestureSwitch(true)
        }
    }

    /**
     * 提交手势开关状态。开启时同步把无障碍标记为可用：
     * 无障碍开启走 Settings.Secure 直写，没有可观察的 flow，若不在此时刷新，
     * 状态卡片会一直停留在「暂不可用」，直到下次 ON_RESUME 重新读取权限状态。
     */
    private fun commitGestureSwitch(enabled: Boolean) {
        updateUiState {
            it.copy(
                isGestureSwitchEnabled = enabled,
                isAccessibilityEnabled = if (enabled) true else it.isAccessibilityEnabled,
            ).withRuntimeStatus()
        }
        saveGestureSwitchEnabled(enabled) {
            if (enabled) {
                // 总闸重新打开：按落盘的球/收件箱配置把共同宿主拉回来。
                // 关闭方向由 FloatBallService 收到总闸后自行 stopSelf，不需要在这里抢时序。
                facades.system.restoreFloatBall(AppContext.get())
            }
        }
    }

    fun onGestureButtonEnabledChange(button: GestureButton, enabled: Boolean) {
        updateUiState {
            val buttons = it.gestureButtons
            val index = buttons.indexOf(button)
            if (index < 0) it else {
                it.copy(gestureButtons = buttons.mapIndexed { i, b ->
                    if (i == index) b.copy(enabled = enabled) else b
                }).withRuntimeStatus()
            }
        }
        saveSettings()
    }

    fun deleteSubGesture(gesture: SubGesture) {
        viewModelScope.launch {
            GestureConfig.updateSubGestures { settings ->
                val cleanedGestureList = settings.subGestures.filter { it.id != gesture.id }
                settings.copy(subGestures = cleanedGestureList)
            }
            GestureConfig.cleanSubGestureReferences(gesture.id)
            delay(50)
        }
    }

    fun showRenameDialog(target: RenameTarget) {
        updateUiState { it.copy(renameDialogTarget = target).withRuntimeStatus() }
    }

    fun hideRenameDialog() {
        updateUiState { it.copy(renameDialogTarget = null).withRuntimeStatus() }
    }

    fun doRename(target: RenameTarget, newName: String) {
        if (newName.isBlank()) {
            hideRenameDialog()
            return
        }
        viewModelScope.launch {
            when (target) {
                is RenameTarget.GestureButton -> {
                    val button = target.button
                    GestureConfig.updateButtons { buttons ->
                        buttons.map {
                            if (it.id == button.id) it.copy(name = newName)
                            else it
                        }
                    }
                }
                is RenameTarget.SubGesture -> {
                    GestureConfig.updateSubGestures { settings ->
                        settings.copy(
                            subGestures = settings.subGestures.map {
                                if (it.id == target.gesture.id) it.copy(name = newName) else it
                            }
                        )
                    }
                }
            }
            hideRenameDialog()
        }
    }
    fun updatePermissionState() {
        viewModelScope.launch {
            val state = facades.system.loadHomePermissionState(AppContext.get())
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

    private fun observeShizukuStatus() {
        viewModelScope.launch {
            ShizukuManager.statusFlow.collectLatest { status ->
                updateUiState { it.copy(shizukuStatus = status).withRuntimeStatus() }
            }
        }
    }

    private fun saveSettings() {
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

    private fun saveGestureSwitchEnabled(enabled: Boolean, onSaved: suspend () -> Unit = {}) {
        viewModelScope.launch {
            GestureConfig.updateInitial {
                it.copy(gestureEnabled = enabled)
            }
            onSaved()
        }
    }

    private fun loadData() {
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
                val switchEnabled = if (hasLoadedGestureSwitch) {
                    uiState.isGestureSwitchEnabled
                } else {
                    gesture.initialSettings.gestureEnabled
                }
                hasLoadedGestureSwitch = true
                uiState.copy(
                    isGestureSwitchEnabled = switchEnabled,
                    gestureButtons = gesture.gestureButtons.sortedBy { it.id },
                    subGestures = gesture.subGestureSettings.subGestures,
                    isKeepAliveEnabled = runtime.advancedSettings.keepAliveEnabled,
                ).withRuntimeStatus()
            }.collectLatest { state ->
                updateUiState { state }
            }
        }
    }

    private fun UiState.withRuntimeStatus(): UiState {
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

internal fun parseNumberSuffix(text: String): Int {
    val match = Regex("""(\d+)$""").find(text)
    return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
}
