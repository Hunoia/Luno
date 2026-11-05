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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.ScreenPickGesture
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.facade.OcrModelState
import hunoia.luno.facade.PpModelUiState
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.ui.facade.LocalFacades
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 屏幕识别设置页：全屏长按识屏、幽灵卡参数与 OCR 模型管理。
 *
 * 动作设置根页只留一个入口行；参数多，单独一页避免根页变成参数堆。
 * OCR 模型（运行环境 + PP-OCRv6 包导入/删除）内嵌在本页，不再单独页面。
 */
@Composable
fun ScreenPickSettingsScreen(onBack: () -> Unit) {
    val facades = LocalFacades.current
    val scope = rememberCoroutineScope()
    val ocrModels by facades.ocrModels.state.collectAsStateWithLifecycle()
    val initialSettings by GestureConfig.initial.collectAsStateWithLifecycle(
        initialValue = InitialSettings(),
    )
    val advancedSettings by TuneConfig.advanced.collectAsStateWithLifecycle(
        initialValue = AdvancedSettings(),
    )
    val screenPick = initialSettings.screenPickGesture
    val commit: (ScreenPickGesture) -> Unit = { next ->
        scope.launch { GestureConfig.updateInitial { it.copy(screenPickGesture = next) } }
    }
    var confirmDelete by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { facades.ocrModels.importPp(uri) }
    }

    LunoScreen(title = stringResource(R.string.screen_pick_settings_title), onBack = onBack) {
        LunoGroup(title = stringResource(R.string.screen_pick_settings_group)) {
            LunoSwitchRow(
                title = stringResource(R.string.screen_pick_global_long_press),
                subtitle = stringResource(R.string.screen_pick_global_long_press_hint),
                checked = screenPick.enabled,
                onCheckedChange = { checked -> commit(screenPick.copy(enabled = checked)) },
                shape = segmentedShape(0, 5),
            )
            ScreenPickSlider(
                value = screenPick.longPressDelayMs.toFloat(),
                range = 200f..1_000f,
                text = stringResource(R.string.screen_pick_long_press_delay),
                display = { stringResource(R.string.screen_pick_long_press_delay_value, it.roundToInt()) },
                shape = segmentedShape(1, 5),
                onCommit = { commit(screenPick.copy(longPressDelayMs = it.roundToInt())) },
            )
            ScreenPickSlider(
                value = screenPick.dragStartTimeoutMs.toFloat(),
                range = 0f..5_000f,
                text = stringResource(R.string.screen_pick_drag_timeout),
                display = { ms ->
                    if (ms.roundToInt() == 0) stringResource(R.string.screen_pick_drag_timeout_off)
                    else stringResource(R.string.screen_pick_drag_timeout_value, ms.roundToInt())
                },
                shape = segmentedShape(2, 5),
                onCommit = { commit(screenPick.copy(dragStartTimeoutMs = it.roundToInt())) },
            )
            ScreenPickSlider(
                value = screenPick.slopDp,
                range = 2f..40f,
                text = stringResource(R.string.screen_pick_slop),
                display = { stringResource(R.string.screen_pick_slop_value, it.roundToInt()) },
                shape = segmentedShape(3, 5),
                onCommit = { commit(screenPick.copy(slopDp = it.roundToInt().toFloat())) },
            )
            ScreenPickSlider(
                value = screenPick.triggerCooldownMs.toFloat(),
                range = 0f..3_000f,
                text = stringResource(R.string.screen_pick_trigger_interval),
                display = { stringResource(R.string.screen_pick_trigger_interval_value, it.roundToInt()) },
                shape = segmentedShape(4, 5),
                onCommit = { commit(screenPick.copy(triggerCooldownMs = it.roundToInt())) },
            )
        }
        LunoGroup(title = stringResource(R.string.screen_pick_settings_image_group)) {
            LunoSwitchRow(
                title = stringResource(R.string.screen_pick_save_to_gallery),
                subtitle = stringResource(R.string.screen_pick_save_to_gallery_hint),
                checked = advancedSettings.screenPickSaveToGallery,
                onCheckedChange = { checked ->
                    scope.launch {
                        TuneConfig.updateAdvanced { it.copy(screenPickSaveToGallery = checked) }
                    }
                },
                shape = segmentedShape(0, 1),
            )
        }
        // ===== OCR 模型管理（原独立页面，已内嵌） =====
        LunoGroup(title = stringResource(R.string.ocr_model_env_group)) {
            LunoSettingsRow(
                title = ocrModels.socModel.ifBlank { stringResource(R.string.ocr_model_soc) },
                subtitle = stringResource(
                    if (ocrModels.gpuSupported) {
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
            badgeText = ppBadge(ocrModels.pp),
            badgeError = ocrModels.pp.state is OcrModelState.Failed,
        ) {
            LunoSettingsRow(
                title = stringResource(R.string.ocr_model_pp_state),
                subtitle = ppDetail(ocrModels.pp),
                subtitleColor = if (ocrModels.pp.state is OcrModelState.Failed) {
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
                enabled = !ocrModels.importing,
                shape = segmentedShape(1, 4),
            )
            LunoSettingsRow(
                title = stringResource(R.string.ocr_model_pp_delete),
                onClick = { confirmDelete = true },
                enabled = ocrModels.pp.state is OcrModelState.Ready && !ocrModels.importing,
                shape = segmentedShape(2, 4),
            )
            LunoSettingsRow(
                title = stringResource(R.string.ocr_model_refresh),
                onClick = { scope.launch { facades.ocrModels.refresh() } },
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
                        scope.launch { facades.ocrModels.deletePp() }
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
private fun ScreenPickSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    text: String,
    display: @Composable (Float) -> String,
    shape: Shape,
    onCommit: (Float) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value) }
    LunoTextSlider(
        value = draft,
        onValueChange = { draft = it },
        onValueChangeFinished = { onCommit(draft) },
        text = text,
        valueDisplay = { display(it) },
        valueRange = range,
        shape = shape,
    )
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
