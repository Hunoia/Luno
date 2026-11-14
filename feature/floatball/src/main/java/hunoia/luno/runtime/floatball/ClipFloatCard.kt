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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
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
import hunoia.luno.ui.theme.CapsuleShape
import hunoia.luno.ui.theme.RowIconSize
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.MinInteractiveSize
import hunoia.luno.ui.theme.SmallShape
import hunoia.luno.ui.theme.overlaySpec
import hunoia.luno.ui.theme.ShapeExtraSmall
import hunoia.luno.ui.theme.containerShape

/**
 * 剪贴板悬浮卡片。
 *
 * 三层结构：拖动把手（纯视觉锚点）→ 内容预览 → 底部点按浮起的动作条。
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
    onTap: () -> Unit,
    onLiftStart: () -> Unit,
    onLiftCancel: () -> Unit,
    onLiftRelease: (Offset) -> Unit,
    onLiftDragStart: () -> Unit,
    onLiftDrag: (Offset) -> Unit,
    onMoveStart: (Offset) -> Unit,
    onMoveTo: (Offset) -> Unit,
    onMoveEnd: () -> Unit,
    onResizeStart: (Int, Offset) -> Unit,
    onResizeTo: (Int, Offset) -> Unit,
    onResizeEnd: () -> Unit,
    onPinch: (Float) -> Unit,
    onPinchEnd: () -> Unit,
    onInsert: () -> Unit,
    onSend: () -> Unit,
    onCopy: () -> Unit,
    onClose: () -> Unit,
) {
    val view = LocalView.current
    // 整体替换而非逐个闭包快照：手势对象只创建一次，回调必须每次重组后都是新的。
    val latest = rememberUpdatedState(
        ClipFloatGesture.Handlers(
            resizable = resizable,
            barOpen = barOpen,
            onTap = onTap,
            onLiftStart = onLiftStart,
            onLiftCancel = onLiftCancel,
            onLiftRelease = onLiftRelease,
            onLiftDragStart = onLiftDragStart,
            onLiftDrag = onLiftDrag,
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
                        .height(DRAG_STRIP_HEIGHT)
                        .padding(top = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
                    )
                }
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
            onInsert = onInsert,
            onSend = onSend,
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
 * 底部动作条：点卡片才出现，2×2 等分排列。
 *
 * 只放图标不放长文案：窗口最窄 140dp，一行塞不下 4 个 48dp 命中区，
 * 而 2.0× 字体下带标签的行必然截断（ui-guidelines §1.5 / §2.3）。
 */
@Composable
private fun ActionBar(
    visible: Boolean,
    onInsert: () -> Unit,
    onSend: () -> Unit,
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
    Surface(
        modifier = modifier.graphicsLayer {
            this.alpha = alpha
            translationY = (1f - alpha) * ACTION_BAR_SLIDE.toPx()
        },
        shape = SmallShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        // 动作条在卡片内部（离窗口边界 ≥8dp），阴影能完整画出来，不受「窗口 == 内容」限制。
        shadowElevation = 4.dp,
    ) {
        Column {
            Row(Modifier.fillMaxWidth()) {
                ActionButton(Icons.Default.ContentCopy, R.string.clip_float_copy, onCopy)
                ActionButton(Icons.Default.Close, R.string.clip_float_close, onClose)
            }
            Row(Modifier.fillMaxWidth()) {
                ActionButton(Icons.Default.Edit, R.string.clip_float_insert, onInsert)
                ActionButton(Icons.Default.Share, R.string.clip_float_send, onSend)
            }
        }
    }
}

@Composable
private fun RowScope.ActionButton(
    icon: ImageVector,
    labelRes: Int,
    onClick: () -> Unit,
) {
    val label = stringResource(labelRes)
    Box(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = MinInteractiveSize)
            .pressClickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(RowIconSize),
        )
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

/** 拖出投递时的跟手胶囊：说明这一条会被插入还是粘贴。 */
@Composable
internal fun ClipFloatDragGhost(content: ClipFloatContent) {
    Surface(
        shape = CapsuleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
        // ghost 窗口是 WRAP_CONTENT、正好包住胶囊，阴影同样会被裁掉（§4.6 硬规则）。
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (content.kind == ClipKind.IMAGE) Icons.Default.Image else Icons.Default.TextFields,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(
                    if (content.kind == ClipKind.IMAGE) {
                        R.string.clip_float_release_paste
                    } else {
                        R.string.clip_float_release_insert
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

private val DRAG_STRIP_HEIGHT = 20.dp
private val CONTENT_PADDING_H = 12.dp
private val CONTENT_PADDING_V = 4.dp
private val ACTION_BAR_MARGIN = 8.dp
private val ACTION_BAR_SLIDE = 8.dp

/** 文本预览上限（ui-guidelines §4.6）。 */
private const val CONTENT_MAX_LINES = 6
