package hunoia.luno.ui.quicklaunch
import hunoia.luno.ui.theme.*

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import hunoia.luno.R
import coil.compose.AsyncImage
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun KeyboardRow(
    view: View,
    keys: List<Pair<String, String?>>,
    keyHeight: Dp,
    onDelete: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    onAdjust: (() -> Unit)? = null,
    onToken: (String) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cellWidth = (maxWidth - Spacing6 * (keys.size - 1)) / keys.size
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing6), modifier = Modifier.fillMaxWidth()) {
            keys.forEach { (label, token) ->
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    if (isPressed) 0.93f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessHigh),
                    label = "keyScale"
                )
                val isFunction = label == "删除" || label == "调整"
                Surface(
                    modifier = Modifier
                        .width(cellWidth)
                        .height(keyHeight)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(MaterialTheme.shapes.medium)
                        .combinedClickable(
                            interactionSource = interactionSource,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                when (label) {
                                    "删除" -> onDelete?.invoke()
                                    "调整" -> onAdjust?.invoke()
                                    else -> token?.let(onToken)
                                }
                            },
                            onLongClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                when (label) {
                                    "QW" -> onToken("1")
                                    "ER" -> onToken("2")
                                    "TY" -> onToken("3")
                                    "UI" -> onToken("4")
                                    "OP" -> onToken("5")
                                    "AS" -> onToken("6")
                                    "DF" -> onToken("7")
                                    "GH" -> onToken("8")
                                    "JK" -> onToken("9")
                                    "L" -> onToken("0")
                                    "删除" -> onClear?.invoke()
                                }
                            }
                        ),
                    color = if (isFunction) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (isFunction) MaterialTheme.colorScheme.onTertiaryContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        when (label) {
                            "调整" -> Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.quick_app_launcher_adjust))
                            "删除" -> Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.quick_app_launcher_delete_input))
                            else -> Text(label, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun rememberAppIconAsync(context: Context, packageName: String): Drawable? {
    var icon: Drawable? by remember(packageName) { mutableStateOf(QuickLaunchFacade.getCachedIcon(packageName)) }
    if (icon == null) {
        LaunchedEffect(packageName) {
            icon = withContext(Dispatchers.IO) {
                QuickLaunchFacade.loadIcon(context, packageName)
            }?.also { QuickLaunchFacade.cacheIcon(packageName, it) }
        }
    }
    return icon
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppItem(app: AppInfo, iconHeight: Dp? = null, onClick: () -> Unit, onLongPress: () -> Unit) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) 0.92f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "appScale"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                interactionSource = interactionSource,
                onClick = { onClick() },
                onLongClick = { onLongPress() }
            )
            .padding(Spacing4),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val iconMod = if (iconHeight != null) {
            Modifier.height(iconHeight).fillMaxWidth()
        } else {
            Modifier.fillMaxWidth().aspectRatio(1f)
        }.clip(MaterialTheme.shapes.medium)
        val icon = rememberAppIconAsync(context, app.packageName)
        if (icon != null) {
            AsyncImage(
                model = icon,
                contentDescription = app.label,
                modifier = iconMod,
                contentScale = ContentScale.Fit
            )
        } else {
            Box(
                modifier = iconMod.background(MaterialTheme.colorScheme.surfaceContainer)
            )
        }
    }
}
