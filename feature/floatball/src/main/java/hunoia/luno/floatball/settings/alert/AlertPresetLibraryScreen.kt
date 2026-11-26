package hunoia.luno.floatball.settings.alert

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import hunoia.luno.config.model.AlertLayerCategory
import hunoia.luno.config.model.AlertLibraryExport
import hunoia.luno.config.model.AlertPreset
import hunoia.luno.core.domain.JsonSerializer
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.LunoActionBar
import hunoia.luno.ui.component.LunoActionBarItem
import hunoia.luno.ui.component.PressIconButton
import hunoia.luno.ui.component.menu.LunoDropdownMenu
import hunoia.luno.ui.component.menu.LunoDropdownMenuItem
import hunoia.luno.ui.component.page.LunoPage
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.SegmentedRows
import hunoia.luno.ui.component.row.segmentedRows
import hunoia.luno.ui.theme.MarkColorSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 到达提示·预设库（设置根目录的总管理入口）。
 *
 * - 我的预设：点行编辑；删除在预设编辑页的顶栏菜单里。
 * - 内置素材：只读，点一下复制成用户预设再编辑（快照口径）；36 个默认收起。
 * - 导入 / 导出收进顶栏菜单；新建走右下 FAB。
 */
@Composable
fun AlertPresetLibraryScreen(
    onBack: () -> Unit,
    onEditPreset: (String) -> Unit,
) {
    val editor = rememberAlertEditor()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    var busy by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val library = editor.library
    var categoryFilter by remember { mutableStateOf<AlertLayerCategory?>(null) }
    val mine = library.userPresets()
        .filter { categoryFilter == null || categoryFilter in it.categories() }
    val singles = mine.filter { it.resolvedKind == hunoia.luno.config.model.AlertPresetKind.Single }
    val combos = mine.filter { it.resolvedKind == hunoia.luno.config.model.AlertPresetKind.Combo }

    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    fun createPreset() {
        if (busy) return
        busy = true
        val name = uniquePresetName(
            resources.getString(R.string.float_ball_preset_default_name),
            library.presets.map { it.name },
        )
        val preset = newUserPreset(name)
        scope.launch {
            editor.commitLibrary { it.copy(presets = it.presets + preset) }
            busy = false
            onEditPreset(preset.id)
        }
    }

    fun copyAndEdit(preset: AlertPreset) {
        if (busy) return
        busy = true
        val copied = preset.asUserCopy().let { copy ->
            copy.copy(
                name = uniquePresetName(
                    resources.getString(
                        R.string.float_ball_preset_copy_name,
                        preset.displayNameString(context),
                    ),
                    library.presets.map { it.name },
                ),
            )
        }
        scope.launch {
            editor.commitLibrary { it.copy(presets = it.presets + copied) }
            busy = false
            onEditPreset(copied.id)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val payload = JsonSerializer.globalJson.encodeToString(
                library.toExport(System.currentTimeMillis()),
            )
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(payload.toByteArray(Charsets.UTF_8))
                    } ?: error("no stream")
                }.isSuccess
            }
            busy = false
            toast(
                resources.getString(
                    if (ok) R.string.float_ball_preset_export_done
                    else R.string.float_ball_preset_export_failed,
                ),
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val decoded = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bytes = stream.readBytes()
                        if (bytes.size > AlertLibraryImportMaxBytes) error("file too large")
                        JsonSerializer.globalJson.decodeFromString<AlertLibraryExport>(
                            bytes.toString(Charsets.UTF_8),
                        )
                    }
                }.getOrNull()
            }
            val merged = decoded?.let { library.mergeImport(it) }
            if (decoded == null || merged == null) {
                busy = false
                toast(resources.getString(R.string.float_ball_preset_import_failed))
                return@launch
            }
            val added = merged.presets.size - library.presets.size
            editor.commitLibrary { merged }
            busy = false
            toast(resources.getString(R.string.float_ball_preset_import_done, added))
        }
    }

    LunoPage(title = stringResource(R.string.float_ball_preset_library_title),
onBack = onBack,
bottomBar = { hazeState ->
            LunoActionBar(hazeState) {
                LunoActionBarItem(
                    onClick = ::createPreset,
                    icon = Icons.Default.Add,
                    contentDescription = stringResource(R.string.float_ball_preset_new),
                )
            }
        },
actions = {
            PressIconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.float_ball_alert_more),
                )
            }
            LunoDropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                LunoDropdownMenuItem(
                    text = stringResource(R.string.float_ball_preset_export),
                    leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                    enabled = !busy,
                    onClick = {
                        menuOpen = false
                        exportLauncher.launch(
                            "luno-alert-effects-${System.currentTimeMillis()}.json",
                        )
                    },
                )
                LunoDropdownMenuItem(
                    text = stringResource(R.string.float_ball_preset_import),
                    leadingIcon = { Icon(Icons.Default.FileOpen, contentDescription = null) },
                    enabled = !busy,
                    onClick = {
                        menuOpen = false
                        // 部分文件管理器把 .json 报成 octet-stream，只认 json 会选不到文件。
                        importLauncher.launch(
                            arrayOf(
                                "application/json",
                                "text/plain",
                                "application/octet-stream",
                            ),
                        )
                    },
                )
            }
        }) {
    list() {
        lazy {

        item(key = "filter") {
            AlertCategoryFilter(
                selected = categoryFilter,
                onSelect = { categoryFilter = it },
            )
        }
        item(key = "singles") {
            LunoGroup(
                title = stringResource(R.string.float_ball_preset_group_single),
                subtitle = stringResource(R.string.float_ball_preset_group_single_hint),
                badgeText = "${singles.size}",
            ) {
                val rows = segmentedRows {
                    if (singles.isEmpty()) {
                        item { shape ->
                            LunoSettingsRow(
                                title = stringResource(R.string.float_ball_preset_empty),
                                subtitle = stringResource(R.string.float_ball_preset_empty_hint),
                                shape = shape,
                            )
                        }
                    }
                    singles.forEach { preset ->
                        item { shape ->
                            LunoSettingsRow(
                                title = preset.displayName(),
                                subtitle = "${preset.layerSummary()}  ${preset.categorySummary()}",
                                shape = shape,
                                leading = { PresetSwatch(library.swatchFor(preset)) },
                                enabled = !busy,
                                onClick = { onEditPreset(preset.id) },
                            )
                        }
                    }
                }
                SegmentedRows(rows)
            }
        }
        if (combos.isNotEmpty()) {
            item(key = "combos") {
                LunoGroup(
                    title = stringResource(R.string.float_ball_preset_group_combo),
                    subtitle = stringResource(R.string.float_ball_preset_group_combo_hint),
                    badgeText = "${combos.size}",
                ) {
                    val rows = segmentedRows {
                        combos.forEach { preset ->
                            item { shape ->
                                LunoSettingsRow(
                                    title = preset.displayName(),
                                    subtitle = "${preset.layerSummary()}  ${preset.categorySummary()}",
                                    shape = shape,
                                    leading = { PresetSwatch(library.swatchFor(preset)) },
                                    enabled = !busy,
                                    onClick = { onEditPreset(preset.id) },
                                )
                            }
                        }
                    }
                    SegmentedRows(rows)
                }
            }
        }

        }
    }
}
}

/** 预设卡片左侧的小色块（调色板首色）；没有颜色时用主题主色。 */
@Composable
private fun PresetSwatch(colors: List<androidx.compose.ui.graphics.Color>) {
    Box(
        modifier = Modifier
            .size(MarkColorSize)
            .background(
                color = colors.firstOrNull() ?: MaterialTheme.colorScheme.primary,
                shape = CircleShape,
            ),
    )
}

/** 预设卡片色点：预设级调色板 → 层颜色 → 主题主色。 */
@Composable
private fun hunoia.luno.config.model.AlertLibrary.swatchFor(
    preset: AlertPreset,
): List<androidx.compose.ui.graphics.Color> =
    preset.swatchColors(this, MaterialTheme.colorScheme.primary)

/** 卡片副标题的类别串（不依赖 Composable 上下文时用）。 */
@Composable
internal fun AlertPreset.categorySummary(): String {
    val parts = categories().map { stringResource(it.titleRes()) }
    return parts.joinToString(" · ")
}