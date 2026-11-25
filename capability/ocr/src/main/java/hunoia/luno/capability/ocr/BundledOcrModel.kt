package hunoia.luno.capability.ocr

import android.content.Context

/**
 * 随 APK 打包的 PP-OCRv6 模型（资产直载）。
 *
 * 模型文件在 `:app` 的 `assets/ppocrv6_gpu/`（由 `syncOcrModels` 从 `docs/ppocrv` 同步，
 * 模型二进制不入 git）；本对象列出固定清单与资产路径，引擎据此加载，
 * 不再扫描 filesDir、不再校验 manifest / SHA-256。
 */
object BundledOcrModel {

    /** assets 子目录（相对 `context.assets`）。 */
    const val ASSET_DIR = "ppocrv6_gpu"

    /**
     * 内置 rec 输入宽度档位：单档 640。短行也跑 640 宽（右补白，约 2× 320 档计算量，
     * 但 rec 只占识别管线小头），换取宽行不切段、常驻仅 1 份模型、逻辑更简。
     */
    val REC_WIDTHS: List<Int> = listOf(640)

    const val DET_MODEL = OcrModelStore.DET_MODEL
    const val DICT = OcrModelStore.DICT

    fun detAssetPath(): String = "$ASSET_DIR/$DET_MODEL"

    fun recAssetPath(width: Int): String = "$ASSET_DIR/${OcrModelStore.recFileName(width)}"

    fun dictAssetPath(): String = "$ASSET_DIR/$DICT"

    /** 资产是否可读（首次探测缓存）；引擎用它代替旧 filesDir 的 `isReady`。 */
    @Volatile
    private var present: Boolean? = null

    fun isPresent(context: Context): Boolean =
        present ?: runCatching {
            context.assets.list(ASSET_DIR)?.any { it == DET_MODEL || OcrModelStore.isRecFile(it) } == true
        }.getOrDefault(false).also { present = it }
}
