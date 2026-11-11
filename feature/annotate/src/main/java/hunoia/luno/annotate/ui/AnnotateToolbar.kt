package hunoia.luno.annotate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.AutoFixOff
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Crop32
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Filter9Plus
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Rectangle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.annotate.R
import hunoia.luno.annotate.model.AnnotateDocument
import hunoia.luno.annotate.model.AnnotateState
import hunoia.luno.annotate.model.ShapeKind
import hunoia.luno.annotate.model.ToolKind
import hunoia.luno.annotate.model.ToolStyle
import hunoia.luno.annotate.render.exportPixelSize
import hunoia.luno.ui.theme.CompactControlIconSize
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.SubMinInteractiveSize
import hunoia.luno.ui.component.PressFilledIconButton
import hunoia.luno.ui.component.PressIconButton

/**
 * 两个宿主共用的工具条内容。宿主只决定它停在哪、是否可拖拽：
 * 屏幕标注放进独立浮层窗口（长按拖动 / 贴边），图片编辑固定在底部。
 *
 * 三行布局（§4.8 紧凑工具条）：
 * 1. 工具（恰好 7 个，窄屏也能一行放下）；
 * 2. 调色板（可横向滚动）+ 清除选区 + 退出/导出（常驻）；
 * 3. 撤销/重做 + 宿主扩展动作 + 尺寸滑杆（橡皮激活时该位换成矩形擦 / 清除全部）。
 *
 * 退出/导出/撤销不能放进可滚动区：否则窄屏上会被挤出可视区。
 */
@Composable
fun AnnotateToolbar(
    document: AnnotateDocument,
    onExit: () -> Unit,
    onExport: () -> Unit,
    exportLabel: String,
    modifier: Modifier = Modifier,
    onToolClick: (ToolKind) -> Unit = {},
    extraActions: @Composable RowScope.() -> Unit = {},
    /** 裁剪面板“恢复整图”按钮；null = 不显示该按钮。 */
    onCropReset: (() -> Unit)? = null,
) {
    val state = document.state
    val tool = state.activeTool

    // 紧凑工具条：关掉 M3 的 48dp 强制触控区，否则 7 个工具在窄屏（dp 宽度 ~300）上放不下。
    // 36dp 仍高于 ui-guidelines §4.8 允许的 32dp 下限。
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Surface(
            modifier = modifier,
            shape = LargeShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            // 无投影（P：工具卡去阴影），层次靠色差。
            shadowElevation = 0.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                // 工具行：点工具选中并通知宿主弹样式二层卡（再点同一工具收起）。
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // 主工具在前、次级（荧光笔/直线/箭头）在后，中间加细分隔线；
                    // 窄屏仍可整行横向滚动（P1 可发现性）。
                    ToolDisplayOrder.forEachIndexed { index, kind ->
                        if (index in GroupBoundaries) {
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .width(1.dp)
                                    .height(22.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant),
                            )
                        }
                        ToolButton(
                            kind = kind,
                            selected = kind == tool,
                            onClick = {
                                document.selectTool(kind)
                                onToolClick(kind)
                            },
                        )
                    }
                }

                // 底部操作行：样式（颜色/大小等）全部进二层卡，这里只留全局操作。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompactIconButton(
                        icon = Icons.AutoMirrored.Filled.Undo,
                        label = stringResource(R.string.annotate_undo),
                        enabled = state.undoStack.isNotEmpty(),
                        onClick = { document.undo() },
                    )
                    CompactIconButton(
                        icon = Icons.AutoMirrored.Filled.Redo,
                        label = stringResource(R.string.annotate_redo),
                        enabled = state.redoStack.isNotEmpty(),
                        onClick = { document.redo() },
                    )
                    if (state.selectedElementId != null) {
                        // 选中元素的删除入口（B5）；选中态由画布 SELECT 工具维护。
                        CompactIconButton(
                            icon = Icons.Default.Delete,
                            label = stringResource(R.string.annotate_delete_element),
                            onClick = { document.removeSelected() },
                        )
                    }
                    // 适应按钮：当前 0.75x 时点击变 1x，其它时回到 0.75x。
                    CompactIconButton(
                        icon = Icons.Default.FitScreen,
                        label = stringResource(R.string.annotate_reset_zoom),
                        onClick = { document.toggleViewZoom() },
                    )
                    extraActions()
                    Spacer(Modifier.weight(1f))
                    CompactIconButton(
                        icon = Icons.Default.Close,
                        label = stringResource(R.string.annotate_exit),
                        onClick = onExit,
                    )
                    CompactIconButton(
                        icon = Icons.Default.Check,
                        label = exportLabel,
                        onClick = onExport,
                    )
                }
            }
        }
    }
}

