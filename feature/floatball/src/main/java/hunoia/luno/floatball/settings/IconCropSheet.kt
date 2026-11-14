package hunoia.luno.floatball.settings

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import hunoia.luno.floatball.R
import hunoia.luno.runtime.floatball.CustomBallIcon
import hunoia.luno.ui.component.sheet.AnchoredDialog
import hunoia.luno.ui.component.sheet.SheetActionButtons

/** 裁剪预览边长（dp）：足够大便于 pan/zoom，又留在卡片内。 */
private val IconCropPreviewSize = 200.dp

/**
 * 悬浮球自定义图标裁剪预览（选择时手动调整）。
 *
 * 复用 [CustomBallIcon]（与球面同一条渲染路径）保证 WYSIWYG：预览里看到什么，
 * 确认后球面就是什么。手势只更新 scale / offset 快照，不落盘；确认时回调
 * （scale, offsetX, offsetY），由宿主复制文件 + 写设置。
 *
 * 遮罩画在内容之上：圆内透出图标，圆外压暗（模拟全球面被裁成正圆的视觉边界）。
 */
@Composable
fun IconCropSheet(
    context: android.content.Context,
    uri: Uri,
    initialScale: Float,
    initialOffsetX: Float,
    initialOffsetY: Float,
    initialAlpha: Float = 1f,
    onDismiss: () -> Unit,
    onConfirm: (scale: Float, offsetX: Float, offsetY: Float) -> Unit,
) {
    var scale by remember { mutableFloatStateOf(initialScale.coerceIn(MinScale, MaxScale)) }
    var offsetX by remember { mutableFloatStateOf(initialOffsetX.coerceIn(-MaxOffset, MaxOffset)) }
    var offsetY by remember { mutableFloatStateOf(initialOffsetY.coerceIn(-MaxOffset, MaxOffset)) }
    // 确认走两段式退场（同 ConfirmDialog）：先 dismiss() 播退场，播完再回调宿主，
    // 否则确认动作在退场起播前就把弹层卸出组合，动画被截断。
    var pendingConfirm by remember { mutableStateOf<Triple<Float, Float, Float>?>(null) }
    // pan 是像素，需换算为归一化偏移；密度在组合内读，不能进 pointerInput 的非组合 lambda。
    val pxPerUnit = with(LocalDensity.current) { IconCropPreviewSize.toPx() }

    AnchoredDialog(
        onDismiss = {
            pendingConfirm?.let { (s, ox, oy) -> onConfirm(s, ox, oy) }
            onDismiss()
        },
    ) { dismiss ->
        Text(
            text = stringResource(R.string.float_ball_custom_icon_crop_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(IconCropPreviewSize),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(IconCropPreviewSize)
                    .semantics { contentDescription = "icon crop preview" }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(MinScale, MaxScale)
                            offsetX = (offsetX + pan.x / pxPerUnit).coerceIn(-MaxOffset, MaxOffset)
                            offsetY = (offsetY + pan.y / pxPerUnit).coerceIn(-MaxOffset, MaxOffset)
                        }
                    },
            ) {
                CustomBallIcon(
                    context = context,
                    model = uri,
                    scale = scale,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    alpha = initialAlpha,
                    modifier = Modifier.size(IconCropPreviewSize),
                )
            }
            CropScrimMask(modifier = Modifier.size(IconCropPreviewSize))
        }
        Text(
            text = stringResource(R.string.float_ball_custom_icon_crop_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SheetActionButtons(
            onDismiss = dismiss,
            onConfirm = {
                pendingConfirm = Triple(scale, offsetX, offsetY)
                dismiss()
            },
            confirmLabel = stringResource(hunoia.luno.design.R.string.confirm),
        )
    }
}

/** 圆形裁剪遮罩：除内切圆外压暗，给出「全球面被裁成正圆」的边界。 */
@Composable
private fun CropScrimMask(modifier: Modifier = Modifier) {
    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)
    Canvas(modifier = modifier) {
        // EvenOdd：点在圆内绕数为偶数（不填），点在圆外矩形内为奇数（填）→ 只压暗圆外环。
        val path = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, Size(size.width, size.height)))
            addOval(Rect(Offset.Zero, Size(size.width, size.height)))
        }
        drawPath(path, color = scrim)
    }
}

private const val MinScale = 1f
private const val MaxScale = 4f
private const val MaxOffset = 0.5f
