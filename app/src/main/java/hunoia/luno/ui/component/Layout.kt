package hunoia.luno.ui.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import hunoia.luno.ui.theme.ContentBottom
import hunoia.luno.ui.theme.PageGutter

@Composable
fun MyColumn(
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    contentPadding: Dp = PageGutter,
    bottomPadding: Dp = ContentBottom,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(contentPadding)
            .padding(bottom = bottomPadding),
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        content = content
    )
}

