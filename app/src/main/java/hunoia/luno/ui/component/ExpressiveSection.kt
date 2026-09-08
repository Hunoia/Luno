package hunoia.luno.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import hunoia.luno.ui.theme.CardShape
import hunoia.luno.ui.theme.ContentPaddingHorizontal
import hunoia.luno.ui.theme.SectionTitlePadding
import hunoia.luno.ui.theme.Spacing8

@Composable
fun ExpressiveSection(
    modifier: Modifier = Modifier,
    title: String = "",
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title.isNotEmpty()) {
            Text(
                modifier = Modifier
                    .padding(bottom = SectionTitlePadding)
                    .padding(horizontal = ContentPaddingHorizontal),
                text = title,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier = Modifier.padding(Spacing8),
                verticalArrangement = Arrangement.spacedBy(Spacing8),
            ) {
                content()
            }
        }
    }
}
