package hunoia.luno.ui.component.feedback

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import hunoia.luno.ui.theme.EmptyStatePaddingVertical
import hunoia.luno.ui.theme.LargeGutter
import hunoia.luno.ui.theme.ListSpacing

/**
 * 反馈组件（见 docs/ui-spec.md §7）：加载 / 错误与空态共用同一种版式
 * （垂直大留白 + 居中内容），调用方只给文案与重试入口。
 */

/** 加载态：居中菊花 + 文案。 */
@Composable
fun LoadingState(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = EmptyStatePaddingVertical),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(ListSpacing))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = ListSpacing),
            )
        }
    }
}

/** 错误态：文案 + 可选重试。无重试时只展示文案。 */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "",
    containerColor: Color? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = EmptyStatePaddingVertical, horizontal = LargeGutter),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = containerColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (onRetry != null && retryLabel.isNotEmpty()) {
                TextButton(onClick = onRetry, modifier = Modifier.padding(top = ListSpacing)) {
                    Text(retryLabel)
                }
            }
        }
    }
}
