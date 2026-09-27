package hunoia.luno.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import hunoia.luno.ui.theme.TopBarPaddingExtra

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    title: String = "",
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    showBackIcon: Boolean = true,
    onTitleClick: (() -> Unit)? = null,
    titleStyle: TextStyle = MaterialTheme.typography.headlineMedium,
    postfixTitle: (@Composable () -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
    ) {
        TopAppBar(
            modifier = Modifier.fillMaxWidth(),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (titleContent != null) {
                        titleContent()
                    } else {
                        Text(
                            modifier = Modifier
                                .let {
                                    if (showBackIcon) it else {
                                        it.padding(start = TopBarPaddingExtra)
                                    }
                                }
                                .let {
                                    if (onTitleClick == null) it else it.clickable(onClick = onTitleClick)
                                },
                            text = title,
                            style = titleStyle
                        )
                        postfixTitle?.invoke()
                    }
                }
            },
            navigationIcon = {
                if (showBackIcon && onBack != null) {
                    IconButton(
                        modifier = Modifier.padding(start = TopBarPaddingExtra / 2),
                        onClick = onBack
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go Back")
                    }
                }
            },
            actions = {
                Row(modifier = Modifier.padding(end = TopBarPaddingExtra / 2)) {
                    actions()
                }
            }
        )
    }
}