/**
 * 样式二层卡（B：点工具从工具卡上方弹出）：按工具展示该工具的样式/操作。
 *
 * 内容按工具分类：
 * - 绘画类（画笔/荧光笔/矩形/直线/箭头）与文字：色板 + 尺寸滑杆（线宽/字号）；
 * - 马赛克：颗粒大小滑杆（无颜色）；
 * - 橡皮：矩形擦开关 + 清除全部；
 * - 选区：清选区 + 选区填充马赛克 + 导出尺寸。
 *
 * 宿主负责把 [StylePanel] 叠在工具卡上方（浮层窗口内 Column 顶部 / 图片编辑底部 Column），
 * 以及管理「再点同一工具收起、点另一工具换内容」。
 */
@Composable
fun StylePanel(
    tool: ToolKind,
    document: AnnotateDocument,
    modifier: Modifier = Modifier,
    onCropReset: (() -> Unit)? = null,
) {
    val state = document.state
    val style = state.styleFor(tool)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (tool) {
            ToolKind.ERASER -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    CompactIconButton(
                        icon = Icons.Default.Rectangle,
                        label = stringResource(R.string.annotate_eraser_rect),
                        selected = state.eraserRect,
                        onClick = { document.setEraserRect(!state.eraserRect) },
                    )
                    CompactIconButton(
                        icon = Icons.Default.DeleteSweep,
                        label = stringResource(R.string.annotate_clear_all),
                        enabled = state.hasElements,
                        onClick = { document.clearAll() },
                    )
                }
                // 橡皮宽度：默认比画笔粗，独立滑杆便于精细擦除。
                SizeSlider(
                    label = stringResource(R.string.annotate_stroke_width),
                    value = style.strokeWidth,
                    valueRange = tool.sizeRange(),
                    onValueChange = {
                        document.updateStyle(tool, style.copy(strokeWidth = it))
                    },
                )
            }

            // SELECT 与 CROP 都显示选区操作（用户确认两面板共享）。
            ToolKind.SELECT, ToolKind.CROP -> {
                SelectionPanelContent(state = state, document = document)
                if (tool == ToolKind.CROP && onCropReset != null) {
                    CompactIconButton(
                        icon = Icons.Default.FitScreen,
                        label = stringResource(R.string.annotate_crop_reset),
                        onClick = onCropReset,
                    )
                }
            }

            ToolKind.SHAPE -> {
                // 形状子类型选择器（横向滚动）+ 填充开关 + 色板 + 尺寸滑杆。
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    ShapeKind.entries.forEach { shape ->
                        CompactIconButton(
                            icon = shapeIconFor(shape),
                            label = stringResource(shapeLabelFor(shape)),
                            selected = state.activeShape == shape,
                            onClick = { document.setActiveShape(shape) },
                        )
                    }
                    CompactIconButton(
                        icon = Icons.Default.FormatColorFill,
                        label = stringResource(R.string.annotate_shape_fill),
                        selected = style.shapeFilled,
                        onClick = {
                            document.updateStyle(tool, style.copy(shapeFilled = !style.shapeFilled))
                        },
                    )
                }
                ColorPaletteRow(
                    style = style,
                    onColorChange = { document.updateStyle(tool, style.copy(colorArgb = it)) },
                )
                SizeSlider(
                    label = stringResource(R.string.annotate_stroke_width),
                    value = style.primarySize(tool),
                    valueRange = tool.sizeRange(),
                    onValueChange = {
                        document.updateStyle(tool, style.withPrimarySize(tool, it))
                    },
                )
            }

            ToolKind.MOSAIC -> {
                // 颗粒大小（B6）：滑杆直接绑 styleFor(MOSAIC).mosaicCell。
                SizeSlider(
                    label = stringResource(R.string.annotate_mosaic_size),
                    value = style.mosaicCell,
                    valueRange = tool.sizeRange(),
                    onValueChange = {
                        document.updateStyle(tool, style.copy(mosaicCell = it))
                    },
                )
            }

            // 绘画类 + 文字：色板 + 尺寸滑杆。
            else -> {
                ColorPaletteRow(
                    style = style,
                    onColorChange = { document.updateStyle(tool, style.copy(colorArgb = it)) },
                )
                SizeSlider(
                    label = stringResource(
                        if (tool == ToolKind.TEXT || tool == ToolKind.NUMBER) R.string.annotate_text_size
                        else R.string.annotate_stroke_width,
                    ),
                    value = style.primarySize(tool),
                    valueRange = tool.sizeRange(),
                    onValueChange = {
                        document.updateStyle(tool, style.withPrimarySize(tool, it))
                    },
                )
            }
        }
    }
}

