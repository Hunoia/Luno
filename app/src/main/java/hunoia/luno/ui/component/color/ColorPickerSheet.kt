package hunoia.luno.ui.component.color

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.config.model.ThemeColorKey
import hunoia.luno.ui.theme.displayNameRes
import hunoia.luno.ui.theme.resolveColor
import hunoia.luno.ui.theme.Spacing12
import hunoia.luno.ui.theme.Spacing16
import hunoia.luno.ui.theme.Spacing2
import hunoia.luno.ui.theme.Spacing4
import hunoia.luno.ui.theme.Spacing24
import hunoia.luno.ui.theme.Spacing48
import hunoia.luno.ui.theme.Spacing8


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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing12),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing16, vertical = Spacing16),
                verticalArrangement = Arrangement.spacedBy(Spacing12),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing12),
                ) {
                    Box(
                        modifier = Modifier
                            .size(Spacing48)
                            .clip(CircleShape)
                            .background(
                                color = previewColor,
                                shape = CircleShape,
                            )
                            .border(
                                width = Spacing2,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape,
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = Spacing12, vertical = Spacing12),
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
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing8)) {
                                chunked.forEach { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(Spacing8),
                                    ) {
                                        row.forEach { themeKey ->
                                            val color = themeKey.resolveColor()
                                            val isSelected = selectedThemeKey == themeKey
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(MaterialTheme.shapes.large)
                                                    .background(
                                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                        else MaterialTheme.colorScheme.surface,
                                                    )
                                                    .clickable { selectedThemeKey = themeKey }
                                                    .padding(vertical = Spacing8),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .then(
                                                            if (isSelected) Modifier.border(
                                                                width = 2.dp,
                                                                color = MaterialTheme.colorScheme.primary,
                                                                shape = CircleShape,
                                                            ) else Modifier,
                                                        )
                                                        .clip(CircleShape)
                                                        .background(color),
                                                )
                                                Spacer(Modifier.height(Spacing4))
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
                val thumbRadius = with(density) { 10.dp.toPx() }
                val thumbStroke = with(density) { 2.dp.toPx() }
                val hueBarHeight = with(density) { 24.dp }

                Column(
                    modifier = Modifier.padding(horizontal = Spacing24, vertical = Spacing12),
                    verticalArrangement = Arrangement.spacedBy(Spacing12),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(hueBarHeight)
                            .clip(MaterialTheme.shapes.extraSmall)
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
                            drawRect(
                                Brush.horizontalGradient(
                                    0.00f to Color.Red,
                                    0.17f to Color.Yellow,
                                    0.33f to Color.Green,
                                    0.50f to Color.Cyan,
                                    0.67f to Color.Blue,
                                    0.83f to Color.Magenta,
                                    1.00f to Color.Red,
                                ),
                            )

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
                            .height(32.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, hsvColor),
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Slider(
                            modifier = Modifier.fillMaxWidth(),
                            value = alpha,
                            onValueChange = { alpha = it },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = hsvColor.copy(alpha = alpha),
                                activeTrackColor = hsvColor.copy(alpha = 1f),
                                inactiveTrackColor = hsvColor.copy(alpha = 0.2f),
                            ),
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing16, vertical = Spacing12),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing12),
                ) {
                    TabChip(
                        selected = selectedTab == 0,
                        text = stringResource(id = R.string.custom),
                        onClick = { selectedTab = 0 },
                        modifier = Modifier,
                    )
                    TabChip(
                        selected = selectedTab == 1,
                        text = "主题色",
                        onClick = { selectedTab = 1 },
                        modifier = Modifier,
                    )
                    Spacer(Modifier.weight(1f))
                    FilledTonalButton(
                        onClick = {
                            when (selectedTab) {
                                1 -> {
                                    val key = selectedThemeKey ?: return@FilledTonalButton
                                    onColorSelected(ColorSelection.Theme(key))
                                }
                                else -> {
                                    val color = hsvColor.copy(alpha = alpha)
                                    onColorSelected(ColorSelection.Custom(color))
                                }
                            }
                            onDismissRequest()
                        },
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(Spacing4))
                        Text(text = stringResource(id = R.string.confirm))
                    }
                }
            }
        }
    }
}

