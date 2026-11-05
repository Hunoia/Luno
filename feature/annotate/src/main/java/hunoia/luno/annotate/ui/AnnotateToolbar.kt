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
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.AutoFixOff
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.Rectangle
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import hunoia.luno.annotate.model.ToolKind
import hunoia.luno.annotate.model.ToolStyle
import hunoia.luno.annotate.render.exportPixelSize
import hunoia.luno.ui.theme.CompactControlIconSize
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.SubMinInteractiveSize

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
    extraActions: @Composable RowScope.() -> Unit = {},
) {
    val state = document.state
    val tool = state.activeTool
    val style = state.styleFor(tool)

    // 紧凑工具条：关掉 M3 的 48dp 强制触控区，否则 7 个工具在窄屏（dp 宽度 ~300）上放不下。
    // 36dp 仍高于 ui-guidelines §4.8 允许的 32dp 下限。
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Surface(
            modifier = modifier,
            shape = LargeShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // 主工具在前、次级（荧光笔/直线/箭头）在后，中间加细分隔线；
                    // 窄屏仍可整行横向滚动（P1 可发现性）。
                    ToolDisplayOrder.forEachIndexed { index, kind ->
                        if (index == PrimaryToolCount) {
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
                            onClick = { document.selectTool(kind) },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AnnotatePalette.forEach { color ->
                            ColorSwatch(
                                color = Color(color),
                                selected = style.colorArgb == color,
                                onClick = { document.updateStyle(tool, style.copy(colorArgb = color)) },
                            )
                        }
                        if (state.selection != null) {
                            // 选区导出尺寸（P1）：导出前就能知道会得到多大图。
                            val px = state.exportPixelSize()
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
                    }
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
                    if (state.viewZoom > 1.001f) {
                        // 双指放大后出现「适应屏幕」（B7），退出缩放不影响笔迹。
                        CompactIconButton(
                            icon = Icons.Default.FitScreen,
                            label = stringResource(R.string.annotate_reset_zoom),
                            onClick = { document.resetViewTransform() },
                        )
                    }
                    extraActions()
                    if (tool != ToolKind.ERASER) {
                        Slider(
                            value = style.primarySize(tool),
                            onValueChange = {
                                document.updateStyle(tool, style.withPrimarySize(tool, it))
                            },
                            valueRange = tool.sizeRange(),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp),
                        )
                    } else {
                        // 橡皮次级动作（C1）：矩形擦模式开关 + 清除全部。
                        // 只在橡皮激活时出现，不占其它工具的行宽。
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
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolButton(kind: ToolKind, selected: Boolean, onClick: () -> Unit) {
    val icon = iconFor(kind)
    val label = stringResource(labelFor(kind))
    if (selected) {
        FilledIconButton(onClick = onClick, modifier = Modifier.size(SubMinInteractiveSize)) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(CompactControlIconSize))
        }
    } else {
        IconButton(onClick = onClick, modifier = Modifier.size(SubMinInteractiveSize)) {
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
        FilledIconButton(onClick = onClick, modifier = Modifier.size(SubMinInteractiveSize)) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(CompactControlIconSize))
        }
        return
    }
    IconButton(
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
    IconButton(onClick = onClick, modifier = Modifier.size(SubMinInteractiveSize)) {
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
    ToolKind.RECT -> Icons.Default.CropSquare
    ToolKind.LINE -> Icons.Default.HorizontalRule
    ToolKind.ARROW -> Icons.Default.ArrowOutward
    ToolKind.TEXT -> Icons.Default.TextFields
    ToolKind.MOSAIC -> Icons.Default.BlurOn
    ToolKind.SELECT -> Icons.Default.CropFree
}

private fun labelFor(kind: ToolKind): Int = when (kind) {
    ToolKind.PEN -> R.string.annotate_tool_pen
    ToolKind.HIGHLIGHT -> R.string.annotate_tool_highlight
    ToolKind.ERASER -> R.string.annotate_tool_eraser
    ToolKind.RECT -> R.string.annotate_tool_rect
    ToolKind.LINE -> R.string.annotate_tool_line
    ToolKind.ARROW -> R.string.annotate_tool_arrow
    ToolKind.TEXT -> R.string.annotate_tool_text
    ToolKind.MOSAIC -> R.string.annotate_tool_mosaic
    ToolKind.SELECT -> R.string.annotate_tool_select
}

/** 工具显示顺序：主工具在前（PEN…SELECT），次级（荧光笔/直线/箭头）在分隔线后。 */
private val ToolDisplayOrder: List<ToolKind> = listOf(
    ToolKind.PEN,
    ToolKind.ERASER,
    ToolKind.RECT,
    ToolKind.TEXT,
    ToolKind.MOSAIC,
    ToolKind.SELECT,
    ToolKind.HIGHLIGHT,
    ToolKind.LINE,
    ToolKind.ARROW,
)

/** 分隔线前的工具数量（主工具区）。 */
private const val PrimaryToolCount = 6

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
    ToolKind.MOSAIC -> mosaicCell
    else -> strokeWidth
}

private fun ToolStyle.withPrimarySize(tool: ToolKind, value: Float): ToolStyle = when (tool) {
    ToolKind.TEXT -> copy(textSize = value)
    ToolKind.MOSAIC -> copy(mosaicCell = value)
    else -> copy(strokeWidth = value)
}

private fun ToolKind.sizeRange(): ClosedFloatingPointRange<Float> = when (this) {
    ToolKind.TEXT -> 24f..96f
    ToolKind.MOSAIC -> 8f..48f
    ToolKind.HIGHLIGHT -> 4f..24f
    // 其余线宽都是屏幕 dp（B9）。
    else -> 1f..16f
}
