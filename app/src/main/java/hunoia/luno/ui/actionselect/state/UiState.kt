package hunoia.luno.ui.actionselect

import hunoia.luno.action.model.StoredAction
import hunoia.luno.action.model.launchQualifiedName
import hunoia.luno.action.model.shortcutQualifiedName
import hunoia.luno.action.model.NewActionLibraryEntry
import hunoia.luno.config.model.SubGesture
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.LauncherInfo
import hunoia.luno.quicklaunch.model.qualifiedName
import hunoia.luno.quicklaunch.model.qualifiedNameWithIntents

data class UiState(
    val title: String = "",
    val actions: List<StoredAction> = emptyList(),
    val apps: List<AppInfo> = emptyList(),
    val createShortcuts: List<LauncherInfo> = emptyList(),
    val launchShortcuts: List<LauncherInfo> = emptyList(),
    val maxSelectCount: Int = MAX_SELECT_COUNT,
    val selectedRecord: SelectedRecord = SelectedRecord(),
    val longPressTargetIndex: Int? = null,
    val subGestures: List<SubGesture> = emptyList(),
    val excludedSubGestureId: String = "",

    /**
     * 本页是自动化规则的动作选择(而不是触钮/子手势的)。
     * 子手势动作需要一根真实手指才能进入,绑到自动化规则上会静默无效,所以要区别对待。
     */
    val isAutomation: Boolean = false,
    val actionLibraryEntries: List<NewActionLibraryEntry> = emptyList(),

    /** 传给 `ActionDefinitions.pickerDefinitions()` 的排除集。恒非空 —— 空集合会让过滤失效。 */
    val excludedTypeIds: Set<String> = emptySet(),
) {
    data class SelectedRecord(val list: List<Any> = emptyList()) {

        val size: Int get() = list.size

        fun selectAll(actions: List<StoredAction>): SelectedRecord {
            return this.copy(list = actions.toList())
        }

        private fun pick(action: StoredAction, maxSelectCount: Int): List<Any> {
            return if (maxSelectCount <= 1) listOf(action) else list + action
        }

        fun selectAction(action: StoredAction, selected: Boolean, maxSelectCount: Int): SelectedRecord {
            if (!selected) {
                return this.copy(list = list.filterNot { it is StoredAction && it == action })
            }
            return this.copy(list = pick(action, maxSelectCount))
        }

        fun selectAppInfo(app: AppInfo, selected: Boolean, maxSelectCount: Int): SelectedRecord {
            val action = app.toStoredAction()
            if (!selected) {
                return this.copy(list = list.filterNot { (it as? StoredAction)?.launchQualifiedName() == app.qualifiedName })
            }
            return this.copy(list = pick(action, maxSelectCount))
        }

        fun selectShortcutInfo(shortcut: LauncherInfo.ShortcutInfo, selected: Boolean, maxSelectCount: Int): SelectedRecord {
            val action = shortcut.toStoredAction()
            if (!selected) {
                return this.copy(list = list.filterNot {
                    (it as? StoredAction)?.shortcutQualifiedName() == shortcut.qualifiedNameWithIntents
                })
            }
            return this.copy(list = pick(action, maxSelectCount))
        }

        fun removeAllAppInfos(list: List<AppInfo>): SelectedRecord {
            val newList = this.list.toMutableList().apply {
                removeAll {
                    val qualifiedName = (it as? StoredAction)?.launchQualifiedName() ?: return@removeAll false
                    list.any { selected -> qualifiedName == selected.qualifiedName }
                }
            }
            return this.copy(list = newList)
        }

        fun removeAllShortcutInfos(list: List<LauncherInfo.ShortcutInfo>): SelectedRecord {
            val newList = this.list.toMutableList().apply {
                removeAll {
                    val qualifiedName = (it as? StoredAction)?.shortcutQualifiedName() ?: return@removeAll false
                    list.any { selected ->
                        qualifiedName == selected.qualifiedNameWithIntents
                    }
                }
            }
            return this.copy(list = newList)
        }

        fun isSelected(obj: Any): Boolean {
            if (obj is AppInfo) {
                return list.find {
                    (it as? StoredAction)?.launchQualifiedName() == obj.qualifiedName
                } != null
            } else if (obj is LauncherInfo.ShortcutInfo) {
                return list.find {
                    (it as? StoredAction)?.shortcutQualifiedName() == obj.qualifiedNameWithIntents
                } != null
            } else if (obj is StoredAction) {
                return list.find { it is StoredAction && it == obj } != null
            }
            return obj in list
        }
    }

}

sealed interface UiEvent
