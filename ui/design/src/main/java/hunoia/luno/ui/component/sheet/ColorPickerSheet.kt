package hunoia.luno.ui.component.sheet

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.ImageDecoder
import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.os.Environment
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.palette.graphics.Palette
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.ThemeColorKey
import hunoia.luno.design.R
import hunoia.luno.ui.theme.CanvasStrokeWidth
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.ColorWheelSize
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.ListItemVerticalPadding
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.MinInteractiveSize
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SheetTopShape
import hunoia.luno.ui.theme.SubMinInteractiveSize
import hunoia.luno.ui.theme.cardSurfaceColor
import hunoia.luno.ui.theme.displayNameRes
import hunoia.luno.ui.theme.raisedSurfaceColor
import hunoia.luno.ui.theme.resolveColor
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import hunoia.luno.ui.component.PressIconButton
import hunoia.luno.ui.component.input.rememberControlledTextFieldState
import hunoia.luno.ui.component.pressClickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerBottomSheet(
    onDismissRequest: () -> Unit,
    onColorSelected: (ColorSelection) -> Unit,
    initialSelection: ColorSelection = ColorSelection.Custom(MaterialTheme.colorScheme.primary),
    /**
     * 复制色值：由调用方注入剪贴板写入（组件库不直接碰平台剪贴板 API，
     * 也不依赖数据层的单口）。为 null 时不展示复制按钮。
     */
    onCopyColor: ((String) -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val startColor = when (initialSelection) {
        is ColorSelection.Custom -> initialSelection.color
        is ColorSelection.Theme -> initialSelection.key.resolveColor(scheme)
    }

    var selectedTab by remember(initialSelection) {
        mutableStateOf(if (initialSelection is ColorSelection.Theme) 1 else 0)
    }
    var selectedThemeKey by remember(initialSelection) {
        mutableStateOf((initialSelection as? ColorSelection.Theme)?.key)
    }
    // 标准色板 / 提取色板里点选的色（ARGB）；切回自定义 tab 时清除，让圆盘/滑块重新主导。
    var swatchArgb by remember(initialSelection) { mutableStateOf<Int?>(null) }
    var alpha by remember(initialSelection) { mutableFloatStateOf(startColor.alpha) }
    var hue by remember(initialSelection) { mutableFloatStateOf(0f) }
    var saturation by remember(initialSelection) { mutableFloatStateOf(0f) }
    var value by remember(initialSelection) { mutableFloatStateOf(1f) }

    LaunchedEffect(initialSelection) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(startColor.toArgb(), hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
        alpha = startColor.alpha
    }

    val hsvColor by remember { derivedStateOf { Color.hsv(hue, saturation, value) } }
    val resolvedColor by remember(hsvColor, alpha) { derivedStateOf { hsvColor.copy(alpha = alpha) } }
    val previewColor = when (selectedTab) {
        1 -> selectedThemeKey?.resolveColor(scheme) ?: resolvedColor
        2, 3 -> swatchArgb?.let { Color(it) } ?: resolvedColor
        else -> resolvedColor
    }
    val previewHex = remember(previewColor) { formatHex(previewColor) }

    var hexField by remember(initialSelection) { mutableStateOf(formatHex(startColor)) }
    var hexFocused by remember { mutableStateOf(false) }

    // 圆盘/滑块/主题 tab 改变预览色时同步 hex；正在输入时不打断用户。
    LaunchedEffect(previewHex, hexFocused) {
        if (!hexFocused) hexField = previewHex
    }

    fun applyHex(digits: String) {
        if (digits.length != 6 && digits.length != 8) return
        val argb = runCatching { AndroidColor.parseColor("#$digits") }.getOrNull() ?: return
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(argb, hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
        alpha = AndroidColor.alpha(argb) / 255f
        selectedTab = 0
    }

    // 输入框走 TextFieldState（foundation 1.13 起 value/onValueChange 重载弃用）：归一化仍在
    // 这里做，回写由桥接负责（外部值 → state，state 变化 → onValueChange）。
    val hexState = rememberControlledTextFieldState(hexField) { raw ->
        val digits = raw.uppercase(Locale.ROOT)
            .filter { it.isDigit() || it in 'A'..'F' }
            .take(8)
        // 主题 tab 里编辑 hex：先把自定义态对齐到当前预览色再切回，
        // 保证自定义 tab 的圆盘/滑块与输入框内容一致。
        if (selectedTab == 1) {
            val base = previewColor
            val hsv = FloatArray(3)
            AndroidColor.colorToHSV(base.toArgb(), hsv)
            hue = hsv[0]
            saturation = hsv[1]
            value = hsv[2]
            alpha = base.alpha
            selectedTab = 0
        }
        hexField = "#$digits"
        applyHex(digits)
    }

    val context = LocalContext.current
    val copiedHint = stringResource(R.string.color_picker_copied)

    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
    val scrollState = rememberScrollState()

    LunoSheet(
        onDismissRequest = onDismissRequest,
        scrollState = OptimizedScrollState.Scroll(scrollState),
        // 颜色盘底部是通栏操作条（自带顶角与内容内边距），整层不再叠 gutter。
        horizontalPadding = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = PageGutter)
                    .padding(top = PageGutter, bottom = ListItemVerticalPadding),
                verticalArrangement = Arrangement.spacedBy(ListSpacing),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ListSpacing),
                ) {
                    Box(
                        modifier = Modifier
                            .size(MinInteractiveSize)
                            .clip(CircleShape)
                            .background(color = previewColor, shape = CircleShape)
                            .border(
                                width = CanvasStrokeWidth,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape,
                            ),
                    )
                    BasicTextField(
                        state = hexState,
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { hexFocused = it.isFocused },
                        lineLimits = TextFieldLineLimits.SingleLine,
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done,
                        ),
                        decorator = TextFieldDecorator { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .clip(LargeShape)
                                    .background(cardSurfaceColor())
                                    .padding(
                                        horizontal = PageGutter,
                                        vertical = ListItemVerticalPadding,
                                    ),
                            ) {
                                innerTextField()
                            }
                        },
                    )
                    if (onCopyColor != null) {
                        PressIconButton(
                            onClick = {
                                onCopyColor.invoke(previewHex)
                                showToast(copiedHint)
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.color_picker_copy),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // 最近使用色（进程内 MRU，跨页共享）：点选即作为待定自定义色。
                val recent = RecentColors.snapshot()
                if (recent.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
                    ) {
                        recent.take(12).forEach { argb ->
                            Box(
                                modifier = Modifier
                                    .size(SubMinInteractiveSize)
                                    .clip(CircleShape)
                                    .background(Color(argb))
                                    .border(CanvasStrokeWidth, scheme.outlineVariant, CircleShape)
                                    .pressClickable(
                                        onClick = {
                                            swatchArgb = argb
                                            selectedTab = 0
                                        },
                                        role = Role.Button,
                                    ),
                            ) {}
                        }
                    }
                }

                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "colorPickerTab",
                ) { tab ->
                    when (tab) {
                        0 -> CustomColorContent(
                            hue = hue,
                            saturation = saturation,
                            value = value,
                            alpha = alpha,
                            onHueSaturationChanged = { newHue, newSaturation ->
                                hue = newHue
                                saturation = newSaturation
                                swatchArgb = null
                            },
                            onValueChanged = {
                                value = it
                                swatchArgb = null
                            },
                            onAlphaChanged = { alpha = it },
                        )
                        1 -> ThemeColorPalette(
                            selectedKey = selectedThemeKey,
                            onSelect = {
                                selectedThemeKey = it
                                swatchArgb = null
                            },
                        )
                        2 -> StandardColorPalette(
                            selectedArgb = swatchArgb,
                            onSelect = { swatchArgb = it },
                        )
                        else -> ExtractColorContent(
                            onSelect = { swatchArgb = it },
                        )
                    }
                }
            }

            // 按钮背景延伸到 sheet 底部：底边距 + 手势导航区都在 Surface 内，背景全包。
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .animateContentSize(),
                shape = SheetTopShape,
                color = raisedSurfaceColor(),
            ) {
                val confirmEnabled = when (selectedTab) {
                    1 -> selectedThemeKey != null
                    2, 3 -> swatchArgb != null
                    else -> true
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PageGutter, vertical = ListItemVerticalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ListSpacing),
                ) {
                    // tab 区 weight(1f) + 横向滚动：4 个 tab 在窄屏（320dp）放不下，
                    // 也不能把右侧「确定」挤出屏幕（tab 是辅助导航，按钮必须完整可见）。
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = MinInteractiveSize)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
                    ) {
                        val tabs = listOf(
                            stringResource(id = R.string.custom) to 0,
                            stringResource(id = R.string.color_picker_theme_tab) to 1,
                            stringResource(id = R.string.color_picker_standard_tab) to 2,
                            stringResource(id = R.string.color_picker_extract_tab) to 3,
                        )
                        tabs.forEach { (label, index) ->
                            val selected = selectedTab == index
                            Box(
                                modifier = Modifier
                                    .heightIn(min = MinInteractiveSize)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primaryContainer
                                        else Color.Transparent,
                                    )
                                    .pressClickable(onClick = { selectedTab = index }, role = Role.Button)
                                    .padding(horizontal = PageGutter),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    maxLines = 1,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    SheetSingleActionButton(
                        text = stringResource(id = R.string.confirm),
                        enabled = confirmEnabled,
                        onClick = {
                            val selection = when (selectedTab) {
                                1 -> {
                                    val key = selectedThemeKey ?: return@SheetSingleActionButton
                                    ColorSelection.Theme(key)
                                }
                                2, 3 -> {
                                    val argb = swatchArgb ?: return@SheetSingleActionButton
                                    ColorSelection.Custom(Color(argb))
                                }
                                else -> ColorSelection.Custom(resolvedColor)
                            }
                            RecentColors.push(selection.toArgb(scheme))
                            onColorSelected(selection)
                            onDismissRequest()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomColorContent(
    hue: Float,
    saturation: Float,
    value: Float,
    alpha: Float,
    onHueSaturationChanged: (hue: Float, saturation: Float) -> Unit,
    onValueChanged: (Float) -> Unit,
    onAlphaChanged: (Float) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ListSpacing),
    ) {
        HsvWheelPicker(
            hue = hue,
            saturation = saturation,
            value = value,
            onHueSaturationChanged = onHueSaturationChanged,
            modifier = Modifier.size(ColorWheelSize),
        )

        // 渐变 Brush 按色相/饱和度缓存：拖动明度时不重建。
        val valueBrush = remember(hue, saturation) {
            Brush.horizontalGradient(
                colors = listOf(Color.Black, Color.hsv(hue, saturation, 1f)),
            )
        }
        ColorSlider(
            value = value,
            onValueChange = onValueChanged,
            brush = valueBrush,
            label = stringResource(R.string.color_picker_value),
            valueText = "${(value * 100).roundToInt()}%",
        )

        val opaque = Color.hsv(hue, saturation, value)
        val alphaBrush = remember(opaque) {
            Brush.horizontalGradient(
                colors = listOf(opaque.copy(alpha = 0f), opaque.copy(alpha = 1f)),
            )
        }
        ColorSlider(
            value = alpha,
            onValueChange = onAlphaChanged,
            brush = alphaBrush,
            label = stringResource(R.string.color_picker_alpha),
            valueText = "${(alpha * 100).roundToInt()}%",
            checker = true,
        )
    }
}

@Composable
private fun ThemeColorPalette(
    selectedKey: ThemeColorKey?,
    onSelect: (ThemeColorKey) -> Unit,
) {
    val keys = ThemeColorKey.entries
    ColorSwatchGrid(count = keys.size) { index, cardModifier ->
        val themeKey = keys[index]
        val color = themeKey.resolveColor()
        ColorSwatchCard(
            color = color,
            name = stringResource(id = themeKey.displayNameRes),
            hex = formatHex(color),
            selected = selectedKey == themeKey,
            onClick = { onSelect(themeKey) },
            modifier = cardModifier,
        )
    }
}

/** alpha = 255 时输出 `#RRGGBB`，否则输出 `#AARRGGBB`。 */
private fun formatHex(color: Color): String {
    val argb = color.toArgb()
    val a = AndroidColor.alpha(argb)
    val r = AndroidColor.red(argb)
    val g = AndroidColor.green(argb)
    val b = AndroidColor.blue(argb)
    return if (a == 255) {
        String.format(Locale.ROOT, "#%02X%02X%02X", r, g, b)
    } else {
        String.format(Locale.ROOT, "#%02X%02X%02X%02X", a, r, g, b)
    }
}

// ---------------------------------------------------------------- 最近使用色

/**
 * 进程内最近使用色（MRU，上限 [Max]，跨页共享）。不落盘：取色器是高频轻交互，
 * 持久化交给调用方（写入自定义调色板 / 配置）承担，这里只做会话内记忆。
 */
internal object RecentColors {
    const val Max = 12
    private val items = mutableListOf<Int>()

    fun snapshot(): List<Int> = items.toList()

    fun push(argb: Int) {
        synchronized(this) {
            items.remove(argb)
            items.add(0, argb)
            while (items.size > Max) items.removeAt(items.size - 1)
        }
    }
}

// ---------------------------------------------------------------- 标准色板

/** Material 500 主色 + 中性灰：ARGB 与色名资源成对，卡片要同时显示两者。 */
private data class StandardSwatch(val argb: Int, val nameRes: Int)

private val StandardMaterialColors: List<StandardSwatch> = listOf(
    StandardSwatch(0xFFF44336.toInt(), R.string.color_picker_swatch_red),
    StandardSwatch(0xFFE91E63.toInt(), R.string.color_picker_swatch_pink),
    StandardSwatch(0xFF9C27B0.toInt(), R.string.color_picker_swatch_purple),
    StandardSwatch(0xFF673AB7.toInt(), R.string.color_picker_swatch_deep_purple),
    StandardSwatch(0xFF3F51B5.toInt(), R.string.color_picker_swatch_indigo),
    StandardSwatch(0xFF2196F3.toInt(), R.string.color_picker_swatch_blue),
    StandardSwatch(0xFF03A9F4.toInt(), R.string.color_picker_swatch_light_blue),
    StandardSwatch(0xFF00BCD4.toInt(), R.string.color_picker_swatch_cyan),
    StandardSwatch(0xFF009688.toInt(), R.string.color_picker_swatch_teal),
    StandardSwatch(0xFF4CAF50.toInt(), R.string.color_picker_swatch_green),
    StandardSwatch(0xFF8BC34A.toInt(), R.string.color_picker_swatch_light_green),
    StandardSwatch(0xFFCDDC39.toInt(), R.string.color_picker_swatch_lime),
    StandardSwatch(0xFFFFEB3B.toInt(), R.string.color_picker_swatch_yellow),
    StandardSwatch(0xFFFFC107.toInt(), R.string.color_picker_swatch_amber),
    StandardSwatch(0xFFFF9800.toInt(), R.string.color_picker_swatch_orange),
    StandardSwatch(0xFFFF5722.toInt(), R.string.color_picker_swatch_deep_orange),
    StandardSwatch(0xFF795548.toInt(), R.string.color_picker_swatch_brown),
    StandardSwatch(0xFF9E9E9E.toInt(), R.string.color_picker_swatch_grey),
    StandardSwatch(0xFF607D8B.toInt(), R.string.color_picker_swatch_blue_grey),
    StandardSwatch(0xFF000000.toInt(), R.string.color_picker_swatch_black),
    StandardSwatch(0xFFFFFFFF.toInt(), R.string.color_picker_swatch_white),
)

@Composable
private fun StandardColorPalette(
    selectedArgb: Int?,
    onSelect: (Int) -> Unit,
) {
    ColorSwatchGrid(count = StandardMaterialColors.size) { index, cardModifier ->
        val swatch = StandardMaterialColors[index]
        ColorSwatchCard(
            color = Color(swatch.argb),
            name = stringResource(swatch.nameRes),
            hex = formatHex(Color(swatch.argb)),
            selected = selectedArgb == swatch.argb,
            onClick = { onSelect(swatch.argb) },
            modifier = cardModifier,
        )
    }
}

// ---------------------------------------------------------------- 提取

private const val TAG = "ColorPicker"

@Composable
private fun ExtractColorContent(
    onSelect: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var swatches by remember { mutableStateOf<List<Int>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    val extractingFailed = stringResource(R.string.color_picker_extract_failed)
    // 壁纸直读（WallpaperManager.getDrawable）Android 14 起必抛 SecurityException，
    // Android 13 返回系统默认壁纸：只有持有「所有文件访问」的 app 才能读到真实壁纸。
    // 授权状态在 onResume 重查（从系统设置页授权回来时刷新），见 Permissions 同款模式。
    val lifecycleOwner = LocalLifecycleOwner.current
    var permTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val wallpaperAllowed = remember(permTick) { Environment.isExternalStorageManager() }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { extractFromImage(context, uri) }
                        .onFailure { Log.w(TAG, "image extract failed", it) }
                        .getOrNull()
                }
                busy = false
                if (result.isNullOrEmpty()) showToast(extractingFailed) else swatches = result
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ListSpacing),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
        ) {
            PressIconButton(
                onClick = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            ) {
                Icon(Icons.Default.Image, contentDescription = stringResource(R.string.color_picker_extract_image))
            }
            PressIconButton(
                enabled = wallpaperAllowed && !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { extractFromWallpaper(context) }
                                .onFailure { Log.w(TAG, "wallpaper extract failed", it) }
                                .getOrNull()
                        }
                        busy = false
                        if (result.isNullOrEmpty()) showToast(extractingFailed) else swatches = result
                    }
                },
            ) {
                Icon(Icons.Default.Wallpaper, contentDescription = stringResource(R.string.color_picker_extract_wallpaper))
            }
        }
        if (!wallpaperAllowed) {
            Text(
                text = stringResource(R.string.color_picker_extract_wallpaper_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (busy) {
            Text(
                text = stringResource(R.string.color_picker_extract_busy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (swatches.isNotEmpty()) {
            val shown = swatches.take(6)
            ColorSwatchGrid(count = shown.size) { index, cardModifier ->
                val argb = shown[index]
                ColorSwatchCard(
                    color = Color(argb),
                    name = stringResource(R.string.color_picker_extract_swatch, index + 1),
                    hex = formatHex(Color(argb)),
                    selected = false,
                    onClick = { onSelect(argb) },
                    modifier = cardModifier,
                )
            }
        }
    }
}

/** 相册图片 → 主色（解码时降采样 + 强制软件位图，IO 线程）。 */
private fun extractFromImage(context: android.content.Context, uri: android.net.Uri): List<Int> {
    // ImageDecoder 默认分配 HARDWARE 位图（API 28+），HARDWARE 调 getPixels 直接抛
    // IllegalStateException（Palette 量化第一步就是 getPixels）；显式 SOFTWARE。
    // setTargetSampleSize 解码期降采样，控制大图内存。
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val maxDim = if (info.size.width > info.size.height) info.size.width else info.size.height
        val sample = (maxDim + 255) / 256
        decoder.setTargetSampleSize(sample.coerceAtLeast(1))
        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
    }
    return extractPaletteColors(bitmap)
}

/**
 * 壁纸 → 主色。调用方已按「所有文件访问」授权 gate（Android 14 起未授权直接抛
 * SecurityException），这里 drawable 转位图后走统一量化。IO 线程。
 */
@SuppressLint("MissingPermission")
private fun extractFromWallpaper(context: android.content.Context): List<Int> {
    val drawable = WallpaperManager.getInstance(context).drawable ?: return emptyList()
    val bitmap = createBitmap(
        drawable.intrinsicWidth.coerceAtLeast(1),
        drawable.intrinsicHeight.coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    val canvas = android.graphics.Canvas(bitmap)
    drawable.setBounds(0, 0, bitmap.width, bitmap.height)
    drawable.draw(canvas)
    return extractPaletteColors(bitmap)
}

/** Palette 量化出最多 6 个主色（ARGB）；bitmap 长边降采样到 256，控制量化开销。 */
private fun extractPaletteColors(bitmap: Bitmap): List<Int> {
    val w = bitmap.width
    val h = bitmap.height
    val scale = min(256f / w, 256f / h).coerceAtMost(1f)
    val small = if (scale < 1f) {
        bitmap.scale(
            (w * scale).toInt().coerceAtLeast(1),
            (h * scale).toInt().coerceAtLeast(1),
            filter = true,
        )
    } else {
        bitmap
    }
    return runCatching {
        val palette = Palette.from(small).maximumColorCount(16).generate()
        // 默认过滤器会滤掉纯黑 / 纯白 / 近红 I 线：极端图（纯色墙纸等）可能一条不剩，
        // 兜底主色（按 population 最大），不让提取整趟白跑。
        palette.swatches.ifEmpty {
            palette.dominantSwatch?.let { listOf(it) } ?: emptyList()
        }.map { it.rgb }.take(6)
    }.getOrElse { emptyList() }
}
