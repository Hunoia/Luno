package hunoia.luno.picker

import hunoia.luno.core.domain.StoredAction
import hunoia.luno.core.domain.withoutVariantKey
import hunoia.luno.core.domain.launchQualifiedName
import hunoia.luno.action.model.shortcutQualifiedName
import hunoia.luno.core.domain.ActionLibraryEntry
import hunoia.luno.config.model.ActionPanel
import hunoia.luno.config.model.GridPanel
import hunoia.luno.config.model.SubGesture
import hunoia.luno.core.domain.AppInfo
import hunoia.luno.bridge.launcher.LauncherInfo
import hunoia.luno.quickaccess.model.identity
import hunoia.luno.quickaccess.model.qualifiedNameWithIntents
import hunoia.luno.quickaccess.query.SystemAppsVisibility
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
    val actionLibraryEntries: List<ActionLibraryEntry> = emptyList(),
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
     * 面板项模式的当前面板 id：面板里可以再放面板动作，但自己不能引用自己。
     * 动作面板项与网格面板项各用自己那个字段，另一个为空。
     */
    val currentGridPanelId: String = "",
    /** 同 [currentGridPanelId]，动作面板项模式下的当前动作面板 id。 */
    val currentActionPanelId: String = "",

    /** 传给 `ActionDefinitions.pickerDefinitions()` 的排除集。恒非空 —— 空集合会让过滤失效。 */
    val excludedTypeIds: Set<String> = emptySet(),
    /** true = 只挑一个动作复制 Deep Link，不写任何配置（这类选择里「无动作」无意义）。 */
    val copyDeepLink: Boolean = false,
    /** 应用tab「系统应用两类开关」页内内存开关（默认关、不记忆），影响应用清单查询。 */
    val systemVisibility: SystemAppsVisibility = SystemAppsVisibility(),
) {
    /**
     * 单选：一个槽至多一个动作。内部仍是 List<Any>（应用/快捷方式/库条目类型不同，
     * 统一由 [toStoredAction] 收敛），对外 [action] 才是单值。
     * 多选：list 可有多个（均收敛成 StoredAction），行点选切换勾选，右上角统一提交。
     */
    data class SelectedRecord(
        val list: List<Any> = emptyList(),
        /** true = 多选模式（list 里多条，点行切换，右上角提交/清空）。 */
        val multiSelect: Boolean = false,
    ) {

        val size: Int get() = list.size

        val isEmpty: Boolean get() = list.isEmpty()

        /** 多选模式下收敛成 StoredAction 的已选列表（单选时为空）。 */
        val multiActions: List<StoredAction>
            get() = if (multiSelect) list.mapNotNull { it.toStoredActionOrNull() } else emptyList()

        /**
         * 原始选择：「无动作」保留为 `internal.none`。
         * 面板项可以把「无」当占位项落盘，落盘层需要能分清「选了无」和「没选」。
         */
        val rawSelection: StoredAction? get() {
            val value = list.singleOrNull() ?: return null
            return when (value) {
                is StoredAction -> value
                else -> value.toStoredActionOrNull()
            }
        }

        /**
         * 当前选中的动作（应用/快捷方式/库条目已收敛成 StoredAction）。`internal.none`
         * 归一成 null：落盘就是删键/存 null，存 none 会让「空槽」与「显式选了无」分叉。
         */
        val action: StoredAction? get() = rawSelection?.takeIf { it.typeId != Action.NONE_TYPE_ID }

        fun selectOne(action: StoredAction?): SelectedRecord =
            copy(list = listOfNotNull(action))

        fun selectAction(action: StoredAction): SelectedRecord = selectOne(action)

        fun selectAppInfo(app: AppInfo): SelectedRecord = selectOne(app.toStoredAction())

        fun selectShortcutInfo(shortcut: LauncherInfo.ShortcutInfo): SelectedRecord =
            selectOne(shortcut.toStoredAction())

        /** 多选切换：在则移除、不在则加（收敛成 StoredAction，与单选同一口径）。 */
        fun toggle(obj: Any): SelectedRecord {
            val converted = obj.toStoredActionOrNull() ?: return this
            val exists = list.any { matchesStored(it.toStoredActionOrNull(), converted) }
            return copy(
                list = if (exists) {
                    list.filterNot { matchesStored(it.toStoredActionOrNull(), converted) }
                } else {
                    list + converted
                },
            )
        }

        fun isSelected(obj: Any): Boolean {
            if (multiSelect) {
                return list.any { matchesStored(it.toStoredActionOrNull(), obj) }
            }
            val current = action ?: return obj is StoredAction && obj.typeId == Action.NONE_TYPE_ID
            return matchesStored(current, obj)
        }

        /** 统一命中判定：候选 StoredAction vs 行对象（应用 / 快捷方式 / 动作）。 */
        private fun matchesStored(current: StoredAction?, obj: Any): Boolean {
            if (current == null) return false
            return when (obj) {
                is AppInfo -> current.launchQualifiedName() == obj.identity
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