package hunoia.luno.facade

import kotlinx.coroutines.flow.StateFlow

/**
 * OCR 模型门面（UI 唯一入口）。
 *
 * PP-OCRv6 模型随 APK 打包（见 `:capability:ocr` 的 [BundledOcrModel]），资产直载、
 * 不联网；UI 只读固定信息与 GPU 运行环境。实现留在 `:app` 的 [OcrModelCenter]；
 * UI 通过 `AppGraph.ocrModels` 取得。
 */
interface OcrModelFacade {

    val state: StateFlow<OcrModelUiState>
}

/** 内置模型的运行态：恒就绪；资产缺失 / 加载失败才置 [Failed]。 */
sealed interface OcrModelState {
    data object Ready : OcrModelState
    data class Failed(val message: String) : OcrModelState
}

data class PpModelUiState(
    val state: OcrModelState = OcrModelState.Ready,
    val sizeBytes: Long? = null,
    /** rec 模型输入宽度档位（内置 640 单档）。 */
    val recWidths: List<Int> = emptyList(),
)

data class OcrModelUiState(
    val gpuSupported: Boolean = true,
    val socModel: String = "",
    val pp: PpModelUiState = PpModelUiState(),
)
