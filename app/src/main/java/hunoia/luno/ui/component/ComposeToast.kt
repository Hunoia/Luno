package hunoia.luno.ui.component
import hunoia.luno.ui.theme.*

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

import com.aaron.compose.ktx.clipToBackground
import hunoia.luno.ui.theme.ShapeLarge
import hunoia.luno.bridge.feedback.ToastData
import hunoia.luno.bridge.feedback.channel
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun ComposeToast(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        val context = LocalContext.current
        val snackbarHostState = remember { SnackbarHostState() }
        var toastData by remember { mutableStateOf(ToastData.None) }
        LaunchedEffect(key1 = Unit) {
            for (data in channel) {
                if (!data.isEmpty) {
                    toastData = data
                }
            }
        }
        LaunchedEffect(snackbarHostState, toastData) {
            if (!toastData.isEmpty) {
                val text = when (toastData.resId != 0) {
                    true -> context.getString(toastData.resId)
                    else -> toastData.text
                }
                withTimeoutOrNull(toastData.duration) {
                    snackbarHostState.showSnackbar(text)
                }
            }
        }

        SnackbarHost(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = Spacing24)
                .padding(bottom = Spacing12),
            hostState = snackbarHostState
        ) { snackbarData ->
            Text(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clipToBackground(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = MaterialTheme.shapes.large
                    )
                    .padding(
                        vertical = 8.dp,
                        horizontal = Spacing16
                    )
                    .wrapContentSize(),
                text = snackbarData.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}