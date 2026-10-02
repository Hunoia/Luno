package hunoia.luno.ui.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.SubGestureCleaner
import hunoia.luno.config.backup.RestorePrecheckResult
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.SubGesture
import hunoia.luno.config.model.resolveDisplayName
import hunoia.luno.core.AppContext
import hunoia.luno.R
import hunoia.luno.keepalive.KeepAliveUseCase
import hunoia.luno.permission.PermissionStateUseCase
import hunoia.luno.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeVM : HomeVMBase() {

    override val initialState: UiState = UiState()

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
                android.util.Log.e("LunoLauncher", "backup failed", e)
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
                toast(R.string.restore_precheck_invalid_format)
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
                android.util.Log.e("LunoLauncher", "restore failed", e)
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
            ConfigProvider.updateSubGestureSettings { settings ->
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
            ConfigProvider.updateGestureButtons {
                it + GestureButton.create(name = name)
            }
        }
    }

    fun updateGestureButtonColor(button: GestureButton, color: Int) {
        viewModelScope.launch {
            ConfigProvider.updateGestureButtons { buttons ->
                buttons.map {
                    if (it.id == button.id) it.copy(color = color)
                    else it
                }
            }
        }
    }

    fun updateSubGestureColor(gesture: SubGesture, color: Int) {
        viewModelScope.launch {
            ConfigProvider.updateSubGestureSettings { settings ->
                settings.copy(
                    subGestures = settings.subGestures.map {
                        if (it.id == gesture.id) it.copy(color = color) else it
                    }
                )
            }
        }
    }

    fun onAppGestureEnabledChange(enabled: Boolean) {
        onGestureSwitchChange(enabled) {}
    }

    fun onGestureSwitchChange(enabled: Boolean, onAccessibilityNeeded: () -> Unit) {
        if (!enabled) {
            updateUiState {
                it.copy(isGestureSwitchEnabled = false).withRuntimeStatus()
            }
            saveGestureSwitchEnabled(false)
            return
        }
        viewModelScope.launch {
            val permissionState = PermissionStateUseCase.loadHomePermissionState(AppContext.get())
            if (!permissionState.isAccessibilityEnabled) {
                withContext(Dispatchers.Main) { onAccessibilityNeeded() }
                return@launch
            }
            runCatching { ShizukuManager.requestPermission() }
            runCatching { KeepAliveUseCase.setEnabled(AppContext.get(), true) { _ -> } }
            updateUiState {
                it.copy(isGestureSwitchEnabled = true).withRuntimeStatus()
            }
            saveGestureSwitchEnabled(true)
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
            ConfigProvider.updateSubGestureSettings { settings ->
                val cleanedGestureList = settings.subGestures.filter { it.id != gesture.id }
                settings.copy(subGestures = cleanedGestureList)
            }
            cleanSubGestureReferences(gesture.id)
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
                    ConfigProvider.updateGestureButtons { buttons ->
                        buttons.map {
                            if (it.id == button.id) it.copy(name = newName)
                            else it
                        }
                    }
                }
                is RenameTarget.SubGesture -> {
                    ConfigProvider.updateSubGestureSettings { settings ->
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

internal suspend fun cleanSubGestureReferences(deletedId: String) {
    SubGestureCleaner.cleanSubGestureReferences(
        deletedId = deletedId,
        shouldRemove = { SubGestureCleaner.isSubGestureAction(it) }
    )
}
