package hunoia.luno.ui.freeze

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.aaron.compose.base.BaseComposeVM
import hunoia.luno.R
import hunoia.luno.core.AppContext
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.freeze.FreezeFacade
import hunoia.luno.config.model.FrozenAppSettings
import hunoia.luno.config.ConfigProvider
import hunoia.luno.shizuku.ShizukuManager
import hunoia.luno.bridge.feedback.showComposeToast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "FrozenAppManageVM"

class FrozenAppManageVM : BaseComposeVM<FrozenAppManageVM.UiState, FrozenAppManageVM.UiEvent>() {

    override val initialState: UiState = UiState()

    init {
        viewModelScope.launch {
            ShizukuManager.statusFlow.collectLatest { status ->
                updateUiState { it.copy(shizukuReady = status.isReady) }
                if (status.isReady) {
                    recompute()
                }
            }
        }
        viewModelScope.launch {
            ShizukuManager.autoRequestPermissionIfNeeded()
        }
        viewModelScope.launch {
            ConfigProvider.frozenAppSettings.collectLatest { settings ->
                updateUiState {
                    it.copy(
                        oneKeyPackageNames = settings.oneKeyPackageNames,
                        pendingOneKeyPackageNames = settings.oneKeyPackageNames
                    )
                }
                recompute()
                if (uiState.apps.isEmpty()) {
                    reloadApps()
                }
            }
        }
    }

    fun onQueryChange(query: String) {
        updateUiState {
            it.copy(query = query)
        }
        recompute()
    }

    fun onOneKeyChecked(packageName: String, checked: Boolean) {
        updateUiState {
            val next = it.pendingOneKeyPackageNames.toMutableSet()
            if (checked) next.add(packageName) else next.remove(packageName)
            it.copy(pendingOneKeyPackageNames = next)
        }
    }

    fun commitSelections() {
        viewModelScope.launch {
            ConfigProvider.updateFrozenAppSettings { settings ->
                settings.copy(oneKeyPackageNames = uiState.pendingOneKeyPackageNames)
            }
        }
    }

