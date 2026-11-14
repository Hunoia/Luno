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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import hunoia.luno.config.model.ClipFloatPeekLeft
import hunoia.luno.config.model.ClipFloatPeekWidth
import hunoia.luno.config.model.ClipKind
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.pressClickable
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.MinInteractiveSize
import hunoia.luno.ui.theme.overlaySpec
import hunoia.luno.ui.theme.ShapeExtraSmall
import hunoia.luno.ui.theme.containerShape

/**
 * 剪贴板悬浮卡片。
 *
 * 两层结构：内容预览 → 底部点按浮起的动作条。
 * 卡片内不出现常驻按钮、不出现四角缩放点、不做玻璃：跨应用没有同源底图，
 * 叠 haze 只会得到一层脏色（ui-guidelines §4.6）。
 *
 * 所有手势都交给 [ClipFloatGesture] 仲裁，这里只负责「手势结果长什么样」。
 */
@Composable
internal fun ClipFloatCard(
    content: ClipFloatContent,
    peekSide: Int,
    barOpen: Boolean,
    lifted: Boolean,
    resizable: Boolean,
    /** 停靠态只露一条，不进入长按提起 / 投递，避免在 48dp 可见段上误触发。 */
    liftable: Boolean,
    onTap: () -> Unit,
    onLiftStart: () -> Unit,
    onLiftCancel: () -> Unit,
    onLiftRelease: (Offset) -> Unit,
    onLiftMoveStart: (Offset) -> Unit,
    onLiftMove: (Offset) -> Unit,
    onMoveStart: (Offset) -> Unit,
    onMoveTo: (Offset) -> Unit,
    onMoveEnd: () -> Unit,
    onResizeStart: (Int, Offset) -> Unit,
    onResizeTo: (Int, Offset) -> Unit,
    onResizeEnd: () -> Unit,
    onPinch: (Float) -> Unit,
    onPinchEnd: () -> Unit,
    onCopy: () -> Unit,
    onClose: () -> Unit,
) {
    val view = LocalView.current
    // 整体替换而非逐个闭包快照：手势对象只创建一次，回调必须每次重组后都是新的。
    val latest = rememberUpdatedState(
        ClipFloatGesture.Handlers(
            resizable = resizable,
            liftable = liftable,
            barOpen = barOpen,
            onTap = onTap,
            onLiftStart = onLiftStart,
            onLiftCancel = onLiftCancel,
            onLiftRelease = onLiftRelease,
            onLiftMoveStart = onLiftMoveStart,
            onLiftMove = onLiftMove,
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

    val typeLabel = stringResource(
        if (content.kind == ClipKind.IMAGE) R.string.clip_float_image else R.string.clip_float_text,
    )

    Box(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = LargeShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(
                width = 1.dp,
                color = if (lifted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    // 没有阴影可用，靠描边把卡片和任意跨应用底图分开（见 ui-guidelines §4.6）。
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                },
            ),
            // 窗口尺寸 == 卡片尺寸：加阴影只会被窗口裁成四角的暗楔形（§4.6 硬规则）。
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = typeLabel }
                    .pointerInteropFilter { gesture.onTouch(it) },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = CONTENT_PADDING_H, vertical = CONTENT_PADDING_V),
                ) {
                    when (content.kind) {
                        ClipKind.TEXT -> Text(
                            text = content.text,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = CONTENT_MAX_LINES,
                            overflow = TextOverflow.Ellipsis,
                        )
                        ClipKind.IMAGE -> AsyncImage(
                            model = content.imageFile,
                            contentDescription = typeLabel,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(containerShape(ShapeExtraSmall)),
                        )
                    }
                }
            }
        }

        ActionBar(
            visible = barOpen,
            onCopy = onCopy,
            onClose = onClose,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(ACTION_BAR_MARGIN),
        )

        if (peekSide != 0) PeekStrip(peekSide = peekSide, content = content)
    }
}

/**
 * 底部动作条：点卡片才出现，复制 / 关闭两颗圆形按钮排成一行。
 *
 * 只放图标不放长文案：窗口最窄 140dp，两颗 48dp 命中区 + 6dp 间距 = 102dp，
 * 留出边距仍放得下；带标签的行在 2.0× 字体下必然截断（ui-guidelines §1.5 / §2.3）。
 */
@Composable
private fun ActionBar(
    visible: Boolean,
    onCopy: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = overlaySpec()
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(spec.durationMillis),
        label = "clipFloatBar",
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
        ActionButton(Icons.Default.ContentCopy, R.string.clip_float_copy, onCopy)
        ActionButton(Icons.Default.Close, R.string.clip_float_close, onClose)
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
private fun BoxScope.PeekStrip(peekSide: Int, content: ClipFloatContent) {
    val onLeft = peekSide == ClipFloatPeekLeft
    Box(
        modifier = Modifier
            .align(if (onLeft) Alignment.CenterEnd else Alignment.CenterStart)
            .width(ClipFloatPeekWidth.dp)
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
            imageVector = if (content.kind == ClipKind.IMAGE) Icons.Default.Image else Icons.Default.TextFields,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
    }
}

private val CONTENT_PADDING_H = 12.dp
private val CONTENT_PADDING_V = 4.dp
private val ACTION_BAR_MARGIN = 8.dp
private val ACTION_BAR_SLIDE = 8.dp
private val ACTION_BUTTON_SIZE = 40.dp
private val ACTION_ICON_SIZE = 20.dp
private val ACTION_BUTTON_GAP = 6.dp

/** 文本预览上限（ui-guidelines §4.6）。 */
private const val CONTENT_MAX_LINES = 6
