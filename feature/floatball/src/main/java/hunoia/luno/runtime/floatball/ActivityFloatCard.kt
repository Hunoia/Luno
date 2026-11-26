package hunoia.luno.runtime.floatball

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hunoia.luno.config.model.ActivityFloatPeekLeft
import hunoia.luno.config.model.ActivityFloatPeekWidth
import hunoia.luno.core.domain.ForegroundActivity
import hunoia.luno.floatball.R
import hunoia.luno.runtime.environment.ForegroundActivityHub
import hunoia.luno.ui.component.pressClickable
import hunoia.luno.ui.icon.rememberAppLabel
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.MinInteractiveSize
import hunoia.luno.ui.theme.overlaySpec
import hunoia.luno.ui.theme.raisedSurfaceColor

/**
 * 当前界面悬浮卡片：行1「应用名 · Activity 短名」，行2 完整组件名小字。
 *
 * 交互与 [ClipFloatCard] 同款：点卡片底部浮出动作条（复制 / 关闭），再点收起；
 * 停靠态点一下恢复。不做玻璃、不画缩放点、不加阴影，理由同剪贴板卡（§4.6）。
 * 内容实时读 [ForegroundActivityHub]，前台切换即刷新；所有手势交给
 * [ClipFloatGesture] 仲裁（无提起投递 liftable=false，动作条展开时底部让位给按钮）。
 * 复制取点击时刻的最新值（collectAsState 委托在回调里现读）。
 */
@Composable
internal fun ActivityFloatCard(
    peekSide: Int,
    barOpen: Boolean,
    resizable: Boolean,
    onTap: () -> Unit,
    onCopy: (ForegroundActivity?) -> Unit,
    onClose: () -> Unit,
    onMoveStart: (Offset) -> Unit,
    onMoveTo: (Offset) -> Unit,
    onMoveEnd: () -> Unit,
    onResizeStart: (Int, Offset) -> Unit,
    onResizeTo: (Int, Offset) -> Unit,
    onResizeEnd: () -> Unit,
    onPinch: (Float) -> Unit,
    onPinchEnd: () -> Unit,
) {
    val view = LocalView.current
    val activity by ForegroundActivityHub.current.collectAsState()
    // 整体替换而非逐个闭包快照：手势对象只创建一次，回调必须每次重组后都是新的。
    val latest = rememberUpdatedState(
        ClipFloatGesture.Handlers(
            resizable = resizable,
            liftable = false,
            barOpen = barOpen,
            onTap = onTap,
            onLiftStart = {},
            onLiftCancel = {},
            onLiftRelease = {},
            onLiftMoveStart = {},
            onLiftMove = {},
            onMoveStart = onMoveStart,
            onMoveTo = onMoveTo,
            onMoveEnd = onMoveEnd,
            onResizeStart = onResizeStart,
            onResizeTo = onResizeTo,
            onResizeEnd = onResizeEnd,
            onPinch = onPinch,
            onPinchEnd = onPinchEnd,
        ),
    )
    val gesture = remember { ClipFloatGesture(view, latest.value) }
    SideEffect { gesture.update(latest.value) }

    Box(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = LargeShape,
            color = raisedSurfaceColor(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(
                width = 1.dp,
                // 没有阴影可用，靠描边把卡片和任意跨应用底图分开（ui-guidelines §4.6）。
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            ),
            // 窗口尺寸 == 卡片尺寸：加阴影只会被窗口裁成四角的暗楔形（§4.6 硬规则）。
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInteropFilter { gesture.onTouch(it) }
                    .padding(horizontal = CONTENT_PADDING_H, vertical = CONTENT_PADDING_V),
                verticalArrangement = Arrangement.Center,
            ) {
                val current = activity
                if (current == null) {
                    Text(
                        text = stringResource(R.string.activity_float_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = "${appLabel(current.packageName)} · ${current.shortClassName}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = current.componentName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        ActivityActionBar(
            visible = barOpen,
            onCopy = { onCopy(activity) },
            onClose = onClose,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(ACTION_BAR_MARGIN),
        )

        if (peekSide != 0) PeekStrip(peekSide = peekSide)
    }
}

/** 应用名异步解析（缓存 + IO 补载），取不到回落包名本身。 */
@Composable
private fun appLabel(packageName: String): String {
    val context = LocalContext.current
    val pm = context.applicationContext.packageManager
    return rememberAppLabel(packageName, fallback = packageName) {
        pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
    }
}

/**
 * 底部动作条：点卡片才出现，复制 / 关闭两颗圆形按钮排成一行。
 * 与 [ClipFloatCard] 的动作条同规格：只放图标不放长文案，140dp 最窄窗宽也放得下。
 */
@Composable
private fun ActivityActionBar(
    visible: Boolean,
    onCopy: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = overlaySpec()
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(spec.durationMillis),
        label = "activityFloatBar",
    )
    if (alpha <= 0.01f) return
    Row(
        modifier = modifier.graphicsLayer {
            this.alpha = alpha
            translationY = (1f - alpha) * ACTION_BAR_SLIDE.toPx()
        },
        horizontalArrangement = Arrangement.spacedBy(ACTION_BUTTON_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionButton(Icons.Default.ContentCopy, R.string.activity_float_copy, onCopy)
        ActionButton(Icons.Default.Close, R.string.activity_float_close, onClose)
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    labelRes: Int,
    onClick: () -> Unit,
) {
    val label = stringResource(labelRes)
    Box(
        modifier = Modifier
            .size(MinInteractiveSize)
            .pressClickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(ACTION_BUTTON_SIZE),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(ACTION_ICON_SIZE),
                )
            }
        }
    }
}

/** 停靠态：只有这一段留在屏内，图标与竖条告诉用户「这里还有一张卡，点一下回来」。 */
@Composable
private fun BoxScope.PeekStrip(peekSide: Int) {
    val onLeft = peekSide == ActivityFloatPeekLeft
    Box(
        modifier = Modifier
            .align(if (onLeft) Alignment.CenterEnd else Alignment.CenterStart)
            .width(ActivityFloatPeekWidth.dp)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .align(if (onLeft) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 6.dp)
                .width(3.dp)
                .fillMaxHeight(0.4f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
        )
        Icon(
            imageVector = Icons.Default.Smartphone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
    }
}

private val CONTENT_PADDING_H = 12.dp
private val CONTENT_PADDING_V = 8.dp
private val ACTION_BAR_MARGIN = 8.dp
private val ACTION_BAR_SLIDE = 8.dp
private val ACTION_BUTTON_SIZE = 40.dp
private val ACTION_ICON_SIZE = 20.dp
private val ACTION_BUTTON_GAP = 6.dp
