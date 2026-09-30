package hunoia.luno.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import hunoia.luno.R

@Composable
fun MyAlertDialog(
    onDismissRequest: () -> Unit,
    onConfirmClick: () -> Unit,
    title: String?,
    text: String,
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
                    onDismissRequest()
                    onConfirmClick()
                }
            ) {
                Text(text = stringResource(id = R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                }
            ) {
                Text(text = stringResource(id = R.string.cancel))
            }
        }
    )
}
