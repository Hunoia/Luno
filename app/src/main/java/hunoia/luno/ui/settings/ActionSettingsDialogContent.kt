package hunoia.luno.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import hunoia.luno.action.api.ActionFacade
import hunoia.luno.config.model.Action
import hunoia.luno.ui.component.actionText

@Composable
fun ActionSettingsDialogContent(
    onDismissRequest: () -> Unit,
    action: Action,
    autoDismiss: Boolean = true,
    onActionDataChanged: (String) -> Unit = {}
) {
    val context = LocalContext.current
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = context.actionText(action))
        },
        text = {
            when (action.value) {
                ActionFacade.OPEN_APP_ACTIVITY -> ActivitySettingsContent(
                    action = action,
                    onConfirm = {
                        onActionDataChanged(it)
                        if (autoDismiss) onDismissRequest()
                    }
                )
                ActionFacade.OPEN_URL -> UrlSettingsContent(
                    action = action,
                    onConfirm = {
                        onActionDataChanged(it)
                        if (autoDismiss) onDismissRequest()
                    }
                )
                ActionFacade.EXECUTE_SHELL_COMMAND -> ShellCommandSettingsContent(
                    action = action,
                    onConfirm = {
                        onActionDataChanged(it)
                        if (autoDismiss) onDismissRequest()
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}
