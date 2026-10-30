package hunoia.luno.domain.ocr

import android.net.Uri
import kotlinx.coroutines.flow.StateFlow

/**
 * OCR 模型管理门面（UI 唯一入口）。
 *
 * PP-OCRv5 只接受用户导入的 host AOT 包（SM8750 / HTP V79），不做应用内下载、不联网。
 * 实现留在 `:app` 的 [OcrModelCenter]；UI 通过 `AppGraph.ocrModels` 取得。
 */
interface OcrModelFacade {

    val state: StateFlow<OcrModelUiState>

    /** 重新扫描 PP 模型目录。 */
    suspend fun refresh()

    /** 导入 PP-OCRv5 AOT ZIP；成功后通知 `:service` 重新加载并预热引擎。 */
    suspend fun importPp(uri: Uri)

    /** 删除 PP 模型；`:service` 会丢弃旧引擎，识屏回到「未就绪」。 */
    suspend fun deletePp()
}

/** 单个模型来源的状态。 */
sealed interface OcrModelState {
    data object Unknown : OcrModelState
    data object NotInstalled : OcrModelState
    data object Verifying : OcrModelState
    data object Ready : OcrModelState
    data class Failed(val message: String) : OcrModelState
}

data class PpModelUiState(
    val state: OcrModelState = OcrModelState.Unknown,
    val source: String? = null,
    val sizeBytes: Long? = null,
    val importedAt: Long? = null,
    val soc: String? = null,
)

data class OcrModelUiState(
    val npuSupported: Boolean = false,
    val socModel: String = "",
    val pp: PpModelUiState = PpModelUiState(),
    val importing: Boolean = false,
) {
    /** AOT 模型是否已就绪（未就绪时识屏直接返回空结果，由界面提示导入）。 */
    val ppReady: Boolean get() = pp.state is OcrModelState.Ready
}
