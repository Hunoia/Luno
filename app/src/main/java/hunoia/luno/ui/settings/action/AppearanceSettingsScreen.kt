package hunoia.luno.ui.settings.action

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.config.model.BuiltinAlertPalettes
import hunoia.luno.config.model.ListStyle
import hunoia.luno.config.model.MotionSpeed
import hunoia.luno.config.model.MotionTuningSettings
import hunoia.luno.config.model.ShapeMode
import hunoia.luno.config.model.ShapeModeSetting
import hunoia.luno.config.model.ShapeSetting
import hunoia.luno.config.model.ShadowStrength
import hunoia.luno.config.model.ThemeMode
import hunoia.luno.config.model.ThemePaletteSetting
import hunoia.luno.config.model.UiAppearancePresets
import hunoia.luno.config.model.UiAppearanceSettings
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.ui.component.MotionTuningPreview
import hunoia.luno.ui.component.page.LunoPage
import hunoia.luno.ui.component.row.LunoDropdownRow
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.row.group
import hunoia.luno.ui.theme.ShapeRole
import hunoia.luno.ui.theme.UiAppearance
import hunoia.luno.ui.theme.radiusOptions
import hunoia.luno.ui.theme.toProfile
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 外观设置页：列表风格、动效档位、可调外观（圆角 / 玻璃 / 间距）。
 *
 * 独立页面而非设置根页的一段：动效档位和预览都是可操作项，
 * 放在根页会把「功能入口」列表拉长，也会让预览在滚动中反复重播。
 * 每项都用下拉菜单（[LunoDropdownRow]）：档位标签较长，分段按钮会挤成省略号。
 *
 * 可调外观按**组件族**分组（§2.5）：每组只覆盖同一高度族的容器，半径档位由该族最矮
 * 容器高推出；切换形态后越界会被明确改到该形态的最大合法档（不做静默 clamp）。
 */
@Composable
fun AppearanceSettingsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val advanced by TuneConfig.advanced
        .collectAsStateWithLifecycle(initialValue = TuneConfig.latestAdvanced())
    val ui = advanced.ui

    fun updateMotion(transform: (MotionTuningSettings) -> MotionTuningSettings) {
        scope.launch {
            TuneConfig.updateAdvanced { it.copy(motion = transform(it.motion)) }
        }
    }

    fun updateUi(transform: (UiAppearanceSettings) -> UiAppearanceSettings) {
        scope.launch {
            TuneConfig.updateAdvanced { it.copy(ui = transform(it.ui)) }
        }
    }

    LunoPage(
        title = stringResource(R.string.action_settings_appearance_group),
        onBack = onBack,
    ) {
        column {
            group(title = stringResource(R.string.appearance_group_list)) {
                dropdownRow(
                    title = stringResource(R.string.action_settings_list_style),
                    options = listOf(
                        stringResource(R.string.list_style_segmented),
                        stringResource(R.string.list_style_card_divided),
                    ),
                    selectedIndex = advanced.listStyle.ordinal,
                    onSelect = { index ->
                        scope.launch {
                            TuneConfig.updateAdvanced { it.copy(listStyle = ListStyle.entries[index]) }
                        }
                    },
                )
            }
            group(title = stringResource(R.string.action_settings_motion_group)) {
                dropdownRow(
                    title = stringResource(R.string.motion_panel_speed),
                    options = motionSpeedOptions(),
                    selectedIndex = advanced.motion.panel.ordinal,
                    onSelect = { index ->
                        updateMotion { it.copy(panel = MotionSpeed.entries[index]) }
                    },
                )
                dropdownRow(
                    title = stringResource(R.string.motion_overlay_speed),
                    options = motionSpeedOptions(),
                    selectedIndex = advanced.motion.overlay.ordinal,
                    onSelect = { index ->
                        updateMotion { it.copy(overlay = MotionSpeed.entries[index]) }
                    },
                )
                dropdownRow(
                    title = stringResource(R.string.motion_floating_speed),
                    options = motionSpeedOptions(),
                    selectedIndex = advanced.motion.floating.ordinal,
                    onSelect = { index ->
                        updateMotion { it.copy(floating = MotionSpeed.entries[index]) }
                    },
                )
            }
            block {
                MotionTuningPreview(profile = advanced.motion.toProfile())
            }
            block {
                ThemeSection(ui = ui, onChange = { next -> updateUi { next } })
            }
            block {
                ShapeSection(ui = ui, onChange = { next -> updateUi { next } })
            }
            block {
                GlassSection(ui = ui, onChange = { next -> updateUi { next } })
            }
            block {
                SpacingSection(ui = ui, onChange = { next -> updateUi { next } })
            }
            block {
                LunoGroup {
                    LunoSettingsRow(
                        title = stringResource(R.string.appearance_reset),
                        onClick = { updateUi { UiAppearanceSettings() } },
                        shape = segmentedShape(0, 1),
                    )
                }
            }
        }
    }
}

