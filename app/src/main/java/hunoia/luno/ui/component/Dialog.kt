package hunoia.luno.ui.component
import hunoia.luno.ui.theme.*

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import hunoia.luno.R



@Composable
fun MyAlertDialog(
    onDismissRequest: () -> Unit,
    onConfirmClick: () -> Unit,
    title: String?,
    text: String,
    onCancelClick: (() -> Unit)? = null,
    autoDismissWhenClick: Boolean = true
) {
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = onDismissRequest,
        title = {
            if (!title.isNullOrEmpty()) {
                Text(text = title)
            }
        },
        text = {
            Text(text = text)
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (autoDismissWhenClick) {
                        onDismissRequest()
                    }
                    onConfirmClick()
                }
            ) {
                Text(text = stringResource(id = R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (autoDismissWhenClick) {
                        onDismissRequest()
                    }
                    onCancelClick?.invoke()
                }
            ) {
                Text(text = stringResource(id = R.string.cancel))
            }
        }
    )
}





