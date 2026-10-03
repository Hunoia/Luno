package hunoia.luno.ui.quicklaunch
import hunoia.luno.ui.theme.*

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import hunoia.luno.bridge.copySensitiveText
import hunoia.luno.config.model.QuickAppLauncherSettings
import hunoia.luno.ui.quicklaunch.PasswordGeneratorPanel

@Composable
internal fun SettingsPageContent(
    contentHeight: Dp,
    onUpdateLayout: ((QuickAppLauncherSettings) -> Unit)?,
    onNavigateToApp: () -> Unit,
) {
    Column(
        modifier = Modifier.height(contentHeight)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            QuickAppLauncherAdjustPanel(
                onSettingsChanged = { onUpdateLayout?.invoke(it) },
            )
        }
        Pill(
            onClick = { onNavigateToApp() },
            onDragUp = { onNavigateToApp() },
            hapticOnClick = false,
        )
    }
}

@Composable
internal fun PasswordPageContent(
    contentHeight: Dp,
    context: Context,
    onNavigateToApp: () -> Unit,
) {
    Column(
        modifier = Modifier.height(contentHeight)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            PasswordGeneratorPanel(
                onClose = { onNavigateToApp() },
                onCopyPassword = { password ->
                    copySensitiveText(context, "Generated Password", password)
                    true
                }
            )
        }
        Pill(
            onClick = { onNavigateToApp() },
            onDragUp = { onNavigateToApp() },
            hapticOnClick = false,
        )
    }
}