/**
 * 主题色：跟随系统动态取色，或选一套内置 / 自定义色板。
 *
 * 色板只存 id，定义仍在到达提示素材库；彩虹色板没有单一 seed，不进主题选择器。
 * 切换后整页即时换色，用户不需要额外预览按钮。
 */
@Composable
private fun ThemeSection(
    ui: UiAppearanceSettings,
    onChange: (UiAppearanceSettings) -> Unit,
) {
    val customPalettes = UiAppearance.customPalettes
    val palettes = remember(customPalettes) {
        (BuiltinAlertPalettes.all + customPalettes).filterNot { it.rainbow }
    }
    val currentId = ui.theme.paletteId
    val selectedIndex = palettes.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    val isPalette = ui.theme.mode == ThemeMode.Palette
    val rowCount = if (isPalette && palettes.isNotEmpty()) 3 else 1
    LunoGroup(title = stringResource(R.string.appearance_group_theme)) {
        LunoDropdownRow(
            title = stringResource(R.string.appearance_theme_source),
            options = listOf(
                stringResource(R.string.appearance_theme_system),
                stringResource(R.string.appearance_theme_palette),
            ),
            selectedIndex = ui.theme.mode.ordinal,
            onSelect = { index ->
                val mode = ThemeMode.entries[index]
                onChange(
                    ui.copy(
                        theme = ThemePaletteSetting(
                            mode = mode,
                            paletteId = if (mode == ThemeMode.Palette) {
                                currentId ?: palettes.firstOrNull()?.id
                            } else {
                                currentId
                            },
                        ),
                    ),
                )
            },
            shape = segmentedShape(0, rowCount),
        )
        if (isPalette && palettes.isNotEmpty()) {
            LunoDropdownRow(
                title = stringResource(R.string.appearance_theme_palette_label),
                options = palettes.map { it.name },
                selectedIndex = selectedIndex,
                onSelect = { index ->
                    onChange(
                        ui.copy(theme = ui.theme.copy(paletteId = palettes[index].id)),
                    )
                },
                shape = segmentedShape(1, rowCount),
            )
            LunoSettingsRow(
                title = stringResource(R.string.appearance_theme_custom_hint),
                shape = segmentedShape(2, rowCount),
            )
        }
    }
}

/** 圆角：6 个组件族 × （形态 + 半径）+ 胶囊形态。 */
@Composable
private fun ShapeSection(
    ui: UiAppearanceSettings,
    onChange: (UiAppearanceSettings) -> Unit,
) {
    val groups = listOf(
        Triple(R.string.appearance_corner_list, ShapeRole.List, ui.list),
        Triple(R.string.appearance_corner_menu, ShapeRole.Menu, ui.menu),
        Triple(R.string.appearance_corner_toolbar, ShapeRole.Toolbar, ui.toolbar),
        Triple(R.string.appearance_corner_compact, ShapeRole.Compact, ui.compact),
        Triple(R.string.appearance_corner_sheet, ShapeRole.Sheet, ui.sheet),
    )
    val rowCount = groups.size * 2 + 1
    LunoGroup(title = stringResource(R.string.appearance_group_shape)) {
        groups.forEachIndexed { index, (labelRes, role, setting) ->
            ShapeGroupRows(
                label = stringResource(labelRes),
                role = role,
                setting = setting,
                index = index * 2,
                count = rowCount,
                onChange = { next -> onChange(ui.withRole(role, next)) },
            )
        }
        LunoDropdownRow(
            title = stringResource(R.string.appearance_corner_capsule) + " · " +
                stringResource(R.string.appearance_shape_mode),
            options = shapeModeLabels(),
            selectedIndex = ui.capsule.mode.ordinal,
            onSelect = { index ->
                onChange(
                    ui.copy(capsule = ShapeModeSetting(ShapeMode.entries[index])),
                )
            },
            shape = segmentedShape(groups.size * 2, rowCount),
        )
    }
}

