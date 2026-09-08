package hunoia.luno.ui.freeze

import androidx.lifecycle.viewModelScope
import com.aaron.compose.base.BaseComposeVM
import hunoia.luno.core.AppContext
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.freeze.FreezeFacade
import hunoia.luno.ui.freeze.FrozenAppBlacklistVM.UiEvent
import hunoia.luno.ui.freeze.FrozenAppBlacklistVM.UiState
import hunoia.luno.config.ConfigProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class FrozenAppBlacklistVM : BaseComposeVM<UiState, UiEvent>() {

    override val initialState: UiState = UiState()

    init {
        loadData()
    }

    fun selectApp(appInfo: AppInfo, selected: Boolean) {
        updateUiState {
            val mutableList = it.excludeApps.toMutableList()
            if (selected) {
                mutableList.add(appInfo.packageName)
            } else {
                mutableList.remove(appInfo.packageName)
            }
            it.copy(excludeApps = mutableList)
        }
        save()
    }

    fun selectPreviousApp(appInfo: AppInfo, selected: Boolean) {
        updateUiState {
            val mutableList = it.previousAppExcludeApps.toMutableList()
            if (selected) {
                if (appInfo.packageName !in mutableList) mutableList.add(appInfo.packageName)
            } else {
                mutableList.remove(appInfo.packageName)
            }
            it.copy(previousAppExcludeApps = mutableList)
        }
        savePreviousAppExcludeApps()
    }

    fun reset() {
        updateUiState {
            it.copy(excludeApps = emptyList(), previousAppExcludeApps = emptyList())
        }
        save()
        savePreviousAppExcludeApps()
        reloadApps()
    }

    fun reloadApps() {
        viewModelScope.launchWithLoading {
            save()
            val appInfos = withContext(Dispatchers.IO) {
                try {
                    QuickLaunchFacade
                        .queryApps(AppContext.get())
                        .filter { it.packageName != AppContext.get().packageName }
                } catch (_: Exception) {
                    emptyList()
                }
            }
            val frozenApps = withContext(Dispatchers.IO) {
                FreezeFacade.queryFrozenAppsOnIo(AppContext.get())
            }
            val normalPackageNames = appInfos.map { it.packageName }.toSet()
            val filteredFrozenApps = frozenApps.filter {
                it.packageName !in normalPackageNames && it.packageName != AppContext.get().packageName
            }
            val mergedApps = mutableListOf<AppInfo>()
            mergedApps.addAll(appInfos)
            mergedApps.addAll(filteredFrozenApps)
            arrangeAppInfos(mergedApps)
        }
    }

    private fun save() {
        viewModelScope.launch {
            ConfigProvider.updateAdvancedSettings {
                it.copy(excludeApps = uiState.excludeApps)
            }
        }
    }

    private fun savePreviousAppExcludeApps() {
        viewModelScope.launch {
            ConfigProvider.updateActionSettings {
                it.copy(previousApp = it.previousApp.copy(packageNames = uiState.previousAppExcludeApps))
            }
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(ConfigProvider.advancedSettings, ConfigProvider.actionSettings) { advancedSettings, actionSettings ->
                advancedSettings.excludeApps to actionSettings.previousApp.packageNames
            }.collectLatest { (excludeApps, previousAppExcludeApps) ->
                    updateUiState {
                        it.copy(excludeApps = excludeApps, previousAppExcludeApps = previousAppExcludeApps)
                    }
                }
            }
    }

    private suspend fun arrangeAppInfos(appInfos: List<AppInfo>) {
        val selectedList = mutableListOf<AppInfo>()
        val unselectedList = mutableListOf<AppInfo>()
        val excludeApps = uiState.excludeApps.toMutableList()
        val previousAppExcludeApps = uiState.previousAppExcludeApps.toMutableList()
        withContext(Dispatchers.Default) {
            val packageNames = appInfos.map { app -> app.packageName }
            excludeApps.apply {
                removeAll { packageName -> packageName !in packageNames }
            }
            previousAppExcludeApps.removeAll { packageName -> packageName !in packageNames }
            appInfos.forEach { info ->
                if (info.packageName in excludeApps) {
                    selectedList.add(info)
                } else {
                    unselectedList.add(info)
                }
            }
        }
        updateUiState {
            it.copy(
                excludeApps = excludeApps,
                previousAppExcludeApps = previousAppExcludeApps,
                selectedAppInfos = selectedList,
                unselectedAppInfos = unselectedList
            )
        }
    }

    data class UiState(
        val selectedAppInfos: List<AppInfo> = emptyList(),
        val unselectedAppInfos: List<AppInfo> = emptyList(),
        val excludeApps: List<String> = emptyList(),
        val previousAppExcludeApps: List<String> = emptyList(),
    )

    sealed interface UiEvent
}
