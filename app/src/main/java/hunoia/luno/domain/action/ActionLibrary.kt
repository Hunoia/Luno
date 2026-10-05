package hunoia.luno.domain.action

import hunoia.luno.action.model.NewActionLibrarySettings
import hunoia.luno.action.model.libraryEntryId
import hunoia.luno.config.ConfigProvider
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
     * 自动化里引用它的 effect 失能不断规则，最后删条目本身。
     */
    suspend fun removeEntry(entryId: String) {
        removeEntryRefs(entryId)
        updateSettings { settings ->
            settings.copy(entries = settings.entries.filterNot { it.id == entryId })
        }
    }

    /**
     * 删除库条目后的引用清理：按钮/子手势里指向该条目的动作清掉，
     * 自动化里引用它的 effect 失能不断规则。
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
            rules.map { rule ->
                if (rule.effect.entryId == entryId) {
                    rule.copy(enabled = false, effect = rule.effect.copy(entryId = ""))
                } else {
                    rule
                }
            }
        }
    }
}
