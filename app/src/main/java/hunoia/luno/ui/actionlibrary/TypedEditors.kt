package hunoia.luno.ui.actionlibrary

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import hunoia.luno.action.api.ActionFacade
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.OpenAppOrUrlData
import hunoia.luno.config.model.ShellCommandData
import hunoia.luno.core.JsonSerializer
import hunoia.luno.ui.settings.ActivitySettingsContent
import hunoia.luno.ui.settings.ShellCommandSettingsContent
import hunoia.luno.ui.settings.UrlSettingsContent

@Composable
fun ShellCommandTypedEditor(
    data: ShellCommandData,
    onValueChange: (ShellCommandData) -> Unit,
    modifier: Modifier = Modifier,
) {
    ShellCommandSettingsContent(
        action = Action(
            value = ActionFacade.EXECUTE_SHELL_COMMAND,
            data = JsonSerializer.encodeToString(data),
        ),
        onConfirm = {},
        showConfirmButton = false,
        showTestButton = false,
        showTemplateMode = true,
        modifier = modifier,
        onDataChange = { json ->
            val parsed = JsonSerializer.decodeFromString<ShellCommandData>(json)
            onValueChange(parsed)
        },
    )
}

@Composable
fun UrlTypedEditor(
    data: OpenAppOrUrlData,
    onValueChange: (OpenAppOrUrlData) -> Unit,
    modifier: Modifier = Modifier,
) {
    UrlSettingsContent(
        action = Action(
            value = ActionFacade.OPEN_URL,
            data = JsonSerializer.encodeToString(data),
        ),
        onConfirm = {},
        showConfirmButton = false,
        modifier = modifier,
        onDataChange = { json: String ->
            val parsed = JsonSerializer.decodeFromString<OpenAppOrUrlData>(json)
            onValueChange(parsed)
        },
    )
}

@Composable
fun ActivityTypedEditor(
    data: OpenAppOrUrlData,
    onValueChange: (OpenAppOrUrlData) -> Unit,
    modifier: Modifier = Modifier,
) {
    ActivitySettingsContent(
        action = Action(
            value = ActionFacade.OPEN_APP_ACTIVITY,
            data = JsonSerializer.encodeToString(data),
        ),
        onConfirm = {},
        modifier = modifier,
        onDataChange = { json: String ->
            val parsed = JsonSerializer.decodeFromString<OpenAppOrUrlData>(json)
            onValueChange(parsed)
        },
    )
}
