package hunoia.luno.floatball.settings.alert

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import hunoia.luno.clipboard.ClipboardCenter
import hunoia.luno.config.model.AlertLimits
import hunoia.luno.config.model.AlertPalette
import hunoia.luno.config.model.ColorSource
import hunoia.luno.config.model.ColorSpec
import hunoia.luno.config.model.removePalette
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.PressIconButton
import hunoia.luno.ui.component.row.LunoDropdownRow
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoGroupItem
import hunoia.luno.ui.component.row.LunoInputRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.SegmentedRows
import hunoia.luno.ui.component.row.segmentedRows
import hunoia.luno.ui.component.sheet.ColorPickerBottomSheet
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.component.sheet.toColorSpec
import hunoia.luno.ui.component.sheet.toInitialSelection
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.PaletteStripHeight
import kotlin.math.roundToInt
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
 * 到达提示·调色板编辑页（自定义）：名称、彩虹开关、可视化色块编辑（顶部整条预览 +
 * 长按拖拽排序 / 点色块取色 / 移除 / 锁定 / 生成和谐色；[AlertLimits.MaxPaletteColors] 色，
 * 至少保留 1 色）、彩虹参数（色相偏移 / 方向）与删除调色板。
 * 删除会清空引用它的分类的 `paletteId`；彩虹模式忽略色表，色表整块隐藏。
 *
 * 改动走本地即显状态（[rememberAlertEditor]，与动效设置页同口径），写盘异步回推不稳，
 * 不能只靠配置流刷新。名称是本地草稿 + 防抖落盘（失焦立即写）；「页面在组合期直接调
 * onBack」属于副作用，这里等配置流给出真值后再判定（见 [PaletteAppearWaitMs]）。
 */
