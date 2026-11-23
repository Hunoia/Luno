package hunoia.luno.floatball.settings.alert

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.clipboard.ClipboardCenter
import hunoia.luno.config.model.AlertPalette
import hunoia.luno.config.model.ColorSource
import hunoia.luno.config.model.ColorSpec
import hunoia.luno.config.model.removePalette
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSegmentedGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.component.sheet.ColorPickerBottomSheet
import hunoia.luno.ui.component.sheet.toColorSpec
import hunoia.luno.ui.component.sheet.toInitialSelection
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.PaletteDotSize
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 名称落盘防抖（ms）：输入框每键落盘会把整份 actionSettings 重写一遍，中文 IME 组合期
 * 还会因为写盘回推与组合区刷新打架（丢字 / 光标跳）。停止输入后一次性写。
 */
private const val PaletteNameCommitDebounceMs = 400L

/**
 * 新建调色板 → 编辑页的等待窗口（ms）：写盘刚完成、配置流可能还没回推到本页，
 * 这里等一次真值再判定「已删除」，避免刚建好就被弹回管理页。
 */
private const val PaletteAppearWaitMs = 900L

/**
 * 到达提示·调色板编辑页（自定义）：名称、彩虹开关、逐色编辑（点色块取色）、
 * 增删颜色（1–5 色）与删除调色板。删除会清空引用它的分类的 `paletteId`。
 *
 * 名称走本地草稿 + 防抖落盘（失焦立即写）；「页面在组合期直接调 onBack」属于副作用，
 * 这里改为等配置流给出真值后再判定（见 [PaletteAppearWaitMs]）。
 */
