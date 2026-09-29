package hunoia.luno.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import hunoia.luno.R
import hunoia.luno.shizuku.ShizukuFacade
import hunoia.luno.shizuku.ShellCommandResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CommandTester {

    suspend fun test(context: Context, command: String): ShellCommandResult =
        withContext(Dispatchers.IO) {
            ShizukuFacade.runShellCommand(context, command)
        }
}

@Composable
fun TestButtonAndOutput(
    testing: Boolean,
    testOutput: String,
    canTest: Boolean,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!canTest) return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            enabled = !testing,
            onClick = onTest,
        ) {
            Text(stringResource(if (testing) R.string.testing else R.string.test))
        }
    }
    if (testOutput.isNotBlank()) {
        TestOutputBox(testOutput)
    }
}
