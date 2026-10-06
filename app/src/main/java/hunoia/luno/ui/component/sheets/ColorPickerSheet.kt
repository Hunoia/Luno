package hunoia.luno.ui.component.sheets

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.SheetTopShape
import hunoia.luno.ui.theme.ExtraSmallShape
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.R
import hunoia.luno.config.model.ThemeColorKey
import hunoia.luno.ui.theme.displayNameRes
import hunoia.luno.ui.theme.resolveColor
import hunoia.luno.ui.theme.ListItemVerticalPadding
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SubMinInteractiveSize
import hunoia.luno.ui.theme.MinInteractiveSize
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.CanvasStrokeWidth
import hunoia.luno.ui.theme.CanvasThumbRadius
import hunoia.luno.ui.theme.HueBarHeight
import hunoia.luno.ui.theme.HueBarTouchHeight
import hunoia.luno.ui.theme.LargeGutter
import hunoia.luno.ui.theme.TileInnerPadding
import hunoia.luno.ui.component.sheet.LunoSheet


sealed class ColorSelection {
    data class Custom(val color: Color) : ColorSelection()
    data class Theme(val key: ThemeColorKey) : ColorSelection()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerBottomSheet(
    onDismissRequest: () -> Unit,
    onColorSelected: (ColorSelection) -> Unit,
    initialColor: Color = MaterialTheme.colorScheme.primary,
) {
    var selectedTab by remember { mutableStateOf(0) }
    var alpha by remember { mutableFloatStateOf(initialColor.alpha) }
    var hue by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(0f) }
    var value by remember { mutableFloatStateOf(1f) }
    var selectedThemeKey by remember { mutableStateOf<ThemeColorKey?>(null) }

