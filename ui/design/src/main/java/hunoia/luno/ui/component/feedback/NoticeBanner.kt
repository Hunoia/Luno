package hunoia.luno.ui.component.feedback

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.RowIconSize

/**
 * 顶部降级提示条：只有原因与动作，不承担状态展示（状态在下方状态卡）。
 *
 * 授权缺失 / 依赖未运行这类「页面仍可看、但改不了」的场景共用这一份，
 * 不要再在页面里复制一份同形 Surface。
 */
@Composable
fun NoticeBanner(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = LargeShape,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(
                start = PageGutter,
                end = CardInnerSpacing,
                top = CardInnerSpacing,
                bottom = CardInnerSpacing,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                modifier = Modifier.size(RowIconSize),
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.size(CardInnerSpacing))
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onAction) {
                Text(
                    text = actionLabel,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}
