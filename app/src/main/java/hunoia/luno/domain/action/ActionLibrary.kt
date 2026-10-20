package hunoia.luno.domain.action

import hunoia.luno.action.model.NewActionLibrarySettings
import hunoia.luno.action.model.libraryEntryId
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.RuleStep
import hunoia.luno.config.repository.cleanActions
import kotlinx.coroutines.flow.Flow

/** 模板变量：picker 展示用，经 domain 转调，ui 不得直引 runtime。 */
typealias TemplateVariable = hunoia.luno.action.template.TemplateVariable

/** 动作库域门面（ui 层唯一入口）。 */
object ActionLibrary {

    val settings: Flow<NewActionLibrarySettings> = ConfigProvider.newActionLibrarySettings

    suspend fun getSettings(): NewActionLibrarySettings = ConfigProvider.getNewActionLibrarySettings()

    suspend fun updateSettings(transform: suspend (NewActionLibrarySettings) -> NewActionLibrarySettings) =
        ConfigProvider.updateNewActionLibrarySettings(transform)

    /** picker 用的全部模板变量。 */
    val templateVariables: List<TemplateVariable>
        get() = hunoia.luno.action.template.RuntimeVariables.all

    /**
     * 删除库条目：按钮/子手势里指向该条目的动作清掉，
     * 自动化里引用它的步骤动作清空，最后删条目本身。
     */
    suspend fun removeEntry(entryId: String) {
        val entry = ConfigProvider.getNewActionLibrarySettings().entries.firstOrNull { it.id == entryId }
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
                rule.copy(effect = rule.effect.copy(steps = rule.effect.steps.cleanSteps(entryId)))
            }
        }
    }
}

/** 递归清理步骤树里对已删条目的引用（含 if 分支）。 */
private fun List<RuleStep>.cleanSteps(entryId: String): List<RuleStep> = map { step ->
    val actionCleared = step.action?.libraryEntryId() == entryId
    step.copy(
        action = if (actionCleared) null else step.action,
        thenSteps = step.thenSteps.cleanSteps(entryId),
        elseSteps = step.elseSteps.cleanSteps(entryId),
    )
}