@Composable
private fun ShapeGroupRows(
    label: String,
    role: ShapeRole,
    setting: ShapeSetting,
    index: Int,
    count: Int,
    onChange: (ShapeSetting) -> Unit,
) {
    val options = role.radiusOptions(setting.mode)
    val effective = if (setting.radiusDp in options) setting.radiusDp else options.lastOrNull() ?: setting.radiusDp
    LunoDropdownRow(
        title = "$label · ${stringResource(R.string.appearance_shape_mode)}",
        options = shapeModeLabels(),
        selectedIndex = setting.mode.ordinal,
        onSelect = { i ->
            val mode = ShapeMode.entries[i]
            // 形态换了合法档位也换：越界就明确落到该形态的最大档（不是静默 clamp）。
            val nextOptions = role.radiusOptions(mode)
            val snapped = if (setting.radiusDp in nextOptions) setting.radiusDp else nextOptions.last()
            onChange(setting.copy(mode = mode, radiusDp = snapped))
        },
        shape = segmentedShape(index, count),
    )
    LunoDropdownRow(
        title = "$label · ${stringResource(R.string.appearance_radius)}",
        options = options.map { stringResource(R.string.appearance_radius_value, it) },
        selectedIndex = options.indexOf(effective).coerceAtLeast(0),
        onSelect = { i -> onChange(setting.copy(radiusDp = options[i])) },
        shape = segmentedShape(index + 1, count),
    )
}

/** 玻璃：顶栏（模糊 / 不透明度）+ 悬浮（模糊 / 不透明度 / 阴影）。 */
@Composable
private fun GlassSection(
    ui: UiAppearanceSettings,
    onChange: (UiAppearanceSettings) -> Unit,
) {
    group(title = stringResource(R.string.appearance_group_glass)) {
        // 模糊 0 档就是「关闭」：此时不透明度不再参与渲染（Backdrop 走实色分支），
        // 滑杆置灰并显示「关闭」，避免用户以为 40% 透明度还有磨砂。
        val blurOff = stringResource(R.string.appearance_glass_off)
        val topBar = ui.topBarGlass
        var blurDraft by remember(topBar.blurDp) { mutableFloatStateOf(topBar.blurDp.toFloat()) }
        sliderRow(
            value = blurDraft,
            onValueChange = { blurDraft = it },
            onValueChangeFinished = {
                onChange(ui.copy(topBarGlass = topBar.copy(blurDp = blurDraft.roundToInt())))
            },
            text = stringResource(R.string.appearance_top_bar_glass) + " · " +
                stringResource(R.string.appearance_glass_blur),
            valueDisplay = { v -> if (v <= 0f) blurOff else "${v.roundToInt()} dp" },
            valueRange = UiAppearancePresets.BlurDp.first.toFloat()..UiAppearancePresets.BlurDp.last.toFloat(),
            steps = 0,
        )
        var alphaDraft by remember(topBar.alpha) { mutableFloatStateOf(topBar.alpha) }
        sliderRow(
            value = alphaDraft,
            onValueChange = { alphaDraft = it },
            onValueChangeFinished = {
                onChange(ui.copy(topBarGlass = topBar.copy(alpha = alphaDraft)))
            },
            enabled = topBar.blurDp > 0,
            text = stringResource(R.string.appearance_top_bar_glass) + " · " +
                stringResource(R.string.appearance_glass_alpha),
            valueDisplay = { "${(it * 100).roundToInt()}%" },
            valueRange = 0.4f..0.9f,
            steps = 0,
        )

        val floating = ui.floatingGlass
        var floatingBlur by remember(floating.blurDp) {
            mutableFloatStateOf(floating.blurDp.toFloat())
        }
        sliderRow(
            value = floatingBlur,
            onValueChange = { floatingBlur = it },
            onValueChangeFinished = {
                onChange(ui.copy(floatingGlass = floating.copy(blurDp = floatingBlur.roundToInt())))
            },
            text = stringResource(R.string.appearance_floating_glass) + " · " +
                stringResource(R.string.appearance_glass_blur),
            valueDisplay = { v -> if (v <= 0f) blurOff else "${v.roundToInt()} dp" },
            valueRange = UiAppearancePresets.BlurDp.first.toFloat()..UiAppearancePresets.BlurDp.last.toFloat(),
            steps = 0,
        )
        var floatingAlpha by remember(floating.alpha) { mutableFloatStateOf(floating.alpha) }
        sliderRow(
            value = floatingAlpha,
            onValueChange = { floatingAlpha = it },
            onValueChangeFinished = {
                onChange(ui.copy(floatingGlass = floating.copy(alpha = floatingAlpha)))
            },
            enabled = floating.blurDp > 0,
            text = stringResource(R.string.appearance_floating_glass) + " · " +
                stringResource(R.string.appearance_glass_alpha),
            valueDisplay = { "${(it * 100).roundToInt()}%" },
            valueRange = 0.4f..0.9f,
            steps = 0,
        )
        dropdownRow(
            title = stringResource(R.string.appearance_floating_glass) + " · " +
                stringResource(R.string.appearance_glass_shadow),
            options = shadowLabels(),
            selectedIndex = floating.shadow.ordinal,
            onSelect = { index ->
                onChange(
                    ui.copy(floatingGlass = floating.copy(shadow = ShadowStrength.entries[index])),
                )
            },
        )
    }
}

