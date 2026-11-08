package hunoia.luno.ui.component.sheet

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import hunoia.luno.ui.theme.TileInnerPadding
import hunoia.luno.ui.theme.displayNameRes
import hunoia.luno.ui.theme.resolveColor
import java.util.Locale
import kotlin.math.roundToInt

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

    val context = LocalContext.current
    val copiedHint = stringResource(R.string.color_picker_copied)

    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
    val scrollState = rememberScrollState()

    LunoSheet(
        onDismissRequest = onDismissRequest,
        scrollState = OptimizedScrollState.Scroll(scrollState),
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
                        value = hexField,
                        onValueChange = { raw ->
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
                        },
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { hexFocused = it.isFocused },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done,
                        ),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .clip(LargeShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
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
                        IconButton(
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
                            },
                            onValueChanged = { value = it },
                            onAlphaChanged = { alpha = it },
                        )
                        else -> ThemeColorPalette(
                            selectedKey = selectedThemeKey,
                            onSelect = { selectedThemeKey = it },
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
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                val confirmEnabled = selectedTab != 1 || selectedThemeKey != null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PageGutter, vertical = ListItemVerticalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ListSpacing),
                ) {
                    Row(
                        modifier = Modifier.heightIn(min = MinInteractiveSize),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
                    ) {
                        val tabs = listOf(
                            stringResource(id = R.string.custom) to 0,
                            stringResource(id = R.string.color_picker_theme_tab) to 1,
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
                                    .clickable(onClick = { selectedTab = index }, role = Role.Button)
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
                    Spacer(Modifier.weight(1f))
                    SheetSingleActionButton(
                        text = stringResource(id = R.string.confirm),
                        enabled = confirmEnabled,
                        onClick = {
                            val selection = if (selectedTab == 1) {
                                val key = selectedThemeKey ?: return@SheetSingleActionButton
                                ColorSelection.Theme(key)
                            } else {
                                ColorSelection.Custom(resolvedColor)
                            }
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
    val rows = ThemeColorKey.entries.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(CardInnerSpacing)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
            ) {
                row.forEach { themeKey ->
                    val color = themeKey.resolveColor()
                    val isSelected = selectedKey == themeKey
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(LargeShape)
                            .background(
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface,
                            )
                            .border(
                                width = CanvasStrokeWidth / 2,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = LargeShape,
                            )
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { onSelect(themeKey) },
                            )
                            .padding(vertical = CardInnerSpacing),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(SubMinInteractiveSize)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) CanvasStrokeWidth
                                    else CanvasStrokeWidth / 2,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape,
                                ),
                        )
                        Spacer(Modifier.height(TileInnerPadding))
                        Text(
                            text = stringResource(id = themeKey.displayNameRes),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (row.size < 3) {
                    repeat(3 - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
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
