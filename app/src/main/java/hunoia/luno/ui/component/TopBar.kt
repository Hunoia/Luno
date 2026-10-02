package hunoia.luno.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import hunoia.luno.ui.theme.TopBarPaddingExtra
import hunoia.luno.ui.theme.TopBarTitlePadding
import hunoia.luno.ui.theme.glassSurfaceColor
import hunoia.luno.ui.theme.liquidGlassBlur
import top.yukonga.miuix.kmp.blur.LayerBackdrop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    modifier: Modifier = Modifier,
    title: String = "",
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    showBackIcon: Boolean = true,
    onTitleClick: (() -> Unit)? = null,
    titleStyle: TextStyle = MaterialTheme.typography.titleLarge,
    postfixTitle: (@Composable () -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior = exitUntilCollapsedScrollBehavior(),
    backdrop: LayerBackdrop? = null,
) {
    val surfaceColor = glassSurfaceColor()
    TopAppBar(
        modifier = modifier.liquidGlassBlur(backdrop),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (titleContent != null) {
                    titleContent()
                } else {
                    Text(
                        modifier = Modifier
                            .padding(start = TopBarTitlePadding)
                            .graphicsLayer { alpha = 1f - scrollBehavior.state.collapsedFraction }
                            .let { if (onTitleClick == null) it else it.clickable(onClick = onTitleClick) },
                        text = title,
                        style = titleStyle,
                    )
                    postfixTitle?.invoke()
                }
            }
        },
        navigationIcon = {
            if (showBackIcon && onBack != null) {
                IconButton(
                    modifier = Modifier.padding(start = TopBarPaddingExtra / 2),
                    onClick = onBack,
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go Back")
                }
            }
        },
        actions = {
            Row(modifier = Modifier.padding(end = TopBarPaddingExtra / 2)) {
                actions()
            }
        },
        expandedHeight = TopAppBarDefaults.MediumAppBarExpandedHeight,
        windowInsets = TopAppBarDefaults.windowInsets,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = surfaceColor,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            scrolledContainerColor = surfaceColor,
        ),
    )
}
