package hunoia.luno.domain.ocr

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import hunoia.luno.capability.ocr.OcrModelInfo
import hunoia.luno.capability.ocr.OcrModelPack
import hunoia.luno.capability.ocr.OcrModelStatus
import hunoia.luno.capability.ocr.OcrModelStore
import hunoia.luno.core.LOG_TAG
import hunoia.luno.service.DispatchClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * OCR 模型管理实现（主进程）。
 *
 * 只管理 PP-OCRv6 的 GPU 包：扫描 `filesDir/ocr_models/ppocrv6_gpu`，
 * 只做「展示 / 导入 / 删除」，不联网。
 * 导入或删除后通过 [DispatchClient] 让 `:service` 丢弃旧引擎并重新预热。
 */
object OcrModelCenter : OcrModelFacade {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(
        OcrModelUiState(socModel = socModelName()),
    )
    override val state: StateFlow<OcrModelUiState> = _state.asStateFlow()

    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        scope.launch { refresh() }
    }

    override suspend fun refresh() {
        val context = appContext ?: return
        _state.update { current ->
            current.copy(
                socModel = socModelName(),
                pp = if (current.importing) current.pp else ppState(context),
            )
        }
    }

    override suspend fun importPp(uri: Uri) {
        val context = appContext ?: return
        if (_state.value.importing) return
        _state.update { it.copy(importing = true, pp = it.pp.copy(state = OcrModelState.Verifying)) }
        OcrModelPack.importZip(context, uri)
            .onSuccess { info ->
                Log.i(LOG_TAG, "ocr: PP 模型导入完成 ${info.fileCount} 个文件，${info.sizeBytes} 字节")
                _state.update { it.copy(importing = false, pp = ppStateOf(info)) }
                DispatchClient.refreshOcrEngine()
            }
            .onFailure { failure ->
                Log.w(LOG_TAG, "ocr: PP 模型导入失败：${failure.message}")
                _state.update {
                    it.copy(
                        importing = false,
                        pp = it.pp.copy(
                            state = OcrModelState.Failed(failure.message ?: "导入失败"),
                        ),
                    )
                }
            }
    }

    override suspend fun deletePp() {
        val context = appContext ?: return
        OcrModelStore.delete(context)
        _state.update { it.copy(pp = PpModelUiState(state = OcrModelState.NotInstalled)) }
        DispatchClient.refreshOcrEngine()
    }

    private fun ppState(context: Context): PpModelUiState =
        when (val status = OcrModelStore.status(context)) {
            is OcrModelStatus.Missing -> PpModelUiState(state = OcrModelState.NotInstalled)
            is OcrModelStatus.Invalid ->
                PpModelUiState(state = OcrModelState.Failed(status.reason))
            is OcrModelStatus.Ready -> ppStateOf(status.info)
        }

    private fun ppStateOf(info: OcrModelInfo): PpModelUiState =
        PpModelUiState(
            state = OcrModelState.Ready,
            source = info.manifest.runtime?.ifBlank { null },
            sizeBytes = info.sizeBytes,
            importedAt = info.importedAt,
            soc = info.manifest.soc,
        )

    private fun socModelName(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.ifBlank { Build.BOARD.orEmpty() }
        } else {
            Build.BOARD.orEmpty()
        }
}
