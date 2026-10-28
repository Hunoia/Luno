package hunoia.luno.domain.action

import hunoia.luno.core.domain.ActionLibrarySettings
import hunoia.luno.core.domain.libraryEntryId
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.repository.cleanActions
import hunoia.luno.config.repository.cleanStepRefs
import kotlinx.coroutines.flow.Flow

/** 模板变量：picker 展示用，经 domain 转调，ui 不得直引 runtime。 */
typealias TemplateVariable = hunoia.luno.action.template.TemplateVariable

/** 动作库域门面（ui 层唯一入口）。 */
object ActionLibrary {

    val settings: Flow<ActionLibrarySettings> = ConfigProvider.actionLibrarySettings

    suspend fun getSettings(): ActionLibrarySettings = ConfigProvider.getActionLibrarySettings()

    suspend fun updateSettings(transform: suspend (ActionLibrarySettings) -> ActionLibrarySettings) =
        ConfigProvider.updateActionLibrarySettings(transform)

    /** picker 用的全部模板变量。 */
    val templateVariables: List<TemplateVariable>
        get() = hunoia.luno.action.template.RuntimeVariables.all

    /**
     * 删除库条目：按钮/子手势里指向该条目的动作清掉，
     * 自动化里引用它的步骤动作清空，最后删条目本身。
     */
    suspend fun removeEntry(entryId: String) {
        val entry = ConfigProvider.getActionLibrarySettings().entries.firstOrNull { it.id == entryId }
        removeEntryRefs(entryId)
        updateSettings { settings ->
            settings.copy(entries = settings.entries.filterNot { it.id == entryId })
        }
        entry?.let { hunoia.luno.action.model.ShortcutIconFiles.deleteFor(listOf(it.storedAction)) }
    }

    /**
     * 删除库条目后的引用清理：按钮/子手势里指向该条目的动作清掉，
     * 自动化里引用它的步骤动作清空。
     */
    suspend fun removeEntryRefs(entryId: String) {
        ConfigProvider.updateGestureButtons { buttons ->
            buttons.map { button -> button.cleanActions { it.libraryEntryId() == entryId } }
        }
        ConfigProvider.updateSubGestureSettings { settings ->
            settings.copy(subGestures = settings.subGestures.map { subGesture ->
                subGesture.cleanActions { it.libraryEntryId() == entryId }
            })
        }
        ConfigProvider.updateAutomationRules { rules ->
            // 悬空引用藏在 steps/thenSteps/elseSteps 里，逐级清掉：动作步变成空跳，
            // 规则其余步骤照跑（多步规则不该因为一条失效就整条失能）。
            rules.map { rule ->
                rule.copy(effect = rule.effect.copy(
                    steps = rule.effect.steps.cleanStepRefs { it.libraryEntryId() == entryId },
                ))
            }
        }
    }
}
