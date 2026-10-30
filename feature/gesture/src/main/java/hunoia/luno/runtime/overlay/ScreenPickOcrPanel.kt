package hunoia.luno.runtime.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.gesture.R

/** 框选 OCR 的结果面板：可编辑、可选词，不再塞回幽灵卡。 */
internal data class OcrPanelState(
    val text: String,
    val loading: Boolean = false,
)

internal enum class OcrPanelAction { Copy, Search, Share, Retry, Close }

@Composable
internal fun ScreenOcrPanel(
    state: OcrPanelState,
    onTextChange: (String) -> Unit,
    onAction: (OcrPanelAction) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 点击面板外：只关面板，留在识屏层。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onAction(OcrPanelAction.Close) },
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .padding(16.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {}
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(20.dp),
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.screen_pick_ocr_result),
                    style = MaterialTheme.typography.titleMedium,
                )
                Surface(
                    onClick = { onAction(OcrPanelAction.Close) },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.screen_pick_close),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            if (state.loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                OutlinedTextField(
                    value = state.text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 260.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OcrActionIcon(
                    icon = Icons.Default.ContentCopy,
                    label = stringResource(R.string.screen_pick_ocr_copy),
                    modifier = Modifier.weight(1f),
                ) { onAction(OcrPanelAction.Copy) }
                OcrActionIcon(
                    icon = Icons.Default.Search,
                    label = stringResource(R.string.screen_pick_ocr_search),
                    modifier = Modifier.weight(1f),
                ) { onAction(OcrPanelAction.Search) }
                OcrActionIcon(
                    icon = Icons.Default.Share,
                    label = stringResource(R.string.screen_pick_ocr_share),
                    modifier = Modifier.weight(1f),
                ) { onAction(OcrPanelAction.Share) }
                OcrActionIcon(
                    icon = Icons.Default.Refresh,
                    label = stringResource(R.string.screen_pick_ocr_retry),
                    modifier = Modifier.weight(1f),
                ) { onAction(OcrPanelAction.Retry) }
            }
        }
    }
}

@Composable
private fun OcrActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.height(64.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
