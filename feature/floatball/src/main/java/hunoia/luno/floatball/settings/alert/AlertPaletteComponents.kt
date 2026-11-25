package hunoia.luno.floatball.settings.alert

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import hunoia.luno.config.model.AlertLimits
import hunoia.luno.config.model.AlertPalette
import hunoia.luno.config.model.rainbowRing
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.menu.LunoDropdownMenu
import hunoia.luno.ui.component.menu.LunoDropdownMenuItem
import hunoia.luno.ui.component.pressClickable
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.theme.CanvasStrokeWidth
import hunoia.luno.ui.theme.CapsuleShape
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.CompactControlIconSize
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.PaletteDotSize
import hunoia.luno.ui.theme.PaletteDotSpacing
import hunoia.luno.ui.theme.PalettePreviewHeight
import hunoia.luno.ui.theme.PaletteStripHeight
import hunoia.luno.ui.theme.PaletteSwatchSize
import hunoia.luno.ui.theme.SubMinInteractiveSize
import kotlin.math.roundToInt

/**
 * 调色板缩略预览：非彩虹 = 一排色点（1–[AlertLimits.MaxPaletteColors] 色），
 * 彩虹 = 一条色相环渐变条。
 *
 * 彩虹条与渲染端共用 `AlertPalette.rainbowRing`（首色在末尾再放一次让渐变首尾相接），
 * 预览与实际播放同色；位置不受字体缩放影响，是固定尺寸的色块。
 */
@Composable
internal fun PalettePreview(
    palette: AlertPalette,
    modifier: Modifier = Modifier,
) {
    if (palette.rainbow) {
        val ring = palette.rainbowRing().map { Color(it) }
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(PalettePreviewHeight)
                .clip(CapsuleShape)
                .background(
                    brush = Brush.horizontalGradient(ring + ring.first()),
                ),
        ) {}
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(PaletteDotSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            palette.colors.take(AlertLimits.MaxPaletteColors).forEach { argb ->
                Row(
                    modifier = Modifier
                        .size(PaletteDotSize)
                        .clip(CircleShape)
                        .background(Color(argb)),
                ) {}
            }
        }
    }
}

/** 单色色点（「跟随动效颜色」预览用）。 */
@Composable
internal fun SingleColorDot(color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .size(PaletteDotSize)
            .clip(CircleShape)
            .background(color),
    ) {}
}

/**
 * 紧凑色条预览：单行胶囊，彩虹 = 色相环渐变条，其余 = 1–5 色水平均分。
 * 比缩略色点更大更直观，用于下拉菜单项与「调色板」行的当前值展示。
 */
@Composable
internal fun PaletteSwatchStrip(
    palette: AlertPalette,
    modifier: Modifier = Modifier,
) {
    if (palette.rainbow) {
        val ring = palette.rainbowRing().map { Color(it) }
        Row(
            modifier = modifier
                .size(width = PaletteStripHeight * 2, height = PaletteStripHeight)
                .clip(CapsuleShape)
                .background(brush = Brush.horizontalGradient(ring + ring.first())),
        ) {}
    } else {
        Row(
            modifier = modifier
                .size(width = PaletteStripHeight * 2, height = PaletteStripHeight)
                .clip(CapsuleShape),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val colors = palette.colors.take(AlertLimits.MaxPaletteColors)
                .ifEmpty { listOf(0xFF9E9E9E.toInt()) }
            colors.forEach { argb ->
                Row(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(argb))) {}
            }
        }
    }
}

/**
 * 编辑页整条预览：1–5 色水平均分拼接（与渲染端「相邻两色线性插值」不同，这里是逐色硬
 * 分段，方便辨认每色边界）；空色表 = 中性灰占位。高度默认 [PaletteStripHeight]，
 * 编辑页用 [height] 传更高的值做出「效果条」的存在感。
 */
@Composable
internal fun PaletteStrip(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    height: Dp = PaletteStripHeight,
) {
    Row(
        modifier = modifier
            .height(height)
            .clip(CapsuleShape),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val list = colors.ifEmpty { listOf(Color(0xFF9E9E9E)) }
        list.forEach { color ->
            Row(modifier = Modifier.weight(1f).fillMaxHeight().background(color)) {}
        }
    }
}

