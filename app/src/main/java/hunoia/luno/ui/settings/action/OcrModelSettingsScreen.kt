package hunoia.luno.ui.settings.action

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.domain.AppGraph
import hunoia.luno.domain.ocr.OcrModelState
import hunoia.luno.domain.ocr.PpModelUiState
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * OCR 模型管理页：PP-OCRv6 GPU 包的导入 / 删除与状态。
 *
 * 只调 `AppGraph.ocrModels`；不 import `capability.ocr` 实现类。
 */
@Composable
fun OcrModelSettingsScreen(onBack: () -> Unit) {
    val state by AppGraph.ocrModels.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { AppGraph.ocrModels.importPp(uri) }
    }

    LunoScreen(title = stringResource(R.string.ocr_model_title), onBack = onBack) {
        LunoGroup(title = stringResource(R.string.ocr_model_env_group)) {
            LunoSettingsRow(
                title = state.socModel.ifBlank { stringResource(R.string.ocr_model_soc) },
                subtitle = stringResource(
                    if (state.gpuSupported) {
                        R.string.ocr_model_gpu_supported
                    } else {
                        R.string.ocr_model_gpu_unsupported
                    },
                ),
                shape = segmentedShape(0, 1),
            )
        }
        LunoGroup(
            title = stringResource(R.string.ocr_model_pp_group),
            badgeText = ppBadge(state.pp),
            badgeError = state.pp.state is OcrModelState.Failed,
        ) {
            LunoSettingsRow(
                title = stringResource(R.string.ocr_model_pp_state),
                subtitle = ppDetail(state.pp),
                subtitleColor = if (state.pp.state is OcrModelState.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                shape = segmentedShape(0, 4),
            )
            LunoSettingsRow(
                title = stringResource(R.string.ocr_model_pp_import),
                subtitle = stringResource(R.string.ocr_model_pp_import_hint),
                onClick = {
                    importLauncher.launch(
                        arrayOf("application/zip", "application/octet-stream"),
                    )
                },
                enabled = !state.importing,
                shape = segmentedShape(1, 4),
            )
            LunoSettingsRow(
                title = stringResource(R.string.ocr_model_pp_delete),
                onClick = { confirmDelete = true },
                enabled = state.pp.state is OcrModelState.Ready && !state.importing,
                shape = segmentedShape(2, 4),
            )
            LunoSettingsRow(
                title = stringResource(R.string.ocr_model_refresh),
                onClick = { scope.launch { AppGraph.ocrModels.refresh() } },
                shape = segmentedShape(3, 4),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.ocr_model_pp_delete)) },
            text = { Text(stringResource(R.string.ocr_model_pp_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        scope.launch { AppGraph.ocrModels.deletePp() }
                    },
                ) {
                    Text(stringResource(R.string.ocr_model_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.ocr_model_cancel))
                }
            },
        )
    }
}

@Composable
private fun ppBadge(pp: PpModelUiState): String? = when (pp.state) {
    is OcrModelState.Ready -> stringResource(R.string.ocr_model_pp_ready)
    is OcrModelState.Verifying -> stringResource(R.string.ocr_model_pp_verifying)
    is OcrModelState.Failed -> stringResource(R.string.ocr_model_pp_error)
    else -> null
}

@Composable
private fun ppDetail(pp: PpModelUiState): String {
    val state = pp.state
    val base = when (state) {
        is OcrModelState.Ready -> stringResource(R.string.ocr_model_pp_ready)
        is OcrModelState.Verifying -> stringResource(R.string.ocr_model_pp_verifying)
        is OcrModelState.Failed -> stringResource(R.string.ocr_model_pp_failed, state.message)
        else -> stringResource(R.string.ocr_model_pp_missing)
    }
    val meta = buildList {
        pp.source?.takeIf { it.isNotBlank() }?.let { add(it) }
        pp.recWidths.takeIf { it.isNotEmpty() }?.let {
            add(stringResource(R.string.ocr_model_pp_rec, it.joinToString("/")))
        }
        pp.sizeBytes?.let { add(formatSize(it)) }
        pp.importedAt?.takeIf { it > 0 }?.let {
            add(
                stringResource(
                    R.string.ocr_model_pp_imported_at,
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it)),
                ),
            )
        }
    }
    return if (meta.isEmpty()) base else stringResource(
        R.string.ocr_model_pp_detail,
        base,
        meta.joinToString(" · "),
    )
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
