package hunoia.luno.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.SegmentedGap

/** 底部面板内的分组标题。 */
@Composable
fun SheetGroupHeader(@StringRes titleRes: Int) {
    SheetGroupHeader(stringResource(titleRes))
}

@Composable
fun SheetGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = ListSpacing, bottom = SegmentedGap),
    )
}