/**
 * 调色板卡片（单列全宽）：上部色条预览（彩虹 = 色相环渐变条，其余 = 逐色分段），
 * 下部名称 + 副信息（色数 / 彩虹）。[trailing] 是右侧操作按钮（内置「新建」/ 自定义
 * 「编辑」）；[selected] 高亮描边（选择 sheet 的选中态）；[onClick] = null 时主体不可点
 * （内置卡只用按钮触发「以此新建」，防误触复制）。样式与取色器 `ColorSwatchCard` 同源。
 */
@Composable
internal fun PaletteCard(
    palette: AlertPalette,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val subtitle = if (palette.rainbow) {
        stringResource(R.string.float_ball_capsule_alert_palette_rainbow)
    } else {
        stringResource(R.string.float_ball_capsule_alert_palette_color_count, palette.colors.size)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(LargeShape)
            .background(if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh)
            .then(
                if (onClick != null) {
                    Modifier.pressClickable(onClick = onClick, role = Role.Button)
                } else {
                    Modifier
                },
            )
            .padding(CardInnerSpacing),
    ) {
        if (palette.rainbow) {
            val ring = palette.rainbowRing().map { Color(it) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PaletteStripHeight)
                    .clip(CapsuleShape)
                    .background(brush = Brush.horizontalGradient(ring + ring.first())),
            ) {}
        } else {
            PaletteStrip(
                colors = palette.colors.take(AlertLimits.MaxPaletteColors).map { Color(it) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(CardInnerSpacing))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = palette.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            trailing?.invoke()
        }
    }
}

/**
 * 编辑页单色块：点击取色；[locked] 时右下角显示锁角标（锁定 / 解锁 / 移除走长按菜单，
 * 见 [PaletteSwatchRow]）。边长由调用方 modifier 决定（行内按可用宽度均分、上限
 * [PaletteSwatchSize]），不再写死固定边长——6 色时格子只有约 40dp，写死 48dp 会横向
 * 溢出、互相压住。描边用主题 hairline（近白色块也能辨出边界，与取色器色卡同源）。
 */
@Composable
internal fun PaletteSwatch(
    color: Color,
    locked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(LargeShape)
            .background(color)
            .border(
                width = CanvasStrokeWidth,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = LargeShape,
            )
            .pressClickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        if (locked) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(SubMinInteractiveSize / 2)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = stringResource(R.string.float_ball_capsule_alert_palette_unlock),
                    tint = Color.White,
                    modifier = Modifier.size(CompactControlIconSize),
                )
            }
        }
    }
}

/**
 * 编辑页色块行：长按拖拽横向重排（**实时重排**——拖块中心跨过相邻槽位中点即交换，
 * 视觉连续；松手落盘）。点击取色；长按到阈值立刻弹菜单（锁定 / 移除），继续拖动则
 * 收起菜单转拖拽。尾部「添加」。顺序即渲染取色顺序。
 *
 * 长按命中后要先「落门」（[clickBlockedUid]）：`clickable` 的 tap 判定在子节点先跑，
 * 且抬手判定不检查消费标记，父节点 consume 压不住，松手会同时开色盘——只能靠状态门
 * 在 onClick 里拦一次。门在抬手（或手势中断）后由 finally 打开。
 *
 * 拖拽用**绝对定位模型**：累计位移（相对拖起点）决定目标槽位 =
 * 拖起下标 + round(总位移 / 槽位节距)。每次重算都是幂等的，不存在跨槽后越滚越远的
 * 累积误差（旧实现把累计值当增量叠 `dragOffset += x`，色块会被直接甩到两端）。
 * 绘制偏移只取「相对当前槽位的余量」，所以块始终跟着手指。
 *
 * uid = 该块在 `colors` 里的下标（预览序是落盘序的排列）：重复色值也能各自点击 /
 * 锁定 / 移除，不再用 `indexOf(argb)` 撞到第一个同色块。
 */
