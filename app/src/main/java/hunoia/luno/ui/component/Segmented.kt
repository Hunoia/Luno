package hunoia.luno.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hunoia.luno.ui.theme.ConnectionRadius
import hunoia.luno.ui.theme.ContainerRadius
import hunoia.luno.ui.theme.SegmentedGap

private val SegmentedHeaderPadding = PaddingValues(start = 16.dp, top = 8.dp, bottom = 16.dp)

fun segmentedShape(index: Int, count: Int): RoundedCornerShape {
    val isSingle = count <= 1
    val top = if (isSingle || index == 0) ContainerRadius else ConnectionRadius
    val bottom = if (isSingle || index == count - 1) ContainerRadius else ConnectionRadius
    return RoundedCornerShape(
        topStart = top,
        topEnd = top,
        bottomStart = bottom,
        bottomEnd = bottom,
    )
}

@Composable
fun SegmentedGroup(
    title: String = "",
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(SegmentedHeaderPadding),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(SegmentedGap)) {
            content()
        }
    }
}
