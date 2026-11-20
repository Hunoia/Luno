package hunoia.luno.runtime.floatball

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import kotlin.math.roundToInt
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
 * [paused] 为 true 时把动图换成其首帧静态位图显示（静置或浮窗不可见时暂停，省电）。
 * 不可见 ≠ 不绘制：窗口只是 alpha 归零时仍按刷新率重组/重绘，动图必须显式暂停。Coil 3 不暴露
 * `MovieDrawable` 句柄（`AsyncImagePainter` 无公开 drawable 访问器），无法直接 `stop()`，
 * 故用「换 model」实现：静态图与 GIF 各自有独立缓存 key，切回原 model 时命中内存缓存
 * 继续播，不会重新解码。
 *
 * 解码尺寸（[decodeRequest]）：必须显式给 `Precision.EXACT` —— `coil-gif` 的
 * `AnimatedImageDecoder` 只在「`isSampled` 或 `precision == EXACT`」时才调
 * `ImageDecoder.setTargetSize`，默认的 `INEXACT` 连尝试都不做。
 * 注意实测结论：**动图源即使给了 targetSize 仍解在源尺寸**（球窗 164 px、请求 164 + EXACT，
 * atrace 里纹理是 240×240 = GIF 源尺寸），动图的解码尺寸由平台决定，应用侧改不动；
 * 单帧静态图则会按 targetSize 解，所以这里保留 EXACT。
 * 目标尺寸取「布局 px × [scale]」：裁剪缩放最大 4×，只按布局尺寸解会让放大后的图标变糊；
 * 超过源尺寸时 `computeDstSize` 不会上采样。
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
    val face = modifier
        .clip(CircleShape)
        .then(if (alpha < 1f) Modifier.alpha(alpha) else Modifier)

    BoxWithConstraints(modifier = face) {
        // 目标解码边长：布局尺寸 × 缩放。scale 放大是「裁掉边缘保留中心」，可视分辨率
        // 需求跟着涨；scale ≤ 1 时不需要比布局更大的位图。
        val targetPx = with(LocalDensity.current) {
            val widthPx = if (maxWidth.isBounded()) maxWidth.toPx() else 0f
            (widthPx * scale.coerceAtLeast(1f)).roundToInt()
        }
        val firstFrame: Bitmap? = rememberFirstFrame(context, model, targetPx)
        // 仅当确实有首帧可换（GIF 已解码）且要求暂停时才切静态，避免静态图多此一举。
        val shownModel = if (paused) firstFrame ?: model else model
        val request = remember(shownModel, targetPx) { decodeRequest(context, shownModel, targetPx) }

        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    // translation 在父坐标空间（未缩放），直接用归一化偏移 × 布局尺寸。
                    translationX = offsetX * size.width
                    translationY = offsetY * size.height
                },
        )
    }
}

/** [Dp] 是否为确定的有限值；`fillMaxSize` 之外的无约束场景退化为「不给尺寸」。 */
private fun Dp.isBounded(): Boolean = this != Dp.Infinity && this != Dp.Unspecified

/**
 * 组装球面图标的 Coil 请求。
 *
 * [targetPx] ≤ 0（布局无约束）时只给 `EXACT` 不指定尺寸，由 Coil 自行决定；
 * 其余情况固定为「边长 targetPx 的正方形」。
 *
 * `EXACT` 对单帧图生效（按 targetPx 解码）；动图实测仍按源尺寸解（见 [CustomBallIcon] 注释），
 * 保留 `EXACT` 是为了静态图标不吃源图尺寸。
 */
private fun decodeRequest(context: Context, model: Any, targetPx: Int): ImageRequest =
    ImageRequest.Builder(context)
        .data(model)
        .apply { if (targetPx > 0) size(targetPx) }
        .precision(Precision.EXACT)
        .build()

/**
 * 解码并缓存 [model] 的首帧静态位图。
 *
 * GIF 取第一帧，PNG / WebP / JPG 返回整图。解码放后台线程（GIF 首帧解码也需解整包），
 * 结果按 [model] + [targetPx] 缓存，静置切换与布局尺寸变化不重复解码。
 * 非图片内容返回 null（交由调用方回退）。
 */
@Composable
private fun rememberFirstFrame(context: Context, model: Any, targetPx: Int): Bitmap? {
    var frame by remember(model, targetPx) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(model, targetPx) {
        if (frame == null) {
            frame = withContext(Dispatchers.IO) { decodeFirstFrame(context, model, targetPx) }
        }
    }
    return frame
}

/**
 * 同步解码首帧；调用方需自行切后台线程。
 *
 * 两段解码：先只读尺寸（`inJustDecodeBounds`），再按 [targetPx] 用 `inSampleSize` 降采样。
 * 否则暂停态会常驻一张源尺寸位图（240² ≈ 230 KB），与「解码尺寸对齐球径」的意图相悖。
 */
private fun decodeFirstFrame(context: Context, model: Any, targetPx: Int): Bitmap? {
    // File 模型可能指向已被清理/跨包恢复的失效路径（配置里存的是绝对路径），
    // 打开会抛 FileNotFoundException——必须容错返回 null，兑现「加载失败由调用方
    // 回退默认图标」的契约（球面渲染 [FloatBallWindow.BallFace] 与编辑页预览共用本组合）。
    // GIF 首帧要读两次（尺寸 + 像素），这里保留「重开流」的写法，不整体读进 ByteArray。
    val open: () -> InputStream? = when (model) {
        is File -> { { runCatching { model.inputStream() }.getOrNull() } }
        is Uri -> { { runCatching { context.contentResolver.openInputStream(model) }.getOrNull() } }
        else -> return null
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open()?.use { BitmapFactory.decodeStream(BufferedInputStream(it), null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, targetPx)
    }
    return open()?.use { BitmapFactory.decodeStream(BufferedInputStream(it), null, options) }
}

/**
 * 2 的幂降采样系数，使解码后的长边不小于 [targetPx]（`BitmapFactory` 的 `inSampleSize`
 * 只认 2 的幂，其它值会被向下取整）。[targetPx] ≤ 0 表示尺寸未知，不解采样。
 */
private fun sampleSizeFor(width: Int, height: Int, targetPx: Int): Int {
    if (targetPx <= 0) return 1
    val longest = maxOf(width, height)
    var sampleSize = 1
    while (longest / (sampleSize * 2) >= targetPx) sampleSize *= 2
    return sampleSize
}