@Composable
fun AlertPaletteEditScreen(
    paletteId: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // 本地即显 + 异步落盘：写盘不保证立刻回推给活跃订阅者，颜色 / 彩虹开关必须当场可见
    // （与动效设置页同一口径，见 rememberAlertEditor）。
    val editor = rememberAlertEditor()
    val palette = editor.library.palettes.firstOrNull { it.id == paletteId }

    // 不在组合期导航：先等配置流（新建调色板的写盘回推窗口），仍没有才回管理页；
    // 等到真值后把本地状态同步过来（管理页已先 syncActionSnapshot，正常路径不会走到这）。
    LaunchedEffect(paletteId) {
        if (palette == null) {
            val appeared = withTimeoutOrNull(PaletteAppearWaitMs) {
                TuneConfig.action.first { current ->
                    current.floatBall.notificationCapsule.alertLibrary.palettes.any { it.id == paletteId }
                }
            }
            if (appeared == null) onBack() else editor.refresh()
        }
    }
    if (palette == null) return

    var editingColorIndex by remember { mutableIntStateOf(-1) }
    // 「添加颜色」先取色再入表：取色那一刻还没有新下标，不能拿旧色表算索引。
    var addingColor by remember { mutableStateOf(false) }
    var nameDraft by remember(paletteId) { mutableStateOf(palette.name) }
    // 生成时不被覆盖的下标（生成期 UI 态，不落盘）；彩虹模式无意义，随 id 重置。
    var lockedColors by remember(paletteId) { mutableStateOf(setOf<Int>()) }
    // 连点 / 返回与删除并发时只允许离开一次（第二次 pop 会弹掉管理页）。
    var leaving by remember { mutableStateOf(false) }
    // 顶栏删除按钮的二次确认弹窗（删除不可逆，且会连带影响引用它的分类）。
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun updatePalette(transform: (AlertPalette) -> AlertPalette) {
        editor.updateLibrary { current ->
            current.copy(
                palettes = current.palettes.map { p ->
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
            persistLibraryNow { current ->
                current.copy(
                    palettes = current.palettes.map {
                        if (it.id == paletteId) it.copy(name = name) else it
                    },
                )
            }
            TuneConfig.syncActionSnapshot()
            onBack()
        }
    }

    /** 删除确认后的落盘 + 返回：先写完再 pop（页面 scope 销毁会吞掉最后一次写盘）。 */
    fun confirmDelete() {
        if (leaving) return
        leaving = true
        scope.launch {
            persistLibraryNow { it.removePalette(paletteId) }
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

    hunoia.luno.ui.component.page.LunoPage(title = stringResource(R.string.float_ball_capsule_alert_palette_edit),
onBack = { commitNameAndBack() },
actions = {
            PressIconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(
                        R.string.float_ball_capsule_alert_palette_delete,
                    ),
                )
            }
        },
dialogs = {
            if (showDeleteConfirm) {
                ConfirmDialog(
                    title = stringResource(
                        R.string.float_ball_capsule_alert_palette_delete_confirm_title,
                        palette.name,
                    ),
                    text = stringResource(
                        R.string.float_ball_capsule_alert_palette_delete_subtitle,
                    ),
                    confirmLabel = stringResource(
                        R.string.float_ball_capsule_alert_palette_delete_confirm,
                    ),
                    destructive = true,
                    onDismissRequest = { showDeleteConfirm = false },
                    onConfirm = {
                        showDeleteConfirm = false
                        confirmDelete()
                    },
                )
            }
        },
sheets = {
            if (addingColor || editingColorIndex >= 0) {
                val scheme = MaterialTheme.colorScheme
                // 「添加」还没有新下标：用默认色起手，选定后再入表。
                val pickerArgb = if (addingColor) {
                    DefaultNewPaletteColor
                } else {
                    palette.colors.getOrNull(editingColorIndex) ?: DefaultNewPaletteColor
                }
                ColorPickerBottomSheet(
                    onDismissRequest = {
                        addingColor = false
                        editingColorIndex = -1
                    },
                    onCopyColor = { hex -> ClipboardCenter.writeTextImmediate(hex, "Luno color") },
                    initialSelection = ColorSpec(
                        source = ColorSource.Custom,
                        customColor = pickerArgb,
                    ).toInitialSelection(scheme = scheme, primaryFallback = false),
                    onColorSelected = { selection ->
                        val argb = selection.toColorSpec(scheme).customColor
                        if (addingColor) {
                            updatePalette { p ->
                                p.copy(colors = (p.colors + argb).take(AlertLimits.MaxPaletteColors))
                            }
                        } else {
                            updatePalette { p ->
                                val index = editingColorIndex
                                    .coerceIn(0, p.colors.lastIndex.coerceAtLeast(0))
                                if (p.colors.isEmpty()) {
                                    p
                                } else {
                                    p.copy(colors = p.colors.toMutableList().also { it[index] = argb })
                                }
                            }
                        }
                        addingColor = false
                        editingColorIndex = -1
                    },
                )
            }
        }) {
    list() {
        lazy {

        item(key = "basic") {
            LunoGroup {
                val rows = segmentedRows {
                    item { shape ->
                        LunoInputRow(
                            label = stringResource(R.string.float_ball_capsule_alert_palette_name),
                            value = nameDraft,
                            onValueChange = { nameDraft = it },
                            placeholder = stringResource(
                                R.string.float_ball_capsule_alert_palette_default_name,
                            ),
                            shape = shape,
                            modifier = Modifier.onFocusChanged {
                                if (!it.isFocused) commitName()
                            },
                        )
                    }
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
                SegmentedRows(rows)
            }
        }
        // 彩虹模式忽略色表：整块隐藏（无效项不显示）；色表可删到只剩 1 色。
        if (!palette.rainbow) {
            item(key = "colors") {
                LunoGroup(
                    title = stringResource(R.string.float_ball_capsule_alert_palette_colors),
                    subtitle = stringResource(R.string.float_ball_capsule_alert_palette_color_limit),
                ) {
                    // 全锁时没有可用的基色，整行禁用（副标题直接说明原因）。
                    val allLocked = lockedColors.size >= palette.colors.size
                    // 四个算法名先取好：stringResource 不能在 map 等非组合 lambda 里调。
                    val generateOptions = listOf(
                        stringResource(R.string.float_ball_capsule_alert_palette_gen_analogous),
                        stringResource(R.string.float_ball_capsule_alert_palette_gen_complementary),
                        stringResource(R.string.float_ball_capsule_alert_palette_gen_triadic),
                        stringResource(R.string.float_ball_capsule_alert_palette_gen_shades),
                    )
                    val rows = segmentedRows {
                        // 色块区整块包 LunoGroupItem：分段风格才有卡色与圆角，大卡风格才由
                        // 整卡托底并在上沿补分割线（组内非行内容不包会变成悬空内容）。
                        item { shape ->
                            LunoGroupItem(shape = shape) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(CardInnerSpacing),
                                    verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
                                ) {
                                    // 效果条：比列表里的缩略条更高，一眼看出整套配色走向。
                                    PaletteStrip(
                                        colors = palette.colors
                                            .take(AlertLimits.MaxPaletteColors)
                                            .map { Color(it) },
                                        modifier = Modifier.fillMaxWidth(),
                                        height = PaletteEditorStripHeight,
                                    )
                                    PaletteSwatchRow(
                                        colors = palette.colors.take(AlertLimits.MaxPaletteColors),
                                        locked = lockedColors,
                                        onToggleLock = { index ->
                                            lockedColors = if (index in lockedColors) lockedColors - index
                                            else lockedColors + index
                                        },
                                        onSelect = { editingColorIndex = it },
                                        onRemove = { index ->
                                            updatePalette { p ->
                                                p.copy(
                                                    colors = p.colors.filterIndexed { i, _ -> i != index },
                                                )
                                            }
                                            lockedColors = lockedColors.filterNot { it == index }
                                                .map { if (it > index) it - 1 else it }.toSet()
                                            if (editingColorIndex == index) {
                                                editingColorIndex = -1
                                            }
                                        },
                                        onMove = { from, to ->
                                            updatePalette { p ->
                                                val list = p.colors.toMutableList()
                                                if (from in list.indices && to in list.indices) {
                                                    val item = list.removeAt(from)
                                                    list.add(to, item)
                                                }
                                                p.copy(colors = list)
                                            }
                                            // 锁定下标跟随移动。
                                            lockedColors = lockedColors.map {
                                                when {
                                                    it == from -> to
                                                    from < to && it in (from + 1)..to -> it - 1
                                                    to < from && it in to until from -> it + 1
                                                    else -> it
                                                }
                                            }.toSet()
                                        },
                                        onAdd = {
                                            addingColor = true
                                            editingColorIndex = -1
                                        },
                                    )
                                    // 手势说明：拖拽 / 长按菜单都不显眼，第一次进来容易找不到。
                                    Text(
                                        text = stringResource(
                                            R.string.float_ball_capsule_alert_palette_colors_hint,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        // 生成配色：下拉菜单。原来是并排 4 个 chip，窄屏会被挤成省略号。
                        item { shape ->
                            LunoDropdownRow(
                                title = stringResource(R.string.float_ball_capsule_alert_palette_generate),
                                subtitle = stringResource(
                                    if (allLocked) {
                                        R.string.float_ball_capsule_alert_palette_generate_locked_all
                                    } else {
                                        R.string.float_ball_capsule_alert_palette_generate_subtitle
                                    },
                                ),
                                options = generateOptions,
                                selectedIndex = -1,
                                enabled = !allLocked,
                                shape = shape,
                                onSelect = { index ->
                                    val base = palette.colors.indices
                                        .firstOrNull { it !in lockedColors }
                                        ?.let { palette.colors.getOrNull(it) }
                                    if (base != null) {
                                        val next = generatePalette(
                                            baseArgb = base,
                                            algo = PaletteGenerateAlgo.entries[index],
                                            count = AlertLimits.MaxPaletteColors,
                                        )
                                        updatePalette { p ->
                                            p.copy(colors = p.colors.mapIndexed { i, c ->
                                                if (i in lockedColors) c else next.getOrNull(i) ?: c
                                            })
                                        }
                                    }
                                },
                            )
                        }
                    }
                    SegmentedRows(rows)
                }
            }
        } else {
            // 彩虹参数组：色相偏移 / 饱和度范围 / 亮度范围 / 方向。
            // 默认值 = 原固定色相环（全饱和 / 全亮度 / 无偏移），滑杆拖动预览、松手落盘。
            item(key = "rainbowParams") {
                LunoGroup(title = stringResource(R.string.float_ball_capsule_alert_palette_rainbow_params)) {
                    val rows = segmentedRows {
                        item { shape ->
                            AlertKnobSlider(
                                label = stringResource(R.string.float_ball_capsule_alert_palette_rainbow_offset),
                                value = palette.rainbowHueOffset,
                                range = 0..359,
                                display = { "${it.roundToInt()}°" },
                                shape = shape,
                                onCommit = { v ->
                                    updatePalette { p -> p.copy(rainbowHueOffset = v) }
                                },
                            )
                        }
                        item { shape ->
                            AlertKnobSlider(
                                label = stringResource(R.string.float_ball_capsule_alert_palette_rainbow_sat_min),
                                value = palette.rainbowSatMin,
                                range = 0..100,
                                display = { "${it.roundToInt()}%" },
                                shape = shape,
                                onCommit = { v ->
                                    updatePalette { p ->
                                        p.copy(rainbowSatMin = v.coerceAtMost(p.rainbowSatMax))
                                    }
                                },
                            )
                        }
                        item { shape ->
                            AlertKnobSlider(
                                label = stringResource(R.string.float_ball_capsule_alert_palette_rainbow_sat_max),
                                value = palette.rainbowSatMax,
                                range = 0..100,
                                display = { "${it.roundToInt()}%" },
                                shape = shape,
                                onCommit = { v ->
                                    updatePalette { p ->
                                        p.copy(rainbowSatMax = v.coerceAtLeast(p.rainbowSatMin))
                                    }
                                },
                            )
                        }
                        item { shape ->
                            AlertKnobSlider(
                                label = stringResource(R.string.float_ball_capsule_alert_palette_rainbow_val_min),
                                value = palette.rainbowValMin,
                                range = 0..100,
                                display = { "${it.roundToInt()}%" },
                                shape = shape,
                                onCommit = { v ->
                                    updatePalette { p ->
                                        p.copy(rainbowValMin = v.coerceAtMost(p.rainbowValMax))
                                    }
                                },
                            )
                        }
                        item { shape ->
                            AlertKnobSlider(
                                label = stringResource(R.string.float_ball_capsule_alert_palette_rainbow_val_max),
                                value = palette.rainbowValMax,
                                range = 0..100,
                                display = { "${it.roundToInt()}%" },
                                shape = shape,
                                onCommit = { v ->
                                    updatePalette { p ->
                                        p.copy(rainbowValMax = v.coerceAtLeast(p.rainbowValMin))
                                    }
                                },
                            )
                        }
                        item { shape ->
                            LunoSwitchRow(
                                title = stringResource(R.string.float_ball_capsule_alert_palette_rainbow_reverse),
                                checked = palette.rainbowReverse,
                                onCheckedChange = { checked -> updatePalette { it.copy(rainbowReverse = checked) } },
                                shape = shape,
                            )
                        }
                    }
                    // 当前效果预览：滑杆一动就跟着变，用户能看出「彩虹是什么样」。
                    // 与渲染端共用 rainbowRing，预览色 = 播放色。
                    PalettePreview(
                        palette = palette,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(all = CardInnerSpacing),
                    )
                    SegmentedRows(rows)
                }
            }
        }

        }
    }
}
}

/** 新增颜色的起始色（与「新建调色板」的默认色一致）。 */
private val DefaultNewPaletteColor = 0xFFE53935.toInt()

/** 编辑页效果条高度：比列表卡里的缩略条（[PaletteStripHeight]）更高，作为「整套效果」主角。 */
private val PaletteEditorStripHeight = PaletteStripHeight + CardInnerSpacing

