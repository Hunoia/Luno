package hunoia.luno.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import hunoia.luno.design.R
import hunoia.luno.ui.theme.CapsuleShape
import hunoia.luno.ui.component.PressIconButton

@Composable
fun AppSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector = Icons.Default.Search,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(leadingIcon, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                PressIconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.search_clear_cd))
                }
            }
        },
        // 固定高度单行输入：胶囊角色，半径随行高自适应（§2.1.1）。
        shape = CapsuleShape,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        singleLine = true
    )
}
