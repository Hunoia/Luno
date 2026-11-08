package hunoia.luno.ui.settings.backup

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.config.backup.AutoBackupFile
import hunoia.luno.config.backup.AutoBackupRepository
import hunoia.luno.config.backup.RestorePrecheckResult
import hunoia.luno.domain.backup.AutoBackupCenter
import hunoia.luno.domain.backup.labelRes
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.facade.LocalFacades
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val statusFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/**
 * 定时自动备份设置页：开关、备份目录、执行状态、立即备份，以及从自动备份恢复。
 *
 * 全部经 [AutoBackupCenter]（设备本地状态 + 调度）与 `AppGraph.backup`（打包/恢复），
 * 页面本身不碰 DataStore 与 SAF 细节。
 */
@Composable
fun AutoBackupSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val facades = LocalFacades.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val settings by AutoBackupCenter.settings.collectAsStateWithLifecycle(initialValue = null)
    var treeName by remember { mutableStateOf<String?>(null) }
    var backups by remember { mutableStateOf<List<AutoBackupFile>>(emptyList()) }
    var listFailed by remember { mutableStateOf(false) }
    /** 选目录的意图：true = 用户正在开开关，选完直接进开启态。 */
    var pendingEnable by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<AutoBackupFile?>(null) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    val treePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) {
            pendingEnable = false
            return@rememberLauncherForActivityResult
        }
        val enable = pendingEnable
        pendingEnable = false
        scope.launch {
            runCatching { AutoBackupCenter.adoptTree(context, uri, enable = enable) }
                .onFailure {
                    Toast.makeText(context, R.string.auto_backup_fail_create, Toast.LENGTH_SHORT).show()
                }
        }
    }

    suspend fun refresh() {
        val uri = AutoBackupCenter.treeUri()
        // provider 不给显示名时退回 uri 尾段，别让「已选目录」显示成「未选择」。
        treeName = uri?.let { AutoBackupCenter.treeDisplayName(context, it) ?: it.lastPathSegment }
        val listed = runCatching { AutoBackupCenter.listBackups(context) }
        backups = listed.getOrDefault(emptyList())
        listFailed = uri != null && listed.isFailure
    }

    LaunchedEffect(settings?.treeUri) { refresh() }

    // 回到本页时重取：自动备份可能在后台跑完、目录可能被系统动过。
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) scope.launch { refresh() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val failure = settings?.lastError
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { AutoBackupRepository.Failure.valueOf(it) }.getOrNull() }
    val reasonText = failure?.let { stringResource(it.labelRes()) }.orEmpty()
    val current = settings
    val statusText = when {
        current == null -> ""
        failure != null && current.lastFailureAtMs >= current.lastSuccessAtMs ->
            stringResource(
                R.string.auto_backup_status_failed,
                formatStatusTime(current.lastFailureAtMs),
                reasonText,
            )
        current.lastSuccessAtMs > 0 ->
            stringResource(R.string.auto_backup_status_success, formatStatusTime(current.lastSuccessAtMs))
        else -> stringResource(R.string.auto_backup_status_never)
    }

    LunoScreen(title = stringResource(R.string.auto_backup), onBack = onBack) {
        LunoGroup(title = stringResource(R.string.auto_backup_switch)) {
            LunoSwitchRow(
                title = stringResource(R.string.auto_backup_switch),
                subtitle = stringResource(R.string.auto_backup_switch_hint),
                checked = current?.enabled == true,
                onCheckedChange = { want ->
                    if (!want) {
                        scope.launch { AutoBackupCenter.setEnabled(context, false) }
                        return@LunoSwitchRow
                    }
                    // 开开关前先确认目录授权还在；不在就走选择器，选完直接开启。
                    scope.launch {
                        if (AutoBackupCenter.hasStoredTree(context)) {
                            AutoBackupCenter.setEnabled(context, true)
                        } else {
                            pendingEnable = true
                            treePicker.launch(null)
                        }
                    }
                },
                shape = segmentedShape(0, 4),
            )
            LunoSettingsRow(
                title = stringResource(R.string.auto_backup_directory),
                subtitle = treeName
                    ?: stringResource(R.string.auto_backup_directory_unset),
                shape = segmentedShape(1, 4),
                onClick = {
                    pendingEnable = false
                    treePicker.launch(null)
                },
            )
            LunoSettingsRow(
                title = stringResource(R.string.auto_backup_status),
                subtitle = statusText,
                subtitleColor = if (failure != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                shape = segmentedShape(2, 4),
            )
            LunoSettingsRow(
                title = stringResource(R.string.auto_backup_run_now),
                subtitle = stringResource(R.string.auto_backup_run_now_hint),
                shape = segmentedShape(3, 4),
                onClick = {
                    if (current?.hasTree() != true) {
                        Toast.makeText(context, R.string.auto_backup_run_need_directory, Toast.LENGTH_SHORT).show()
                        return@LunoSettingsRow
                    }
                    AutoBackupCenter.runNow(context)
                    Toast.makeText(context, R.string.auto_backup_run_queued, Toast.LENGTH_SHORT).show()
                },
            )
        }

        LunoGroup(title = stringResource(R.string.auto_backup_restore)) {
            if (backups.isEmpty()) {
                LunoSettingsRow(
                    title = stringResource(
                        if (listFailed) R.string.auto_backup_restore_list_failed
                        else R.string.auto_backup_restore_empty,
                    ),
                    subtitle = stringResource(R.string.auto_backup_restore_hint),
                    shape = segmentedShape(0, 1),
                )
            } else {
                backups.forEachIndexed { index, file ->
                    LunoSettingsRow(
                        title = file.name,
                        subtitle = if (file.lastModifiedMs > 0) {
                            formatStatusTime(file.lastModifiedMs)
                        } else {
                            stringResource(R.string.auto_backup_restore_hint)
                        },
                        shape = segmentedShape(index, backups.size),
                        onClick = {
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    facades.backup.precheckRestore(context, file.uri)
                                }
                                when (result) {
                                    RestorePrecheckResult.Passed -> {
                                        pendingRestore = file
                                        showRestoreConfirm = true
                                    }
                                    is RestorePrecheckResult.Failed -> Toast.makeText(
                                        context,
                                        result.reason.stringRes,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    // 组合期捕获：确认时 onDismissRequest 已先清掉 pendingRestore。
    val restoreFile = pendingRestore
    if (showRestoreConfirm && restoreFile != null) {
        ConfirmDialog(
            title = stringResource(R.string.restore_confirm_title),
            text = stringResource(R.string.restore_confirm_desc),
            onDismissRequest = {
                showRestoreConfirm = false
                pendingRestore = null
            },
            onConfirm = {
                val uri: Uri = restoreFile.uri
                showRestoreConfirm = false
                pendingRestore = null
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) { facades.backup.restore(context, uri) }
                        Toast.makeText(context, R.string.restore_success, Toast.LENGTH_SHORT).show()
                        refresh()
                    }.onFailure {
                        Toast.makeText(context, R.string.restore_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }
}

private fun formatStatusTime(epochMs: Long): String =
    statusFormatter.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