/** 选区操作面板：SELECT 与 CROP 共用（导出尺寸 + 填充马赛克 + 清除选区）。 */
@Composable
private fun SelectionPanelContent(
    state: AnnotateState,
    document: AnnotateDocument,
) {
    if (state.selection != null) {
        val px = state.exportPixelSize()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // 选区导出尺寸（P1）：导出前就能知道会得到多大图。
            Text(
                text = "${px.width}×${px.height}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            // 选区填充马赛克（B6）：整块铺块，作为普通元素可撤销/选中/删除。
            CompactIconButton(
                icon = Icons.Default.BlurOn,
                label = stringResource(R.string.annotate_fill_mosaic),
                onClick = {
                    document.fillSelectionMosaic(
                        state.styleFor(ToolKind.MOSAIC).mosaicCell,
                    )
                },
            )
            CompactIconButton(
                icon = Icons.Default.LayersClear,
                label = stringResource(R.string.annotate_clear_selection),
                onClick = { document.clearSelection() },
            )
        }
    } else {
        Text(
            text = stringResource(R.string.annotate_select_empty_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** 固定调色板行。 */
@Composable
private fun ColorPaletteRow(
    style: ToolStyle,
    onColorChange: (Long) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnnotatePalette.forEach { color ->
            ColorSwatch(
                color = Color(color),
                selected = style.colorArgb == color,
                onClick = { onColorChange(color) },
            )
        }
    }
}

@Composable
private fun SizeSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ToolButton(kind: ToolKind, selected: Boolean, onClick: () -> Unit) {
    val icon = iconFor(kind)
    val label = stringResource(labelFor(kind))
    if (selected) {
        PressFilledIconButton(onClick = onClick, modifier = Modifier.size(SubMinInteractiveSize)) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(CompactControlIconSize))
        }
    } else {
        PressIconButton(onClick = onClick, modifier = Modifier.size(SubMinInteractiveSize)) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(CompactControlIconSize))
        }
    }
}

@Composable
private fun CompactIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    // [selected] 用实心态表示开关类动作（如矩形擦），与工具条选中工具同一视觉语言。
    if (selected && enabled) {
        PressFilledIconButton(onClick = onClick, modifier = Modifier.size(SubMinInteractiveSize)) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(CompactControlIconSize))
        }
        return
    }
    PressIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(SubMinInteractiveSize),
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(CompactControlIconSize))
    }
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    val ring = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    PressIconButton(onClick = onClick, modifier = Modifier.size(SubMinInteractiveSize)) {
        Surface(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .background(ring, CircleShape)
                .padding(3.dp),
            shape = CircleShape,
            color = color,
        ) {
            if (selected) {
                // 选中环用 Surface 内边距留出，不再画第二层。
                Spacer(Modifier.size(16.dp))
            }
        }
    }
}

private fun iconFor(kind: ToolKind): ImageVector = when (kind) {
    ToolKind.PEN -> Icons.Default.Edit
    ToolKind.HIGHLIGHT -> Icons.Default.Highlight
    ToolKind.ERASER -> Icons.Default.AutoFixOff
    ToolKind.TEXT -> Icons.Default.TextFields
    ToolKind.MOSAIC -> Icons.Default.BlurOn
    ToolKind.SELECT -> Icons.Default.CropFree
    ToolKind.SHAPE -> Icons.Default.Category
    ToolKind.CROP -> Icons.Default.Crop
    ToolKind.NUMBER -> Icons.Default.Filter9Plus
}

private fun labelFor(kind: ToolKind): Int = when (kind) {
    ToolKind.PEN -> R.string.annotate_tool_pen
    ToolKind.HIGHLIGHT -> R.string.annotate_tool_highlight
    ToolKind.ERASER -> R.string.annotate_tool_eraser
    ToolKind.TEXT -> R.string.annotate_tool_text
    ToolKind.MOSAIC -> R.string.annotate_tool_mosaic
    ToolKind.SELECT -> R.string.annotate_tool_select
    ToolKind.SHAPE -> R.string.annotate_tool_shape
    ToolKind.CROP -> R.string.annotate_tool_crop
    ToolKind.NUMBER -> R.string.annotate_tool_number
}