@Composable
internal fun PaletteSwatchRow(
    colors: List<Int>,
    locked: Set<Int>,
    onToggleLock: (Int) -> Unit,
    onSelect: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 预览序（拖拽中的本地排列）。拖拽期间 colors 不变 → uid 稳定；落盘（onMove 改
    // colors）后 remember(colors) 重建，那时拖拽已经结束。
    var preview by remember(colors) {
        mutableStateOf(colors.mapIndexed { i, c -> PaletteSwatchItem(i, c) })
    }
    var draggingUid by remember { mutableStateOf<Int?>(null) }
    var dragFromIndex by remember { mutableIntStateOf(-1) } // 拖起时该块在 colors 的下标
    var dragOffset by remember { mutableFloatStateOf(0f) } // 绘制偏移（当前槽位内余量）
    var slotWidth by remember { mutableIntStateOf(0) }
    var menuForUid by remember { mutableStateOf<Int?>(null) } // 长按菜单打开的块
    // 长按成立后到抬手之间，忽略该块的点击。`clickable` 的抬手判定（waitForUpOrCancellation）
    // 先返回抬手事件、不检查消费标记，父节点 consume 压不住，只能靠这个状态门挡。
    var clickBlockedUid by remember { mutableStateOf<Int?>(null) }

    // 槽位节距 = 格子宽 + 行间距；不算间距会每跨一格就偏 8dp，越拖越不准。
    val cellSpacingPx = with(LocalDensity.current) { CardInnerSpacing.toPx() }
    val draggingIndex = draggingUid?.let { uid -> preview.indexOfFirst { it.uid == uid } }

    fun beginDrag(uid: Int) {
        draggingUid = uid
        dragFromIndex = preview.indexOfFirst { it.uid == uid }
        dragOffset = 0f
    }

    /** 累计位移 → 绝对目标槽位（幂等，可重复调用）。 */
    fun applyDrag(totalX: Float) {
        val uid = draggingUid ?: return
        if (preview.isEmpty()) return
        val pitch = slotWidth + cellSpacingPx
        if (pitch <= 0f) return
        val target = (dragFromIndex + (totalX / pitch).roundToInt())
            .coerceIn(0, preview.lastIndex)
        val current = preview.indexOfFirst { it.uid == uid }
        if (current < 0) return
        if (current != target) {
            preview = preview.toMutableList().apply { add(target, removeAt(current)) }
        }
        // 绘制偏移 = 总位移扣除已跨过的整数个槽位，块永远停在手指下。正常拖动时余量本来
        // 就在 ±半格内；拖到首尾后再往外拖，target 被 clamp 住，这里也把余量钉在半格，
        // 免得上限之外色块继续飞出卡片。
        val half = pitch / 2f
        dragOffset = (totalX - (target - dragFromIndex) * pitch).coerceIn(-half, half)
    }

    fun endDrag() {
        // 预览序与落盘序只差被拖块的位置：from = 拖起时的落盘下标，to = 拖后在预览序的
        // 位置（onMove 内同步搬移锁定下标）。
        val uid = draggingUid
        if (uid != null) {
            val to = preview.indexOfFirst { it.uid == uid }
            if (dragFromIndex >= 0 && to >= 0 && dragFromIndex != to) {
                onMove(dragFromIndex, to)
            }
        }
        draggingUid = null
        dragFromIndex = -1
        dragOffset = 0f
    }

    Row(
        modifier = modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CardInnerSpacing),
    ) {
        preview.forEachIndexed { index, item ->
            val isDragging = index == draggingIndex
            key(item.uid) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .onSizeChanged { if (it.width > 0) slotWidth = it.width }
                        .then(if (isDragging) Modifier.zIndex(1f) else Modifier)
                        .graphicsLayer {
                            if (isDragging) {
                                translationX = dragOffset
                                scaleX = 1.08f
                                scaleY = 1.08f
                            }
                        }
                        .pointerInput(item.uid) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                // 没到长按时限：这是轻点 / 轻滑，交给下层 clickable 取色。
                                if (awaitLongPressOrCancellation(down.id) == null) {
                                    return@awaitEachGesture
                                }
                                // 到阈值立刻弹菜单（不等抬手），并落门挡掉这次抬手会漏出的
                                // onClick —— clickable 的 tap 判定在子节点先跑，父节点 consume
                                // 已经晚了，只能靠状态门。
                                menuForUid = item.uid
                                clickBlockedUid = item.uid
                                try {
                                    var totalX = 0f
                                    var started = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                            ?: break
                                        if (!change.pressed) {
                                            change.consume()
                                            break
                                        }
                                        totalX += change.positionChange().x
                                        if (!started) {
                                            if (kotlin.math.abs(totalX) <= viewConfiguration.touchSlop) {
                                                continue
                                            }
                                            // 转拖拽：菜单收起，手势交给重排。
                                            started = true
                                            menuForUid = null
                                            beginDrag(item.uid)
                                        }
                                        change.consume()
                                        applyDrag(totalX)
                                    }
                                    if (started) endDrag()
                                } finally {
                                    // 抬手后开门；子节点的 onTap 已在抬手派发时跑完，清晚了没关系。
                                    if (clickBlockedUid == item.uid) clickBlockedUid = null
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    PaletteSwatch(
                        color = Color(item.argb),
                        locked = item.uid in locked,
                        // 长按接管期间（clickBlockedUid）忽略这次点击，否则抬手会同时开色盘。
                        onClick = { if (clickBlockedUid != item.uid) onSelect(item.uid) },
                        modifier = Modifier
                            .widthIn(max = PaletteSwatchSize)
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    )
                    LunoDropdownMenu(
                        expanded = menuForUid == item.uid,
                        onDismissRequest = { menuForUid = null },
                    ) {
                        LunoDropdownMenuItem(
                            text = stringResource(
                                if (item.uid in locked) {
                                    R.string.float_ball_capsule_alert_palette_unlock
                                } else {
                                    R.string.float_ball_capsule_alert_palette_lock
                                },
                            ),
                            leadingIcon = {
                                Icon(
                                    imageVector = if (item.uid in locked) {
                                        Icons.Default.LockOpen
                                    } else {
                                        Icons.Default.Lock
                                    },
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                menuForUid = null
                                onToggleLock(item.uid)
                            },
                        )
                        if (colors.size > 1) {
                            LunoDropdownMenuItem(
                                text = stringResource(R.string.float_ball_capsule_alert_palette_remove_color),
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    menuForUid = null
                                    onRemove(item.uid)
                                },
                            )
                        }
                    }
                }
            }
        }
        if (colors.size < AlertLimits.MaxPaletteColors) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                PaletteSwatchAdd(
                    onClick = onAdd,
                    modifier = Modifier
                        .widthIn(max = PaletteSwatchSize)
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )
            }
        }
    }
}

