package hunoia.luno.ui.settings.action

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.GlobalGestureSettings
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.facade.OcrModelState
import hunoia.luno.facade.PpModelUiState
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.ui.facade.LocalFacades
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.component.sheet.AnchoredDialog
import hunoia.luno.ui.component.sheet.ConfirmDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import hunoia.luno.ui.component.PressTextButton

/**
 * 屏幕识别设置页：识屏结果参数（保存到相册）与 OCR 模型管理。
 *
 * 触发方式（长按后拖、双指、按住即触发等）与阈值已经迁到「识别区」编辑页；
 * 本页只留识屏自身参数与运行环境，避免同一参数在两处维护。
 * 识屏诊断：一键探测「识别区开关 → Shizuku → 触摸设备」，结果弹窗展示。
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
    val globalGesture by GestureConfig.globalGesture.collectAsStateWithLifecycle(
        initialValue = GlobalGestureSettings(),
    )
    var confirmDelete by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { facades.ocrModels.importPp(uri) }
    }
    // ===== 识屏诊断 =====
    // stringResource 只能在组合期取：先全部解析，onClick 协程与弹窗里只用现成字符串。
    // 结果在弹窗里展示：项数多、文案长，行副标题放不下。
    val sDiagRunning = stringResource(R.string.screen_pick_diag_running)
    val sDiagSwitchOk = stringResource(R.string.screen_pick_diag_switch_ok)
    val sDiagSwitchOff = stringResource(R.string.screen_pick_diag_switch_off)
    val sDiagShizukuOk = stringResource(R.string.screen_pick_diag_shizuku_ok)
    val sDiagShizukuFail = stringResource(R.string.screen_pick_diag_shizuku_fail)
    val sDiagDeviceOkPrefix = stringResource(R.string.screen_pick_diag_device_ok, "")
    val sDiagDeviceFail = stringResource(R.string.screen_pick_diag_device_fail)
    val sDiagNote = stringResource(R.string.screen_pick_diag_note)
    var diagRunning by remember { mutableStateOf(false) }
    var showDiagDialog by remember { mutableStateOf(false) }
    var diag by remember { mutableStateOf<ScreenPickDiag?>(null) }
    fun diagLines(d: ScreenPickDiag): List<String> = buildList {
        add(if (d.switchOk) "✓ $sDiagSwitchOk" else "✗ $sDiagSwitchOff")
        add(if (d.shizukuOk) "✓ $sDiagShizukuOk" else "✗ $sDiagShizukuFail")
        add(
            if (d.deviceName != null) "✓ ${sDiagDeviceOkPrefix}${d.deviceName}"
            else "✗ $sDiagDeviceFail",
        )
        if (d.allOk) add(sDiagNote)
    }

    LunoScreen(title = stringResource(R.string.screen_pick_settings_title), onBack = onBack) {
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
                shape = segmentedShape(0, 2),
            )
            LunoSwitchRow(
                title = stringResource(R.string.screen_pick_enable_qr),
                subtitle = stringResource(R.string.screen_pick_enable_qr_hint),
                checked = initialSettings.screenPick.enableQrRecognition,
                onCheckedChange = { checked ->
                    scope.launch {
                        GestureConfig.updateInitial {
                            it.copy(screenPick = it.screenPick.copy(enableQrRecognition = checked))
                        }
                    }
                },
                shape = segmentedShape(1, 2),
            )
        }
        // ===== 识屏诊断 =====
        LunoGroup(title = stringResource(R.string.screen_pick_diag_group)) {
            LunoSettingsRow(
                title = stringResource(R.string.screen_pick_diag_run),
                subtitle = stringResource(R.string.screen_pick_diag_hint),
                onClick = {
                    scope.launch {
                        diagRunning = true
                        showDiagDialog = true
                        diag = null
                        diag = runScreenPickDiagChecks(initialSettings, globalGesture)
                        diagRunning = false
                    }
                },
                shape = segmentedShape(0, 0),
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

    if (showDiagDialog) {
        AnchoredDialog(onDismiss = { if (!diagRunning) showDiagDialog = false }) { dismiss ->
            Text(
                text = stringResource(R.string.screen_pick_diag_group),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val d = diag
            Text(
                text = when {
                    diagRunning || d == null -> sDiagRunning
                    else -> diagLines(d).joinToString("\n")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                PressTextButton(
                    onClick = dismiss,
                    enabled = !diagRunning,
                ) {
                    Text(stringResource(hunoia.luno.design.R.string.close))
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.ocr_model_pp_delete),
            text = stringResource(R.string.ocr_model_pp_delete_confirm),
            confirmLabel = stringResource(R.string.ocr_model_delete),
            destructive = true,
            onDismissRequest = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                scope.launch { facades.ocrModels.deletePp() }
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

/** 识屏诊断结果：设置页负责把字段渲染成文案，这里只出事实。 */
private data class ScreenPickDiag(
    val switchOk: Boolean,
    val shizukuOk: Boolean,
    val deviceName: String?,
) {
    val allOk: Boolean get() = switchOk && shizukuOk && deviceName != null
}

/**
 * 全屏长按识屏诊断：逐项检查「开关 → Shizuku → 触摸设备探测」。
 *
 * 只能在设置页进程查静态链路；流式监听跑在无障碍服务进程（结果里提示重启服务）。
 * 设备探测与运行时扫描共用 [hunoia.luno.gesture.touchsource.TouchDeviceScanner]，
 * 避免两份 getevent 口径。
 */
private suspend fun runScreenPickDiagChecks(
    initial: InitialSettings,
    global: GlobalGestureSettings,
): ScreenPickDiag {
    hunoia.luno.shizuku.ShizukuManager.updateStatus()
    val shizukuReady = hunoia.luno.shizuku.ShizukuManager.currentStatus().isReady
    val zonesReady = global.zones.any { zone ->
        zone.enabled && zone.triggers.any { it.enabled && it.hasAnyAction }
    }
    val switchOk = initial.gestureEnabled && zonesReady
    val device = if (switchOk && shizukuReady) {
        hunoia.luno.gesture.touchsource.TouchDeviceScanner.scan().touchscreen
    } else {
        null
    }
    return ScreenPickDiag(
        switchOk = switchOk,
        shizukuOk = shizukuReady,
        deviceName = device?.name?.ifBlank { device.path },
    )
}
