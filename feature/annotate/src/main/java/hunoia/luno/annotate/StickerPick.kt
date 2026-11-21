package hunoia.luno.annotate

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import hunoia.luno.annotate.model.Pt
import hunoia.luno.bridge.ui.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 贴纸选择交接中心（P2e）：`StickerPickActivity` 在 :service 进程内解码选中图片后
 * 交付到这里的 Compose state，画布（同进程）经 snapshotFlow 观察并就地插入贴纸元素。
 *
 * 为什么不走 Activity Result：浮层宿主是 Service 上下文，无法 `startActivityForResult`；
 * 与 [ImageAnnotateHandoff] 同款「进程内对象交接」思路，只保留最后一次交付。
 */
object StickerPickCenter {

    /** 待插入的贴纸位图；null = 无。画布消费后置空。 */
    private var pendingBitmap by mutableStateOf<Bitmap?>(null)

    /** 贴纸落点（图片像素）：由画布 STICKER 手势在发起选图前写入；null = 画布中心。 */
    var tapPoint: Pt? = null

    val pending: Bitmap? get() = pendingBitmap

    fun deliver(bitmap: Bitmap) {
        pendingBitmap = bitmap
    }

    fun consume(): Bitmap? {
        val bitmap = pendingBitmap
        pendingBitmap = null
        return bitmap
    }
}

/**
 * 贴纸选择壳 Activity（P2e）：无界面，进来就弹系统图片选择器；
 * 选中 → 进程内解码（长边 ≤ [MAX_STICKER_PX]，走 ImageDecoder 保 EXIF 方向）
 * → 交付 [StickerPickCenter] → 结束自己。取消选择同样直接结束。
 */
class StickerPickActivity : ComponentActivity() {

    companion object {
        private const val MAX_STICKER_PX = 1024

        /** 从 `:service`（浮层宿主）或图片编辑页拉起；返回是否启动成功。 */
        fun start(context: Context): Boolean = runCatching {
            context.startActivity(
                Intent(context, StickerPickActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
    }

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            finish()
            return@registerForActivityResult
        }
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                AnnotateImageDecoder.decodeSampled(this@StickerPickActivity, uri, MAX_STICKER_PX)
            }
            if (bitmap != null) {
                StickerPickCenter.deliver(bitmap)
            } else {
                showToast(R.string.annotate_image_load_failed)
            }
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            runCatching { picker.launch("image/*") }
                .onFailure { finish() }
        }
    }
}

/**
 * 标注模块共用图片解码（P2e）：ImageDecoder 优先（按 EXIF 方向旋转、原生
 * HEIF/WebP、一次采样），失败回退 BitmapFactory。统一恒出软件位图
 * （马赛克取样要 getPixel；贴纸缩放也不依赖 HARDWARE）。
 */
object AnnotateImageDecoder {

    /** 解码 [uri] 并采样到长边 ≤ [maxLongestPx]。失败返回 null。 */
    fun decodeSampled(context: Context, uri: android.net.Uri, maxLongestPx: Int): Bitmap? = runCatching {
        val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
        val drawable = android.graphics.ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            var sample = 1
            while (longest / sample > maxLongestPx) sample *= 2
            decoder.setTargetSampleSize(sample)
            decoder.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE)
        }
        (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: decodeFallback(context, uri, maxLongestPx)
    }.getOrElse { decodeFallback(context, uri, maxLongestPx) }

    /** 老路径兜底（不处理 EXIF）：仅在 ImageDecoder 不可用时保底出图。 */
    private fun decodeFallback(context: Context, uri: android.net.Uri, maxLongestPx: Int): Bitmap? = runCatching {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > maxLongestPx) sample *= 2
        val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, options)
        }
    }.getOrNull()
}