/** 拖拽中的稳定条目：uid 在 colors 变化时重新分配，拖拽期间稳定（重复 ARGB 也能区分）。 */
private class PaletteSwatchItem(val uid: Int, val argb: Int)

/** 编辑页「添加颜色」色块：虚位样式（surfaceContainerHigh 底 + 加号）。边长由调用方决定。 */
@Composable
internal fun PaletteSwatchAdd(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(LargeShape)
            .border(
                width = CanvasStrokeWidth / 2,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = LargeShape,
            )
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .pressClickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = stringResource(R.string.float_ball_capsule_alert_palette_add_color),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 分类设置页的「调色板」行：缩略预览（无调色板 = 单色回退）+ 当前名 + 点进选择页。
 * [fallbackColor] 是未选调色板时的单色（主题解析后），由调用页传入。
 */
@Composable
internal fun AlertPaletteSettingsRow(
    palette: AlertPalette?,
    fallbackColor: Color,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
) {
    LunoSettingsRow(
        title = stringResource(R.string.float_ball_capsule_alert_palette),
        subtitle = palette?.name
            ?: stringResource(R.string.float_ball_capsule_alert_palette_none),
        shape = shape,
        leading = {
            if (palette != null) {
                PalettePreview(palette)
            } else {
                SingleColorDot(fallbackColor)
            }
        },
        onClick = onClick,
    )
}

