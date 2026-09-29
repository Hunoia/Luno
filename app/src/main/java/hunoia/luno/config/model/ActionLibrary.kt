package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.action.api.ActionFacade
import hunoia.luno.action.template.SystemFunctionTemplates
import hunoia.luno.core.JsonSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Keep
enum class ActionLibraryType { Shell, Url, Activity }

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
data class ShellTemplateData(
    val templateId: String,
    val params: Map<String, String> = emptyMap()
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

    // Legacy types kept for backward-compatible deserialization of old data
    @Serializable @Keep @SerialName("systemTemplate")
    data class SystemTemplate(val data: LegacySystemTemplateData = LegacySystemTemplateData("")) : ActionLibraryPayload

    @Serializable @Keep @SerialName("systemApi")
    data class SystemApi(val data: LegacySystemApiData = LegacySystemApiData("", "")) : ActionLibraryPayload
}

@Serializable
@Keep
data class LegacySystemTemplateData(
    val templateId: String,
    val params: Map<String, String> = emptyMap()
)

@Serializable
@Keep
data class LegacySystemApiData(
    val category: String,
    val command: String,
    val showToast: Boolean = true,
)

val ActionLibraryPayload.type: ActionLibraryType get() = when (this) {
    is ActionLibraryPayload.Shell -> ActionLibraryType.Shell
    is ActionLibraryPayload.Url -> ActionLibraryType.Url
    is ActionLibraryPayload.Activity -> ActionLibraryType.Activity
    is ActionLibraryPayload.SystemTemplate -> ActionLibraryType.Shell
    is ActionLibraryPayload.SystemApi -> ActionLibraryType.Shell
}

internal fun ActionLibraryType.actionValue(): String = when (this) {
    ActionLibraryType.Shell -> ActionFacade.EXECUTE_SHELL_COMMAND
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

    val shellCommand: ShellCommandData get() = when (val p = payload) {
        is ActionLibraryPayload.Shell -> p.data
        is ActionLibraryPayload.SystemTemplate -> {
            val template = SystemFunctionTemplates.getById(p.data.templateId)
            val cmd = if (template != null) SystemFunctionTemplates.generateCommand(template, p.data.params) else ""
            ShellCommandData(cmd, showToast = true, template = ShellTemplateData(p.data.templateId, p.data.params))
        }
        is ActionLibraryPayload.SystemApi -> ShellCommandData(p.data.command, p.data.showToast)
        else -> ShellCommandData()
    }

    val openAppOrUrl: OpenAppOrUrlData get() {
        val p = payload
        return when (p) {
            is ActionLibraryPayload.Url -> p.data
            is ActionLibraryPayload.Activity -> p.data
            else -> OpenAppOrUrlData()
        }
    }

    fun updateShellCommand(data: ShellCommandData): ActionLibraryEntry {
        val p = payload
        return when (p) {
            is ActionLibraryPayload.Shell -> copy(payload = p.copy(data = data))
            is ActionLibraryPayload.SystemTemplate -> copy(payload = ActionLibraryPayload.Shell(data))
            is ActionLibraryPayload.SystemApi -> copy(payload = ActionLibraryPayload.Shell(data))
            else -> copy(payload = ActionLibraryPayload.Shell(data))
        }
    }

    fun updateOpenAppOrUrl(data: OpenAppOrUrlData): ActionLibraryEntry {
        val p = payload
        return when (p) {
            is ActionLibraryPayload.Url -> copy(payload = p.copy(data = data))
            is ActionLibraryPayload.Activity -> copy(payload = p.copy(data = data))
            else -> this
        }
    }

    companion object {
        fun create(type: ActionLibraryType, name: String = ""): ActionLibraryEntry {
            val payload = when (type) {
                ActionLibraryType.Shell -> ActionLibraryPayload.Shell(ShellCommandData())
                ActionLibraryType.Url -> ActionLibraryPayload.Url(OpenAppOrUrlData(type = OpenAppOrUrlData.TYPE_URL))
                ActionLibraryType.Activity -> ActionLibraryPayload.Activity(OpenAppOrUrlData(type = OpenAppOrUrlData.TYPE_ACTIVITY))
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
) {
    fun hasLegacyPayloads(): Boolean = entries.any {
        it.payload is ActionLibraryPayload.SystemTemplate || it.payload is ActionLibraryPayload.SystemApi
    }

    fun migrate(): ActionLibrarySettings {
        val migrated = entries.map { entry ->
            when (val p = entry.payload) {
                is ActionLibraryPayload.Shell,
                is ActionLibraryPayload.Url,
                is ActionLibraryPayload.Activity -> entry
                is ActionLibraryPayload.SystemTemplate -> {
                    val template = SystemFunctionTemplates.getById(p.data.templateId)
                    val cmd = if (template != null) SystemFunctionTemplates.generateCommand(template, p.data.params) else ""
                    entry.copy(payload = ActionLibraryPayload.Shell(
                        ShellCommandData(cmd, showToast = true, template = ShellTemplateData(p.data.templateId, p.data.params))
                    ))
                }
                is ActionLibraryPayload.SystemApi -> {
                    entry.copy(payload = ActionLibraryPayload.Shell(
                        ShellCommandData(p.data.command, p.data.showToast)
                    ))
                }
            }
        }
        return copy(entries = migrated)
    }
}

internal fun ActionLibraryEntry.isPayloadValid(): Boolean = when (payload) {
    is ActionLibraryPayload.Shell -> payload.data.command.isNotBlank()
    is ActionLibraryPayload.Url -> payload.data.url.isNotBlank()
    is ActionLibraryPayload.Activity -> payload.data.packageName.isNotBlank() && payload.data.activityClassName.isNotBlank()
    is ActionLibraryPayload.SystemTemplate -> {
        val template = SystemFunctionTemplates.getById(payload.data.templateId)
        template != null && SystemFunctionTemplates.generateCommand(template, payload.data.params).isNotBlank()
    }
    is ActionLibraryPayload.SystemApi -> payload.data.command.isNotBlank()
}

internal fun ActionLibraryEntry.resolvedShellCommand(): String? = shellCommand.command.trim().takeIf { it.isNotBlank() }

internal fun ActionLibraryEntry.matchesQuery(query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim()
    return name.contains(q, ignoreCase = true) ||
        shellCommand.command.contains(q, ignoreCase = true) ||
        openAppOrUrl.url.contains(q, ignoreCase = true) ||
        openAppOrUrl.packageName.contains(q, ignoreCase = true) ||
        openAppOrUrl.activityClassName.contains(q, ignoreCase = true) ||
        shellCommand.template?.templateId?.contains(q, ignoreCase = true) == true ||
        openAppOrUrl.queryParameters.any { parameter ->
            parameter.name.contains(q, ignoreCase = true) ||
                parameter.value.contains(q, ignoreCase = true)
        }
}

internal fun ActionLibraryType.sortIndex(): Int = when (this) {
    ActionLibraryType.Shell -> 0
    ActionLibraryType.Url -> 1
    ActionLibraryType.Activity -> 2
}

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