    LaunchedEffect(initialColor) {
        alpha = initialColor.alpha
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(initialColor.toArgb(), hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
    }

    val hsvColor by remember { derivedStateOf { Color.hsv(hue, saturation, value) } }
    val resolvedColor by remember(hsvColor, alpha) {
        derivedStateOf { hsvColor.copy(alpha = alpha) }
    }
    val hexColor by remember(resolvedColor) {
        derivedStateOf {
            val nativeColor = resolvedColor.toArgb()
            val red = AndroidColor.red(nativeColor)
            val green = AndroidColor.green(nativeColor)
            val blue = AndroidColor.blue(nativeColor)
            val a = String.format("%02X", (alpha * 255).toInt())
            val r = String.format("%02X", red)
            val g = String.format("%02X", green)
            val b = String.format("%02X", blue)
            "$a$r$g$b"
        }
    }

    val previewColor = when (selectedTab) {
        1 -> selectedThemeKey?.resolveColor() ?: resolvedColor
        else -> resolvedColor
    }

    LunoSheet(
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(PageGutter)
                    .padding(bottom = ListItemVerticalPadding),
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
                            .background(
                                color = previewColor,
                                shape = CircleShape,
                            )
                            .border(
                                width = CanvasStrokeWidth,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape,
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(LargeShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = PageGutter, vertical = ListItemVerticalPadding),
                            text = "#$hexColor",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                ) { tab ->
                    when (tab) {
                        0 -> {
                            HsvRectPicker(
                                modifier = Modifier,
                                hue = hue,
                                saturation = saturation,
                                value = value,
                                onColorChanged = { h, s, v ->
                                    hue = h
                                    saturation = s
                                    value = v
                                },
                            )
                        }
                        1 -> {
                            val chunked = ThemeColorKey.entries
                                .chunked(3)
                            Column(verticalArrangement = Arrangement.spacedBy(CardInnerSpacing)) {
                                chunked.forEach { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
                                    ) {
                                        row.forEach { themeKey ->
                                            val color = themeKey.resolveColor()
                                            val isSelected = selectedThemeKey == themeKey
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(LargeShape)
                                                    .background(
                                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                        else MaterialTheme.colorScheme.surface,
                                                    )
                                                    .clickable { selectedThemeKey = themeKey }
                                                    .padding(vertical = CardInnerSpacing),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(SubMinInteractiveSize)
                                                        .then(
                                                            if (isSelected) Modifier.border(
                                                                width = CanvasStrokeWidth,
                                                                color = MaterialTheme.colorScheme.primary,
                                                                shape = CircleShape,
                                                            ) else Modifier,
                                                        )
                                                        .clip(CircleShape)
                                                        .background(color),
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
                    }
                }
            }

            AnimatedVisibility(visible = selectedTab == 0) {
                val density = LocalDensity.current
                val thumbRadius = with(density) { CanvasThumbRadius.toPx() }
                val thumbStroke = with(density) { CanvasStrokeWidth.toPx() }
                val hueBarHeight = with(density) { HueBarHeight }

                Column(
                    modifier = Modifier.padding(horizontal = LargeGutter, vertical = ListItemVerticalPadding),
                    verticalArrangement = Arrangement.spacedBy(ListSpacing),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(hueBarHeight)
                            .clip(ExtraSmallShape)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        hue = (offset.x / size.width * 360f).coerceIn(0f, 360f)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        hue = (change.position.x / size.width * 360f).coerceIn(0f, 360f)
                                    },
                                )
                            },
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawRect(HueBarBrush)

                            val thumbX = (hue / 360f) * size.width
                            val thumbY = size.height / 2f
                            drawCircle(Color.White, thumbRadius, Offset(thumbX, thumbY))
                            drawCircle(
                                Color.Black.copy(alpha = 0.3f),
                                thumbRadius - thumbStroke,
                                Offset(thumbX, thumbY),
                                style = Stroke(thumbStroke),
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(HueBarTouchHeight)
                            .clip(LargeShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 棋盘格打底：透明端落在格子上才看得出透明，不糊在底色上。
                        Canvas(Modifier.fillMaxSize()) {
                            val tile = with(density) { (TileInnerPadding * 2).toPx() }
                            var row = 0
                            var y = 0f
                            while (y < size.height) {
                                var col = row
                                var x = 0f
                                while (x < size.width) {
                                    drawRect(
                                        color = if (col % 2 == 0) Color.White else CheckerGray,
                                        topLeft = Offset(x, y),
                                        size = Size(
                                            (tile).coerceAtMost(size.width - x),
                                            (tile).coerceAtMost(size.height - y),
                                        ),
                                    )
                                    x += tile
                                    col++
                                }
                                y += tile
                                row++
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = remember(hsvColor) {
                                        Brush.horizontalGradient(colors = listOf(Color.Transparent, hsvColor))
                                    }
                                ),
                        )
                        Slider(
                            modifier = Modifier.fillMaxWidth(),
                            value = alpha,
                            onValueChange = { alpha = it },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                // 轨道全透明：渐变由底下两层画，M3 自绘轨会盖掉棋盘。
                                activeTrackColor = Color.Transparent,
                                inactiveTrackColor = Color.Transparent,
                            ),
                            // 选择器同款圆点：白实心 + 深色细环，alpha 再低也不隐形。
                            thumb = {
                                Canvas(
                                    modifier = Modifier.size(CanvasThumbRadius * 2),
                                ) {
                                    drawCircle(Color.White, radius = size.minDimension / 2f)
                                    drawCircle(
                                        Color.Black.copy(alpha = 0.3f),
                                        radius = size.minDimension / 2f - CanvasStrokeWidth.toPx() / 2,
                                        style = Stroke(CanvasStrokeWidth.toPx()),
                                    )
                                }
                            },
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PageGutter, vertical = ListItemVerticalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ListSpacing),
                ) {
                    // 无容器底：只有选中半带 primaryContainer 底，与确认键同高。
                    Row(
                        modifier = Modifier.heightIn(min = MinInteractiveSize),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
                    ) {
                        val tabs = listOf(
                            stringResource(id = R.string.custom) to 0,
                            "主题色" to 1,
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
                    Button(
                        onClick = {
                            when (selectedTab) {
                                1 -> {
                                    val key = selectedThemeKey ?: return@Button
                                    onColorSelected(ColorSelection.Theme(key))
                                }
                                else -> {
                                    val color = hsvColor.copy(alpha = alpha)
                                    onColorSelected(ColorSelection.Custom(color))
                                }
                            }
                            onDismissRequest()
                        },
                        modifier = Modifier.heightIn(min = MinInteractiveSize),
                    ) {
                        Text(text = stringResource(id = R.string.confirm))
                    }
                }
            }
        }
    }
}

/** 透明度棋盘格深色格：中性灰，与明暗主题无关，静态复用。 */
private val CheckerGray = Color(0xFFCCCCCC)

/** 色相条渐变是完全静态的，不要放进 Canvas 的 draw lambda —— 拖动时会每帧重建 */
private val HueBarBrush = Brush.horizontalGradient(
    0.00f to Color.Red,
    0.17f to Color.Yellow,
    0.33f to Color.Green,
    0.50f to Color.Cyan,
    0.67f to Color.Blue,
    0.83f to Color.Magenta,
    1.00f to Color.Red,
)
