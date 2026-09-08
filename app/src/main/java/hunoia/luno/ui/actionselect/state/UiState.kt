package hunoia.luno.ui.actionselect

import hunoia.luno.action.api.appInfo
import hunoia.luno.action.api.shortcutInfo
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.ActionLibraryEntry
import hunoia.luno.config.model.SubGesture
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.LauncherInfo
import hunoia.luno.quicklaunch.model.qualifiedName
import hunoia.luno.quicklaunch.model.qualifiedNameWithIntents

data class UiState(
    val title: String = "",
    val selectSingle: Boolean = false,
    val actions: List<Action> = emptyList(),
    val apps: List<AppInfo> = emptyList(),
    val createShortcuts: List<LauncherInfo> = emptyList(),
    val launchShortcuts: List<LauncherInfo> = emptyList(),
    val maxSelectCount: Int = MAX_SELECT_COUNT,
    val selectedRecord: SelectedRecord = SelectedRecord(),
    val longPressTargetIndex: Int? = null,
    val actionSettingsDialog: ActionSettingsDialogValue = ActionSettingsDialogValue(false, Action.NONE),
    val subGestures: List<SubGesture> = emptyList(),
    val excludedSubGestureId: String = "",
    val actionLibraryEntries: List<ActionLibraryEntry> = emptyList(),
) {
    data class SelectedRecord(val list: List<Any> = emptyList()) {

        val size: Int get() = list.size

        fun selectAll(actions: List<Action>): SelectedRecord {
            return this.copy(list = actions.toList())
        }

        fun selectAction(action: Action, selected: Boolean): SelectedRecord {
            val newList = list.toMutableList().apply {
                if (selected) {
                    add(action)
                } else {
                    removeAll { it is Action && it.sameAction(action) }
                }
            }
            return this.copy(list = newList)
        }

        fun selectAppInfo(app: AppInfo, selected: Boolean): SelectedRecord {
            val newList = list.toMutableList().apply {
                if (selected) {
                    add(app.toAction())
                } else {
                    removeAll { (it as? Action)?.appInfo?.qualifiedName == app.qualifiedName }
                }
            }
            return this.copy(list = newList)
        }

        fun selectShortcutInfo(shortcut: LauncherInfo.ShortcutInfo, selected: Boolean): SelectedRecord {
            val newList = list.toMutableList().apply {
                if (selected) {
                    add(shortcut.toAction())
                } else {
                    removeAll {
                        (it as? Action)?.shortcutInfo?.qualifiedNameWithIntents == shortcut.qualifiedNameWithIntents
                    }
                }
            }
            return this.copy(list = newList)
        }

        fun removeAllAppInfos(list: List<AppInfo>): SelectedRecord {
            val newList = this.list.toMutableList().apply {
                removeAll {
                    val appInfo = (it as? Action)?.appInfo ?: return@removeAll false
                    list.any { selected -> appInfo.qualifiedName == selected.qualifiedName }
                }
            }
            return this.copy(list = newList)
        }

        fun removeAllShortcutInfos(list: List<LauncherInfo.ShortcutInfo>): SelectedRecord {
            val newList = this.list.toMutableList().apply {
                removeAll {
                    val shortcutInfo = (it as? Action)?.shortcutInfo ?: return@removeAll false
                    list.any { selected ->
                        shortcutInfo.qualifiedNameWithIntents == selected.qualifiedNameWithIntents
                    }
                }
            }
            return this.copy(list = newList)
        }

        fun isSelected(obj: Any): Boolean {
            if (obj is AppInfo) {
                return list.find {
                    (it as? Action)?.appInfo?.qualifiedName == obj.qualifiedName
                } != null
            } else if (obj is LauncherInfo.ShortcutInfo) {
                return list.find {
                    (it as? Action)?.shortcutInfo?.qualifiedNameWithIntents == obj.qualifiedNameWithIntents
                } != null
            } else if (obj is Action) {
                return list.find { it is Action && it.sameAction(obj) } != null
            }
            return obj in list
        }
    }

    data class ActionSettingsDialogValue(
        val show: Boolean,
        val action: Action
    )
}

sealed interface UiEvent
