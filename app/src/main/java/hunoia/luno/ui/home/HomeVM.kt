package hunoia.luno.ui.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.config.backup.RestorePrecheckResult
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.SubGesture
import hunoia.luno.config.model.resolveDisplayName
import hunoia.luno.core.AppContext
import hunoia.luno.R
import hunoia.luno.domain.system.EnableAccessibilityUseCase
import hunoia.luno.domain.system.KeepAliveUseCase
import hunoia.luno.domain.system.PermissionStateUseCase
import hunoia.luno.bridge.system.hasWriteSecureSettingsPermission
import hunoia.luno.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import hunoia.luno.core.LOG_TAG

class HomeVM : HomeVMBase() {

    override val initialState: UiState = UiState()

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

    fun backup(context: Context, saveTo: Uri) {
        viewModelScope.launchWithLoading(
            Dispatchers.IO + CoroutineExceptionHandler { _, e ->
                android.util.Log.e(LOG_TAG, "backup failed", e)
                toast(R.string.backup_failed)
            },
            cancelable = false
        ) {
            BackupService.backup(context, saveTo) { toast(it) }
        }
    }

    fun precheckRestore(context: Context, restoreFrom: Uri, onPassed: () -> Unit) {
        viewModelScope.launchWithLoading(
            Dispatchers.IO + CoroutineExceptionHandler { _, _ ->
                toast(hunoia.luno.core.data.R.string.restore_precheck_invalid_format)
            },
            cancelable = false
        ) {
            when (val result = BackupService.precheckRestore(context, restoreFrom)) {
                RestorePrecheckResult.Passed -> withContext(Dispatchers.Main) { onPassed() }
                is RestorePrecheckResult.Failed -> toast(result.reason.stringRes)
            }
        }
    }

    fun restore(context: Context, restoreFrom: Uri) {
        viewModelScope.launchWithLoading(
            Dispatchers.IO + CoroutineExceptionHandler { _, e ->
                android.util.Log.e(LOG_TAG, "restore failed", e)
                toast(R.string.restore_failed)
            },
            cancelable = false
        ) {
            BackupService.restore(context, restoreFrom) { toast(it) }
            updatePermissionState()
            refreshShizukuStatus()
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

    fun updateGestureButtonColor(button: GestureButton, color: Int) {
        viewModelScope.launch {
            GestureConfig.updateButtons { buttons ->
                buttons.map {
                    if (it.id == button.id) it.copy(color = color)
                    else it
                }
            }
        }
    }

    fun updateSubGestureColor(gesture: SubGesture, color: Int) {
        viewModelScope.launch {
            GestureConfig.updateSubGestures { settings ->
                settings.copy(
                    subGestures = settings.subGestures.map {
                        if (it.id == gesture.id) it.copy(color = color) else it
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
                runCatching { KeepAliveUseCase.setEnabled(AppContext.get(), false) { _ -> } }
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
                runCatching { KeepAliveUseCase.setEnabled(context, true) { _ -> } }
                val ok = runCatching { EnableAccessibilityUseCase.enable(context) }.getOrDefault(false)
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
            val state = PermissionStateUseCase.loadHomePermissionState(context)
            if (!state.isAccessibilityEnabled) {
                updateUiState { it.copy(isGestureSwitchEnabled = false).withRuntimeStatus() }
                withContext(Dispatchers.Main) { onRetryFailed() }
                return@launch
            }
            runCatching { KeepAliveUseCase.setEnabled(context, true) { _ -> } }
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
        saveGestureSwitchEnabled(enabled)
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
}

internal fun parseNumberSuffix(text: String): Int {
    val match = Regex("""(\d+)$""").find(text)
    return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
}
