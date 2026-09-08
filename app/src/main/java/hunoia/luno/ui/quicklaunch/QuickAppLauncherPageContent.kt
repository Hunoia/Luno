package hunoia.luno.ui.quicklaunch
import hunoia.luno.ui.theme.*

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
        modifier = Modifier
            .height(contentHeight)
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    var drag = 0f
                    detectVerticalDragGestures(
                        onDragStart = { drag = 0f },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            drag += amount
                            if (drag > 32f) {
                                onNavigateToApp()
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(vertical = Spacing6)
                    .clickable {
                        onNavigateToApp()
                    }
            ) {
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(Spacing5)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                )
            }
        }
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    var drag = 0f
                    detectVerticalDragGestures(
                        onDragStart = { drag = 0f },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            drag += amount
                            if (drag > 32f) {
                                onNavigateToApp()
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(vertical = Spacing6)
                    .clickable {
                        onNavigateToApp()
                    }
            ) {
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(Spacing5)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                )
            }
        }
    }
}