    fun reloadApps() {
        viewModelScope.launch {
            try {
                commitSelections()
                updateUiState { it.copy(refreshing = true) }
                val apps = withContext(Dispatchers.IO) {
                    val context = AppContext.get()
                    val normal = QuickLaunchFacade.queryApps(
                        context = context,
                        allowRepeatPackage = false
                    )
                    val frozen = FreezeFacade.queryFrozenApps(context)
                    val normalPackageNames = normal.map { it.packageName }.toSet()
                    normal + frozen.filter { it.packageName !in normalPackageNames }
                }
                val frozenStateByPackage = withContext(Dispatchers.IO) {
                    val context = AppContext.get()
                    val packageNames = apps.asSequence().map { it.packageName }.distinct().toList()
                    FreezeFacade.queryFrozenStateByPackage(context, packageNames)
                }
                val shizukuReady = FreezeFacade.isShizukuReady()
                updateUiState {
                    it.copy(
                        apps = apps,
                        frozenStateByPackage = frozenStateByPackage,
                        shizukuReady = shizukuReady,
                        refreshing = false
                    )
                }
                recompute()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "reloadApps failed", e)
                updateUiState { it.copy(refreshing = false) }
            }
        }
    }

    fun onToggleFrozen(packageName: String) {
        if (!uiState.shizukuReady || packageName in uiState.runningPackageActions) return
        viewModelScope.launch {
            try {
                updateUiState { it.copy(runningPackageActions = it.runningPackageActions + packageName) }
                val wasFrozen = uiState.frozenStateByPackage[packageName] == true
                val result = if (wasFrozen) {
                    FreezeFacade.unfreeze(AppContext.get(), packageName)
                } else {
                    FreezeFacade.freeze(AppContext.get(), packageName)
                }
                val nowFrozen = result.nowFrozen
                val expectedFrozen = !wasFrozen
                if (nowFrozen == expectedFrozen) {
                    showComposeToast(if (nowFrozen) R.string.frozen_success else R.string.unfrozen_success)
                } else {
                    showComposeToast(R.string.frozen_state_unchanged)
                }
                updateUiState {
                    it.copy(
                        runningPackageActions = it.runningPackageActions - packageName,
                        frozenStateByPackage = it.frozenStateByPackage + (packageName to nowFrozen)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "onToggleFrozen failed", e)
                updateUiState {
                    it.copy(runningPackageActions = it.runningPackageActions - packageName)
                }
                showComposeToast(e.message ?: AppContext.get().getString(R.string.operation_failed))
            }
            recompute()
        }
    }

    fun onOneKeyFreezeAll() {
        if (!uiState.shizukuReady || uiState.bulkActionRunning) return
        viewModelScope.launch {
            try {
                updateUiState { it.copy(bulkActionRunning = true) }
                val result = FreezeFacade.oneKeyFreeze(AppContext.get())
                showComposeToast(AppContext.get().getString(R.string.bulk_frozen_count, result.successCount))
                val refreshedFrozenState = withContext(Dispatchers.IO) {
                    FreezeFacade.queryFrozenStateByPackage(
                        AppContext.get(),
                        uiState.apps.asSequence().map { it.packageName }.distinct().toList()
                    )
                }
                updateUiState {
                    it.copy(
                        frozenStateByPackage = refreshedFrozenState,
                        bulkActionRunning = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "onOneKeyFreezeAll failed", e)
                updateUiState { it.copy(bulkActionRunning = false) }
                showComposeToast(e.message ?: AppContext.get().getString(R.string.bulk_freeze_failed))
            }
            recompute()
        }
    }

    fun onOneKeyUnfreezeAll() {
        if (!uiState.shizukuReady || uiState.bulkActionRunning) return
        viewModelScope.launch {
            val targets = currentOneKeyTargetsInRange()
            if (targets.isEmpty()) return@launch
            try {
                updateUiState { it.copy(bulkActionRunning = true) }
                val result = FreezeFacade.oneKeyUnfreeze(AppContext.get(), targets)
                val successCount = result.successCount
                val latestState = withContext(Dispatchers.IO) {
                    FreezeFacade.queryFrozenStateByPackage(AppContext.get(), targets)
                }
                showComposeToast(AppContext.get().getString(R.string.bulk_unfrozen_count, successCount))
                updateUiState {
                    it.copy(
                        frozenStateByPackage = it.frozenStateByPackage + latestState,
                        bulkActionRunning = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "onOneKeyUnfreezeAll failed", e)
                updateUiState { it.copy(bulkActionRunning = false) }
                showComposeToast(e.message ?: AppContext.get().getString(R.string.bulk_unfreeze_failed))
            }
            recompute()
        }
    }

    fun clearSelections() {
        updateUiState { it.copy(pendingOneKeyPackageNames = emptySet()) }
        commitSelections()
    }

    fun onOneKeySelectFrozen() {
        val frozenPkgs = uiState.frozenStateByPackage.filter { it.value }.keys
        updateUiState {
            val next = it.pendingOneKeyPackageNames.toMutableSet()
            next.addAll(frozenPkgs)
            it.copy(pendingOneKeyPackageNames = next)
        }
    }

    private fun currentOneKeyTargetsInRange(): List<String> {
        return FreezeFacade.computeOneKeyTargets(
            uiState.apps, uiState.oneKeyPackageNames
        )
    }

    private fun recompute() {
        val inRange = uiState.apps
            .asSequence()
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()

        val query = uiState.query.trim()
        val filtered = if (query.isBlank()) {
            inRange
        } else {
            inRange.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }

        val oneKeySet = uiState.oneKeyPackageNames
        val oneKeyApps = filtered.filter { it.packageName in oneKeySet }
        val otherApps = filtered.filter { it.packageName !in oneKeySet }

        val selectedCount = inRange.count { it.packageName in oneKeySet }
        val frozenState = uiState.frozenStateByPackage
        val frozenCount = inRange.count { frozenState[it.packageName] == true }
        val oneKeyTargetsInRange = inRange.count { it.packageName in oneKeySet }

        updateUiState {
            it.copy(
                visibleApps = filtered,
                oneKeyApps = oneKeyApps,
                otherApps = otherApps,
                selectedCount = selectedCount,
                frozenCount = frozenCount,
                oneKeyTargetCount = oneKeyTargetsInRange,
                hasAnyAppInRange = inRange.isNotEmpty(),
                hasSearchResult = filtered.isNotEmpty()
            )
        }
    }

    @Immutable
    data class UiState(
        val apps: List<AppInfo> = emptyList(),
        val visibleApps: List<AppInfo> = emptyList(),
        val oneKeyApps: List<AppInfo> = emptyList(),
        val otherApps: List<AppInfo> = emptyList(),
        val query: String = "",
        val oneKeyPackageNames: Set<String> = FrozenAppSettings().oneKeyPackageNames,
        val pendingOneKeyPackageNames: Set<String> = oneKeyPackageNames,
        val selectedCount: Int = 0,
        val frozenCount: Int = 0,
        val frozenStateByPackage: Map<String, Boolean> = emptyMap(),
        val shizukuReady: Boolean = false,
        val runningPackageActions: Set<String> = emptySet(),
        val bulkActionRunning: Boolean = false,
        val oneKeyTargetCount: Int = 0,
        val hasAnyAppInRange: Boolean = false,
        val hasSearchResult: Boolean = false,
        val refreshing: Boolean = false
    )

    sealed interface UiEvent
}
