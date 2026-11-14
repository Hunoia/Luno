package hunoia.luno.runtime.floatball

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 自定义球面图标（GIF / PNG / WebP，全球面渲染）。
 *
 * 裁剪预览与 [FloatBallWindow.BallFace] 共用本组合，保证「预览所见 = 球面所得」：
 * 以 `ContentScale.Crop`（cover 填满球径）为基准，叠加 [scale]（≥1 放大）与
 * [offsetX]/[offsetY]（归一化平移，相对球径，-0.5..0.5），最后裁成正圆。
 *
 * [model] 接收 Coil 模型：球面传 `File(path)`，裁剪预览传内容 `Uri`（尚未落盘）。
 * [context] 用于解码 [model] 的首帧（[paused] 时用），也是 `Uri` 打开流的凭据。
 *
 * 变换只作用于 `graphicsLayer`，不改变布局尺寸：窗口命中区仍是 `ballPx × ballPx`
 * 的正方形（ui-guidelines §4.6），圆只是视觉呈现。
 *
 * [alpha] 整体透明度（0.1..1.0）。
 *
 * [paused] 为 true 时把动图换成其首帧静态位图显示（静置暂停，省电）。Coil 3 不暴露
 * `MovieDrawable` 句柄（`AsyncImagePainter` 无公开 drawable 访问器），无法直接 `stop()`，
 * 故用「换 model」实现：静态图与 GIF 各自有独立缓存 key，切回原 model 时命中内存缓存
 * 继续播，不会重新解码。
 *
 * 加载失败（文件被删 / 解码异常）时由调用方回退默认图标；本组合不画兜底占位。
 */
@Composable
fun CustomBallIcon(
    context: Context,
    model: Any,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    paused: Boolean = false,
) {
    val firstFrame: Bitmap? = rememberFirstFrame(context, model)
    // 仅当确实有首帧可换（GIF 已解码）且要求暂停时才切静态，避免静态图多此一举。
    val shownModel = if (paused) firstFrame ?: model else model

    val face = modifier
        .clip(CircleShape)
        .then(if (alpha < 1f) Modifier.alpha(alpha) else Modifier)

    AsyncImage(
        model = shownModel,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = face.graphicsLayer {
            scaleX = scale
            scaleY = scale
            // translation 在父坐标空间（未缩放），直接用归一化偏移 × 布局尺寸。
            translationX = offsetX * size.width
            translationY = offsetY * size.height
        },
    )
}

/**
 * 解码并缓存 [model] 的首帧静态位图。
 *
 * GIF 取第一帧，PNG / WebP / JPG 返回整图。解码放后台线程（GIF 首帧解码也需解整包），
 * 结果按 [model] 缓存，静置切换不重复解码。非图片内容返回 null（交由调用方回退）。
 */
@Composable
private fun rememberFirstFrame(context: Context, model: Any): Bitmap? {
    var frame by remember(model) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(model) {
        if (frame == null) {
            frame = withContext(Dispatchers.IO) { decodeFirstFrame(context, model) }
        }
    }
    return frame
}

/** 同步解码首帧；调用方需自行切后台线程。 */
private fun decodeFirstFrame(context: Context, model: Any): Bitmap? {
    val stream: InputStream = when (model) {
        is File -> model.inputStream()
        is Uri -> context.contentResolver.openInputStream(model)
        else -> return null
    } ?: return null
    return stream.use { input ->
        val buffered = BufferedInputStream(input)
        // GIF 由 BitmapFactory 只解第一帧；mark/reset 兜底非 mark 流。
        val bitmap = BitmapFactory.decodeStream(buffered)
        if (bitmap == null) {
            buffered.reset()
            BitmapFactory.decodeStream(buffered)
        } else bitmap
    }
}