/** 间距：页面边距 + 列表间距。 */
@Composable
private fun SpacingSection(
    ui: UiAppearanceSettings,
    onChange: (UiAppearanceSettings) -> Unit,
) {
    val spacing = ui.spacing
    group(title = stringResource(R.string.appearance_group_spacing)) {
        dropdownRow(
            title = stringResource(R.string.appearance_page_gutter),
            options = UiAppearancePresets.PageGutter.map {
                stringResource(R.string.appearance_radius_value, it)
            },
            selectedIndex = UiAppearancePresets.PageGutter
                .indexOf(spacing.pageGutterDp)
                .coerceAtLeast(0),
            onSelect = { index ->
                onChange(
                    ui.copy(
                        spacing = spacing.copy(
                            pageGutterDp = UiAppearancePresets.PageGutter[index],
                        ),
                    ),
                )
            },
        )
        dropdownRow(
            title = stringResource(R.string.appearance_list_spacing),
            options = UiAppearancePresets.ListSpacing.map {
                stringResource(R.string.appearance_radius_value, it)
            },
            selectedIndex = UiAppearancePresets.ListSpacing
                .indexOf(spacing.listSpacingDp)
                .coerceAtLeast(0),
            onSelect = { index ->
                onChange(
                    ui.copy(
                        spacing = spacing.copy(
                            listSpacingDp = UiAppearancePresets.ListSpacing[index],
                        ),
                    ),
                )
            },
        )
    }
}

/** 角色 → 写回对应分组（CompactMedium / SheetTop 跟随各自母组）。 */
private fun UiAppearanceSettings.withRole(role: ShapeRole, next: ShapeSetting): UiAppearanceSettings =
    when (role) {
        ShapeRole.List -> copy(list = next)
        ShapeRole.Menu, ShapeRole.Toolbar, ShapeRole.MenuInner -> copy(menu = next)
        ShapeRole.Compact, ShapeRole.CompactMedium -> copy(compact = next)
        ShapeRole.Sheet, ShapeRole.SheetTop -> copy(sheet = next)
        ShapeRole.Capsule -> this
    }

@Composable
private fun shapeModeLabels(): List<String> = listOf(
    stringResource(R.string.appearance_shape_mode_continuous),
    stringResource(R.string.appearance_shape_mode_standard),
)

@Composable
private fun shadowLabels(): List<String> = listOf(
    stringResource(R.string.appearance_shadow_none),
    stringResource(R.string.appearance_shadow_subtle),
    stringResource(R.string.appearance_shadow_strong),
)

@Composable
private fun motionSpeedOptions(): List<String> = listOf(
    stringResource(R.string.motion_speed_faster),
    stringResource(R.string.motion_speed_standard),
    stringResource(R.string.motion_speed_slower),
    stringResource(R.string.motion_speed_slowest),
)
