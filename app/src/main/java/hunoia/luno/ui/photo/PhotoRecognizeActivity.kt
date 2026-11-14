package hunoia.luno.ui.photo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import hunoia.luno.R
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.capability.ocr.OcrModelStatus
import hunoia.luno.capability.ocr.OcrModelStore
import hunoia.luno.capability.ocr.OcrText
import hunoia.luno.config.model.OcrScript
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.domain.ocr.OcrEngineHolder
import hunoia.luno.ui.ocr.OcrResultActivity
import hunoia.luno.ui.ocr.OcrResultPayload
import hunoia.luno.ui.theme.SideGestureTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 拍照识别入口页（动作 `image.recognize`，计划 §3.2 拍照识别）。
 *
 * 相机走**系统相机 App**（`ACTION_IMAGE_CAPTURE` + FileProvider 输出，不引 CameraX、不增包体积）；
 * 页面内同时给「从相册选」按钮（`GetContent`，免权限）。选完/拍完进 [OcrResultActivity]，
 * 在结果页内拖框选区域再 OCR —— 复用识屏/剪贴板 OCR 的结果页，不新做裁剪 UI。
 *
 * 与剪贴板 OCR 同口径的边界：
 * - 识别只在结果页拖框（`deferOcrToSelection`）或显式切 OCR 来源时触发，不自动 OCR 整图；
 * - OCR 语言跟随识屏设置（[hunoia.luno.config.model.ScreenPickSettings.ocrScripts]，默认中文）；
 * - 不额外落相册、不进建议胶囊。
 *
 * 模型未就绪时 fail fast：`ACTION_IMAGE_CAPTURE` 前先检查，避免拍完才发现没法识别。
 */
class PhotoRecognizeActivity : ComponentActivity() {

    companion object {
        /** 从执行口（`ExecutorSurfaces.openPhotoRecognize`）拉起；返回 false = 启动失败。 */
        fun start(context: Context): Boolean = runCatching {
            context.startActivity(
                Intent(context, PhotoRecognizeActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }.isSuccess

        /** 解码长边上限：照片偏「看清小字」，比剪贴板 OCR（1600）更大，与图片标注同口径。 */
        private const val MAX_DECODE_PX = 4096
        private const val CAPTURE_DIR = "camera_capture"
        private const val CAPTURE_NAME = "capture.jpg"
    }

    /** 系统相机写入的临时文件（FileProvider 输出），拍完即解码，不留档。 */
    private var pendingCaptureUri: Uri? = null

    /** 系统相机 App（ACTION_IMAGE_CAPTURE + FileProvider 输出）；合约自带读写 grant 标志。 */
    private val cameraCapture = registerForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { ok ->
        val uri = pendingCaptureUri
        pendingCaptureUri = null
        if (!ok || uri == null) {
            showToast(R.string.photo_recognize_capture_failed)
            return@registerForActivityResult
        }
        openOcr(uri)
    }

    private val galleryPick = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) openOcr(uri)
    }

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) launchCamera() else showToast(R.string.photo_recognize_need_camera_permission)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 识别依赖 OCR 模型：缺模型时不进拍照页，直接带因提示（与剪贴板 OCR 同口径）。
        if (OcrModelStore.status(this) !is OcrModelStatus.Ready) {
            showToast(R.string.ocr_model_missing_hint)
            finish()
            return
        }
        enableEdgeToEdge()
        setContent { SideGestureTheme { PhotoPickContent(onCamera = ::onTakePhoto, onGallery = ::onPickGallery) } }
    }

    private fun onTakePhoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val dir = File(cacheDir, CAPTURE_DIR).apply { mkdirs() }
        val uri = runCatching {
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", File(dir, CAPTURE_NAME))
        }.getOrNull()
        if (uri == null) {
            showToast(R.string.photo_recognize_capture_failed)
            return
        }
        pendingCaptureUri = uri
        runCatching { cameraCapture.launch(uri) }.onFailure {
            pendingCaptureUri = null
            showToast(R.string.photo_recognize_capture_failed)
        }
    }

    private fun onPickGallery() {
        galleryPick.launch("image/*")
    }

    /** 解码 → 开 OCR 结果页（先看图、拖框再识别）。位图所有权交给结果页，由它回收。 */
    private fun openOcr(uri: Uri) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { decode(uri) }
            if (bitmap == null) {
                showToast(R.string.photo_recognize_decode_failed)
                return@launch
            }
            val scripts = runCatching {
                GestureConfig.initial.first().screenPick.ocrScripts
                    .ifEmpty { listOf(OcrScript.Chinese) }
            }.getOrDefault(listOf(OcrScript.Chinese))
            val engine = OcrEngineHolder.get(this@PhotoRecognizeActivity)
            val loader: suspend (Bitmap) -> String? = { input ->
                val source = withContext(Dispatchers.IO) {
                    runCatching {
                        if (input.isRecycled) null else input.copy(Bitmap.Config.ARGB_8888, false)
                    }.getOrNull()
                }
                if (source == null) {
                    null
                } else {
                    try {
                        val result = engine.recognize(source, scripts)
                        OcrText.join(result.blocks).ifBlank { null }
                    } catch (_: Throwable) {
                        null
                    } finally {
                        if (!source.isRecycled) source.recycle()
                    }
                }
            }
            val opened = OcrResultActivity.start(
                this@PhotoRecognizeActivity,
                OcrResultPayload(
                    image = bitmap,
                    ocrLoader = loader,
                    preferImage = true,
                    deferOcrToSelection = true,
                ),
            )
            if (!opened) {
                if (!bitmap.isRecycled) bitmap.recycle()
            } else {
                // 结果页已接管：拍照页使命完成，返回时不再落回本页。
                finish()
            }
        }
    }

    /** 走 [ImageDecoder]：按 EXIF 方向旋转（[BitmapFactory] 不处理，竖拍会横着进结果页）。 */
    private fun decode(uri: Uri): Bitmap? = runCatching {
        val source = ImageDecoder.createSource(contentResolver, uri)
        val drawable = ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            var sample = 1
            while (longest / sample > MAX_DECODE_PX) sample *= 2
            decoder.setTargetSampleSize(sample)
            // 恒用软件位图：结果页裁剪要 getPixel，HARDWARE 位图不支持。
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
        }
        (drawable as? BitmapDrawable)?.bitmap ?: decodeWithBitmapFactory(uri)
    }.getOrNull()

    /** [ImageDecoder] 不可用时保底出图（不处理 EXIF，仅作兜底）。 */
    private fun decodeWithBitmapFactory(uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > MAX_DECODE_PX) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()
}

@Composable
private fun PhotoPickContent(onCamera: () -> Unit, onGallery: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.photo_recognize_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.photo_recognize_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onCamera, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.photo_recognize_camera))
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onGallery, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.photo_recognize_gallery))
            }
        }
    }
}
