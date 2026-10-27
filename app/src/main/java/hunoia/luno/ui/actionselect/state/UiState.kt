package hunoia.luno.ui.actionselect

import hunoia.luno.core.domain.StoredAction
import hunoia.luno.core.domain.withoutVariantKey
import hunoia.luno.core.domain.launchQualifiedName
import hunoia.luno.action.model.shortcutQualifiedName
import hunoia.luno.core.domain.ActionLibraryEntry
import hunoia.luno.config.model.ActionPanel
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.GridPanel
import hunoia.luno.config.model.SubGesture
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.LauncherInfo
import hunoia.luno.quicklaunch.model.qualifiedName
import hunoia.luno.quicklaunch.model.qualifiedNameWithIntents
import hunoia.luno.action.model.Action

/**
 * 动作同一性：比 typeId + params，忽略面板长按变体键。变体是挂在已选动作上的附加项，
 * 参与比较会让带变体的动作显示成未勾选，点一下变成追加副本。
 */
fun StoredAction.sameAction(other: StoredAction): Boolean =
    typeId == other.typeId && withoutVariantKey().params == other.withoutVariantKey().params

data class UiState(
    val title: String = "",
    val actions: List<StoredAction> = emptyList(),
    val apps: List<AppInfo> = emptyList(),
    val createShortcuts: List<LauncherInfo> = emptyList(),
    val launchShortcuts: List<LauncherInfo> = emptyList(),
    val selectedRecord: SelectedRecord = SelectedRecord(),
    val subGestures: List<SubGesture> = emptyList(),
    val excludedSubGestureId: String = "",

    /**
     * 本页是自动化规则的动作选择（而不是触钮/子手势）。子手势动作需要真实手指才能进入，
     * 绑到自动化规则上会静默无效，所以要区别对待。
     */
    val isAutomation: Boolean = false,
    val actionLibraryEntries: List<ActionLibraryEntry> = emptyList(),
    /** 选择页“规则”分组数据源：已按 rulesForPicker 过滤（只留启用，自动化模式剔除自身）。 */
    val rules: List<AutomationRule> = emptyList(),
    /** 全局动作面板列表，选择页一行一个具名面板。 */
    val actionPanels: List<ActionPanel> = emptyList(),
    /** 全局网格面板列表，选择页一行一个具名面板（与弧形面板动态行同机制）。 */
    val gridPanels: List<GridPanel> = emptyList(),
    /**
     * 本次是去动作面板挑一个面板项（而不是配触钮/子手势槽）：不显示「无动作」
     * 与面板自身两行动态行。
     */
    val isPanelItemMode: Boolean = false,
    /**
     * 网格面板项模式的当前面板 id：面板里可以再放面板动作，但自己不能引用自己。
     * 非网格面板项场景为空。
     */
    val currentGridPanelId: String = "",

    /** 传给 `ActionDefinitions.pickerDefinitions()` 的排除集。恒非空 —— 空集合会让过滤失效。 */
    val excludedTypeIds: Set<String> = emptySet(),
) {
    /**
     * 单选：一个槽至多一个动作。内部仍是 List<Any>（应用/快捷方式/库条目类型不同，
     * 统一由 [toStoredAction] 收敛），对外 [action] 才是单值。
     */
    data class SelectedRecord(val list: List<Any> = emptyList()) {

        val size: Int get() = list.size

        val isEmpty: Boolean get() = list.isEmpty()

        /**
         * 当前选中的动作（应用/快捷方式/库条目已收敛成 StoredAction）。`internal.none`
         * 归一成 null：落盘就是删键/存 null，存 none 会让「空槽」与「显式选了无」分叉。
         */
        val action: StoredAction? get() {
            val value = list.singleOrNull() ?: return null
            val stored = when (value) {
                is StoredAction -> value
                else -> value.toStoredActionOrNull()
            }
            return stored?.takeIf { it.typeId != Action.NONE_TYPE_ID }
        }

        fun selectOne(action: StoredAction?): SelectedRecord =
            copy(list = listOfNotNull(action))

        fun selectAction(action: StoredAction): SelectedRecord = selectOne(action)

        fun selectAppInfo(app: AppInfo): SelectedRecord = selectOne(app.toStoredAction())

        fun selectShortcutInfo(shortcut: LauncherInfo.ShortcutInfo): SelectedRecord =
            selectOne(shortcut.toStoredAction())

        fun isSelected(obj: Any): Boolean {
            val current = action ?: return obj is StoredAction && obj.typeId == Action.NONE_TYPE_ID
            return when (obj) {
                is AppInfo -> current.launchQualifiedName() == obj.qualifiedName
                is LauncherInfo.ShortcutInfo ->
                    current.shortcutQualifiedName() == obj.qualifiedNameWithIntents
                is StoredAction -> current.sameAction(obj)
                else -> false
            }
        }
    }
}

private fun Any.toStoredActionOrNull(): StoredAction? = runCatching { toStoredAction() }.getOrNull()

sealed interface UiEvent