/** SHAPE 面板的形状子类型图标。 */
private fun shapeIconFor(kind: ShapeKind): ImageVector = when (kind) {
    ShapeKind.RECT -> Icons.Default.CropSquare
    ShapeKind.ELLIPSE -> Icons.Default.RadioButtonUnchecked
    ShapeKind.ROUND_RECT -> Icons.Default.Crop32
    ShapeKind.TRIANGLE -> Icons.Default.ChangeHistory
    ShapeKind.LINE -> Icons.Default.HorizontalRule
    ShapeKind.DASHED_LINE -> Icons.Default.Remove
    ShapeKind.ARROW -> Icons.Default.ArrowOutward
    ShapeKind.CHECK -> Icons.Default.Check
    ShapeKind.CROSS -> Icons.Default.Close
    ShapeKind.STAR -> Icons.Default.StarBorder
    ShapeKind.HEART -> Icons.Default.FavoriteBorder
}

/** SHAPE 面板的形状子类型标签。 */
private fun shapeLabelFor(kind: ShapeKind): Int = when (kind) {
    ShapeKind.RECT -> R.string.annotate_tool_rect
    ShapeKind.ELLIPSE -> R.string.annotate_tool_ellipse
    ShapeKind.ROUND_RECT -> R.string.annotate_tool_round_rect
    ShapeKind.TRIANGLE -> R.string.annotate_tool_triangle
    ShapeKind.LINE -> R.string.annotate_tool_line
    ShapeKind.DASHED_LINE -> R.string.annotate_tool_dashed_line
    ShapeKind.ARROW -> R.string.annotate_tool_arrow
    ShapeKind.CHECK -> R.string.annotate_tool_check
    ShapeKind.CROSS -> R.string.annotate_tool_cross
    ShapeKind.STAR -> R.string.annotate_tool_star
    ShapeKind.HEART -> R.string.annotate_tool_heart
}

/**
 * 工具显示顺序：核心组（画笔/荧光笔/橡皮/文字/序号）+ 形状/裁剪/马赛克组 + 选择组。
 * 组间用分隔线（[GroupBoundaries]），窄屏可横向滚动。
 */
private val ToolDisplayOrder: List<ToolKind> = listOf(
    // 核心
    ToolKind.PEN,
    ToolKind.HIGHLIGHT,
    ToolKind.ERASER,
    ToolKind.TEXT,
    ToolKind.NUMBER,
    // 形状 / 裁剪 / 马赛克
    ToolKind.SHAPE,
    ToolKind.CROP,
    ToolKind.MOSAIC,
    // 选择
    ToolKind.SELECT,
)

/** 每个分隔线前累计的工具数量（组尾）。 */
private val GroupBoundaries: List<Int> = listOf(5, 8)

/** 固定调色板：标注颜色属于“内容”，不跟主题动态取色。 */
val AnnotatePalette: List<Long> = listOf(
    0xFFFFFFFF,
    0xFFFF3B30,
    0xFFFF9500,
    0xFFFFCC00,
    0xFF34C759,
    0xFF007AFF,
    0xFF000000,
)

private fun ToolStyle.primarySize(tool: ToolKind): Float = when (tool) {
    ToolKind.TEXT -> textSize
    ToolKind.NUMBER -> textSize
    ToolKind.MOSAIC -> mosaicCell
    ToolKind.ERASER -> strokeWidth
    else -> strokeWidth
}

private fun ToolStyle.withPrimarySize(tool: ToolKind, value: Float): ToolStyle = when (tool) {
    ToolKind.TEXT -> copy(textSize = value)
    ToolKind.NUMBER -> copy(textSize = value)
    ToolKind.MOSAIC -> copy(mosaicCell = value)
    ToolKind.ERASER -> copy(strokeWidth = value)
    else -> copy(strokeWidth = value)
}

private fun ToolKind.sizeRange(): ClosedFloatingPointRange<Float> = when (this) {
    ToolKind.TEXT -> 24f..96f
    // 序号圆底直径。
    ToolKind.NUMBER -> 20f..80f
    ToolKind.MOSAIC -> 8f..48f
    ToolKind.HIGHLIGHT -> 4f..24f
    // 橡皮默认更粗，允许覆盖更大面积。
    ToolKind.ERASER -> 4f..40f
    // 其余线宽都是屏幕 dp（B9）。
    else -> 1f..16f
}
