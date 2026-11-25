package hunoia.luno.domain.ocr

import android.content.Context
import android.os.Build
import hunoia.luno.capability.ocr.BundledOcrModel
import hunoia.luno.facade.OcrModelFacade
import hunoia.luno.facade.OcrModelState
import hunoia.luno.facade.OcrModelUiState
import hunoia.luno.facade.PpModelUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * OCR 模型展示实现（主进程）。
 *
 * 模型随 APK 打包（资产直载，见 [BundledOcrModel]）；这里只暴露固定信息与 GPU 运行环境，
 * 不再管理导入 / 删除 / 目录扫描，也不触发 `:service` 重载。
 */
object OcrModelCenter : OcrModelFacade {

    private val _state = MutableStateFlow(
        OcrModelUiState(socModel = socModelName()),
    )
    override val state: StateFlow<OcrModelUiState> = _state.asStateFlow()

    /** 应用启动时调用一次：从资产探测内置包大小，填充展示态。 */
    fun init(context: Context) {
        if (_state.value.pp.sizeBytes != null) return
        _state.value = OcrModelUiState(
            gpuSupported = true,
            socModel = socModelName(),
            pp = PpModelUiState(
                state = OcrModelState.Ready,
                sizeBytes = bundledSize(context),
                recWidths = BundledOcrModel.REC_WIDTHS,
            ),
        )
    }

    private fun socModelName(): String =
        Build.SOC_MODEL.ifBlank { Build.BOARD.orEmpty() }

    /** det + 单档 rec 资产字节数（tflite 未压缩可 openFd；字典已压缩，跳过）。 */
    private fun bundledSize(context: Context): Long? = runCatching {
        val assets = context.assets
        assets.openFd(BundledOcrModel.detAssetPath()).length +
            assets.openFd(
                BundledOcrModel.recAssetPath(BundledOcrModel.REC_WIDTHS.first()),
            ).length
    }.getOrNull()
}
