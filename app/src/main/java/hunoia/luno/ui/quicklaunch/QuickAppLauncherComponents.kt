package hunoia.luno.ui.quicklaunch
import hunoia.luno.ui.theme.*

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import hunoia.luno.ui.theme.MediumShape
import hunoia.luno.R
import coil.compose.AsyncImage
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.model.AppInfo

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
        val cellWidth = (maxWidth - 6.dp * (keys.size - 1)) / keys.size
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
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
                        .clip(MediumShape)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppItem(
    app: AppInfo,
    iconSizePx: Int,
    iconHeight: Dp? = null,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
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
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val iconMod = if (iconHeight != null) {
            Modifier.height(iconHeight).fillMaxWidth()
        } else {
            Modifier.fillMaxWidth().aspectRatio(1f)
        }.clip(MediumShape)
        val request = remember(app.packageName, iconSizePx) {
            QuickLaunchFacade.appIconRequest(context, app.packageName, iconSizePx)
        }
        Box(modifier = iconMod) {
            // 图标未就绪时占位。改用叠加而不是 if/else 分支，避免图标到位时
            // 布局从"只有底色"跳到"底色+图标"造成一帧跳动。
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            )
            AsyncImage(
                model = request,
                contentDescription = app.label,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}
