package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.action.api.ActionFacade
import hunoia.luno.core.JsonSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Keep
enum class ActionLibraryType { Shell, Url, Activity, SystemTemplate, SystemApi }

@Serializable
@Keep
enum class ParamType { STRING, INT, BOOLEAN, SELECT }

@Serializable
@Keep
data class ActionLibraryRefData(
    val entryId: String
)

@Serializable
@Keep
data class SystemTemplateData(
    val templateId: String,
    val params: Map<String, String> = emptyMap()
)

@Serializable
@Keep
data class SystemApiData(
    val category: String,
    val command: String,
    val showToast: Boolean = true,
)

@Serializable
@Keep
sealed interface ActionLibraryPayload {

    @Serializable @Keep @SerialName("shell")
    data class Shell(val data: ShellCommandData = ShellCommandData()) : ActionLibraryPayload

    @Serializable @Keep @SerialName("url")
    data class Url(val data: OpenAppOrUrlData = OpenAppOrUrlData(type = OpenAppOrUrlData.TYPE_URL)) : ActionLibraryPayload

    @Serializable @Keep @SerialName("activity")
    data class Activity(val data: OpenAppOrUrlData = OpenAppOrUrlData(type = OpenAppOrUrlData.TYPE_ACTIVITY)) : ActionLibraryPayload

    @Serializable @Keep @SerialName("systemTemplate")
    data class SystemTemplate(val data: SystemTemplateData = SystemTemplateData("")) : ActionLibraryPayload

    @Serializable @Keep @SerialName("systemApi")
    data class SystemApi(val data: SystemApiData = SystemApiData("", "")) : ActionLibraryPayload
}

val ActionLibraryPayload.type: ActionLibraryType get() = when (this) {
    is ActionLibraryPayload.Shell -> ActionLibraryType.Shell
    is ActionLibraryPayload.Url -> ActionLibraryType.Url
    is ActionLibraryPayload.Activity -> ActionLibraryType.Activity
    is ActionLibraryPayload.SystemTemplate -> ActionLibraryType.SystemTemplate
    is ActionLibraryPayload.SystemApi -> ActionLibraryType.SystemApi
}

internal fun ActionLibraryType.actionValue(): String = when (this) {
    ActionLibraryType.Shell,
    ActionLibraryType.SystemTemplate,
    ActionLibraryType.SystemApi -> ActionFacade.EXECUTE_SHELL_COMMAND
    ActionLibraryType.Url -> ActionFacade.OPEN_URL
    ActionLibraryType.Activity -> ActionFacade.OPEN_APP_ACTIVITY
}

@Serializable
@Keep
data class ActionLibraryEntry(
    val id: String,
    val name: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val payload: ActionLibraryPayload,
) {
    val type: ActionLibraryType get() = payload.type

    val shellCommand: ShellCommandData get() = (payload as? ActionLibraryPayload.Shell)?.data ?: ShellCommandData()
    val openAppOrUrl: OpenAppOrUrlData get() {
        val p = payload
        return when (p) {
            is ActionLibraryPayload.Url -> p.data
            is ActionLibraryPayload.Activity -> p.data
            else -> OpenAppOrUrlData()
        }
    }
    val systemTemplate: SystemTemplateData get() = (payload as? ActionLibraryPayload.SystemTemplate)?.data ?: SystemTemplateData("")
    val systemApi: SystemApiData get() = (payload as? ActionLibraryPayload.SystemApi)?.data ?: SystemApiData("", "")

    fun updateShellCommand(data: ShellCommandData): ActionLibraryEntry =
        copy(payload = (payload as? ActionLibraryPayload.Shell)?.copy(data = data) ?: payload)

    fun updateOpenAppOrUrl(data: OpenAppOrUrlData): ActionLibraryEntry {
        val p = payload
        return when (p) {
            is ActionLibraryPayload.Url -> copy(payload = p.copy(data = data))
            is ActionLibraryPayload.Activity -> copy(payload = p.copy(data = data))
            else -> this
        }
    }

    fun updateSystemTemplate(data: SystemTemplateData): ActionLibraryEntry =
        copy(payload = (payload as? ActionLibraryPayload.SystemTemplate)?.copy(data = data) ?: payload)

    fun updateSystemApi(data: SystemApiData): ActionLibraryEntry =
        copy(payload = (payload as? ActionLibraryPayload.SystemApi)?.copy(data = data) ?: payload)

    companion object {
        fun create(type: ActionLibraryType, name: String = ""): ActionLibraryEntry {
            val payload = when (type) {
                ActionLibraryType.Shell -> ActionLibraryPayload.Shell(ShellCommandData())
                ActionLibraryType.Url -> ActionLibraryPayload.Url(OpenAppOrUrlData(type = OpenAppOrUrlData.TYPE_URL))
                ActionLibraryType.Activity -> ActionLibraryPayload.Activity(OpenAppOrUrlData(type = OpenAppOrUrlData.TYPE_ACTIVITY))
                ActionLibraryType.SystemTemplate -> ActionLibraryPayload.SystemTemplate(SystemTemplateData(""))
                ActionLibraryType.SystemApi -> ActionLibraryPayload.SystemApi(SystemApiData("", ""))
            }
            return ActionLibraryEntry(
                id = UUID.randomUUID().toString(),
                name = name,
                payload = payload,
            )
        }

        fun duplicate(entry: ActionLibraryEntry, name: String): ActionLibraryEntry {
            return entry.copy(
                id = UUID.randomUUID().toString(),
                name = name,
                createdAt = System.currentTimeMillis(),
            )
        }
    }
}

@Serializable
@Keep
data class ActionLibrarySettings(
    val entries: List<ActionLibraryEntry> = emptyList()
)

internal fun Action.actionLibraryRefId(): String? {
    if (value != ActionFacade.EXECUTE_SHELL_COMMAND &&
        value != ActionFacade.OPEN_URL &&
        value != ActionFacade.OPEN_APP_ACTIVITY
    ) {
        return null
    }
    return runCatching { JsonSerializer.decodeFromString<ActionLibraryRefData>(data).entryId }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
}