@Composable
fun AlertPaletteEditScreen(
    paletteId: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val actionSettings by TuneConfig.action
        .collectAsStateWithLifecycle(initialValue = TuneConfig.latestAction())
    val alert = actionSettings.floatBall.notificationCapsule.alert
    val palette = alert.customPalettes.firstOrNull { it.id == paletteId }

    // 不在组合期导航：先等配置流（新建调色板的写盘回推窗口），仍没有才回管理页。
    LaunchedEffect(paletteId) {
        if (palette == null) {
            val appeared = withTimeoutOrNull(PaletteAppearWaitMs) {
                TuneConfig.action.first { current ->
                    current.floatBall.notificationCapsule.alert.customPalettes.any { it.id == paletteId }
                }
            }
            if (appeared == null) onBack()
        }
    }
    if (palette == null) return

    var editingColorIndex by remember { mutableStateOf(-1) }
    var nameDraft by remember(paletteId) { mutableStateOf(palette.name) }
    // 连点 / 返回与删除并发时只允许离开一次（第二次 pop 会弹掉管理页）。
    var leaving by remember { mutableStateOf(false) }

    fun updatePalette(transform: (AlertPalette) -> AlertPalette) {
        persistAlert(scope) {
            it.copy(
                customPalettes = it.customPalettes.map { p ->
                    if (p.id == paletteId) transform(p) else p
                },
            )
        }
    }

    /** 待落盘的名称：空 = 无改动（输入被清空时回落原名）。 */
    fun pendingName(): String? {
        val name = nameDraft.trim()
        return if (name.isNotEmpty() && name != palette.name) name else null
    }

    /** 名称落盘：空名视为放弃编辑（回落原名），否则去空白后写入。 */
    fun commitName() {
        val name = pendingName()
        if (name == null) {
            if (nameDraft.trim().isEmpty()) nameDraft = palette.name
            return
        }
        nameDraft = name
        updatePalette { it.copy(name = name) }
    }

    /** 返回前落盘：先写完再 pop，避免页面 scope 被销毁把最后一次改名吞掉。 */
    fun commitNameAndBack() {
        if (leaving) return
        leaving = true
        val name = pendingName()
        if (name == null) {
            onBack()
            return
        }
        scope.launch {
            persistAlertNow { current ->
                current.copy(
                    customPalettes = current.customPalettes.map {
                        if (it.id == paletteId) it.copy(name = name) else it
                    },
                )
            }
            TuneConfig.syncActionSnapshot()
            onBack()
        }
    }

    // 停止输入 [PaletteNameCommitDebounceMs] 后落盘；失焦（点其它行 / 返回）立即落盘。
    LaunchedEffect(nameDraft, palette.id) {
        if (pendingName() != null) {
            delay(PaletteNameCommitDebounceMs)
            commitName()
        }
    }

    // 系统返回也走「先写完再 pop」：防抖窗口内返回不会吞掉最后一次改名。
    BackHandler { commitNameAndBack() }

    LunoScreenLazy(
        title = stringResource(R.string.float_ball_capsule_alert_palette_manage),
        onBack = { commitNameAndBack() },
        sheets = {
            if (editingColorIndex >= 0) {
                val scheme = MaterialTheme.colorScheme
                val currentArgb = palette.colors.getOrNull(editingColorIndex) ?: 0xFFFFFFFF.toInt()
                ColorPickerBottomSheet(
                    onDismissRequest = { editingColorIndex = -1 },
                    onCopyColor = { hex -> ClipboardCenter.writeTextImmediate(hex, "Luno color") },
                    initialSelection = ColorSpec(
                        source = ColorSource.Custom,
                        customColor = currentArgb,
                    ).toInitialSelection(scheme = scheme, primaryFallback = false),
                    onColorSelected = { selection ->
                        val argb = selection.toColorSpec(scheme).customColor
                        updatePalette { p ->
                            val index = editingColorIndex.coerceIn(0, p.colors.lastIndex.coerceAtLeast(0))
                            if (p.colors.isEmpty()) {
                                p
                            } else {
                                p.copy(colors = p.colors.toMutableList().also { it[index] = argb })
                            }
                        }
                        editingColorIndex = -1
                    },
                )
            }
        },
    ) {
        item(key = "name") {
            OutlinedTextField(
                value = nameDraft,
                onValueChange = { nameDraft = it },
                label = { Text(stringResource(R.string.float_ball_capsule_alert_palette_name)) },
                singleLine = true,
                shape = LargeShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .onFocusChanged { if (!it.isFocused) commitName() },
            )
        }
        item(key = "flags") {
            LunoGroup {
                LunoSegmentedGroup {
                    item { shape ->
                        LunoSwitchRow(
                            title = stringResource(R.string.float_ball_capsule_alert_palette_rainbow),
                            subtitle = stringResource(R.string.float_ball_capsule_alert_palette_rainbow_subtitle),
                            checked = palette.rainbow,
                            onCheckedChange = { checked -> updatePalette { it.copy(rainbow = checked) } },
                            shape = shape,
                        )
                    }
                }
            }
        }
        item(key = "colors") {
            LunoGroup(
                title = stringResource(R.string.float_ball_capsule_alert_palette_colors),
                subtitle = stringResource(R.string.float_ball_capsule_alert_palette_color_limit),
            ) {
                LunoSegmentedGroup {
                    palette.colors.take(5).forEachIndexed { index, argb ->
                        item { shape ->
                            LunoSettingsRow(
                                title = stringResource(
                                    R.string.float_ball_capsule_alert_palette_color_count,
                                    index + 1,
                                ),
                                subtitle = String.format(Locale.ROOT, "#%08X", argb),
                                shape = shape,
                                leading = {
                                    Row(
                                        modifier = Modifier
                                            .size(PaletteDotSize)
                                            .border(1.dp, Color(argb).copy(alpha = 0.3f), CircleShape)
                                            .background(Color(argb), CircleShape),
                                    ) {}
                                },
                                onClick = { editingColorIndex = index },
                            )
                        }
                    }
                    if (palette.colors.size < 5) {
                        item { shape ->
                            LunoSettingsRow(
                                title = stringResource(R.string.float_ball_capsule_alert_palette_add_color),
                                icon = Icons.Default.Add,
                                shape = shape,
                                onClick = {
                                    updatePalette {
                                        it.copy(colors = it.colors + 0xFFE53935.toInt())
                                    }
                                },
                            )
                        }
                    }
                    item { shape ->
                        LunoSettingsRow(
                            title = stringResource(R.string.float_ball_capsule_alert_palette_delete),
                            subtitle = stringResource(R.string.float_ball_capsule_alert_palette_delete_subtitle),
                            icon = Icons.Default.Delete,
                            shape = shape,
                            onClick = {
                                // 先落盘再返回：页面级 scope 会随导航销毁，先 launch 再 pop 有丢写风险。
                                if (!leaving) {
                                    leaving = true
                                    scope.launch {
                                        persistAlertNow { it.removePalette(paletteId) }
                                        TuneConfig.syncActionSnapshot()
                                        onBack()
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
