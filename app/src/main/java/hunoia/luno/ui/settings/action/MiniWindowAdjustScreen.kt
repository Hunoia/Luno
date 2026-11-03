package hunoia.luno.ui.settings.action

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.MiniWindowSettings
import hunoia.luno.config.model.WindowBounds
import hunoia.luno.config.model.miniWindowSettings
import hunoia.luno.config.model.withMiniWindowSettings
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.LargeShape
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

private const val MIN_SIZE = 0.2f
private const val STEP = 0.01f
private val HANDLE_TOLERANCE = 40.dp
private val RECT_CORNER = 16.dp
private val HANDLE_RADIUS = 7.dp

private enum class HitKind {
    None, Move, TopLeft, TopRight, BottomLeft, BottomRight, Top, Bottom, Left, Right
}

/**
 * 小窗位置/大小的调整界面。
 * 整屏接管：隐藏系统栏、无 TopBar，系统返回不可靠，出口只有预览底部的【完成】/【取消】。
 * 预览铺满整屏，比例与运行时 `makeActivityOptions` 1:1 对应，无需缩放映射；拖拽同样 clamp 到 0..1。
 * 运行时按方向抵消 HyperOS 的 freeformScale（竖屏 0.7 / 横屏 0.56），两边预览都等于实际。
 * 不锁方向，编辑的是当前 [orientation] 那套；转屏重组会丢弃未保存改动。
 * 两个入口共用本内容组件：路由页与全屏对话框，后者靠 [DialogWindowProvider] 取自己的 Window。
 */
@Composable
fun MiniWindowAdjustContent(
    initial: MiniWindowSettings,
    onCommit: (MiniWindowSettings) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val orientation = LocalConfiguration.current.orientation
    val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
    // 命中半径在这里换算成 px：下面的拖拽回调不是 composable，拿不到 Density。
    val handleTolerancePx = with(LocalDensity.current) { HANDLE_TOLERANCE.toPx() }

    var pending by remember(initial, isLandscape) { mutableStateOf(initial.forOrientation(isLandscape)) }
    var screenPx by remember { mutableStateOf(IntSize.Zero) }
    var dragKind by remember { mutableStateOf(HitKind.None) }
    var grabNorm by remember { mutableStateOf(Offset.Zero) }
    var startRect by remember { mutableStateOf(initial.forOrientation(isLandscape)) }

    fun rectPx(settings: WindowBounds): Rect {
        val sw = screenPx.width
        val sh = screenPx.height
        return Rect(
            left = settings.x * sw,
            top = settings.y * sh,
            right = (settings.x + settings.width) * sw,
            bottom = (settings.y + settings.height) * sh,
        )
    }

    fun hitKindAt(offset: Offset, hit: Float): HitKind {
        if (screenPx.width <= 0 || screenPx.height <= 0) return HitKind.None
        val px = rectPx(pending)
        val handles = listOf(
            Offset(px.left, px.top) to HitKind.TopLeft,
            Offset(px.right, px.top) to HitKind.TopRight,
            Offset(px.left, px.bottom) to HitKind.BottomLeft,
            Offset(px.right, px.bottom) to HitKind.BottomRight,
            Offset((px.left + px.right) / 2f, px.top) to HitKind.Top,
            Offset((px.left + px.right) / 2f, px.bottom) to HitKind.Bottom,
            Offset(px.left, (px.top + px.bottom) / 2f) to HitKind.Left,
            Offset(px.right, (px.top + px.bottom) / 2f) to HitKind.Right,
        )
        for ((point, kind) in handles) {
            if (offset.x in (point.x - hit)..(point.x + hit) &&
                offset.y in (point.y - hit)..(point.y + hit)
            ) return kind
        }
        if (px.contains(offset)) return HitKind.Move
        return HitKind.None
    }

    fun applyDrag(start: WindowBounds, kind: HitKind, norm: Offset, grab: Offset): WindowBounds {
        if (kind == HitKind.None || screenPx.width <= 0 || screenPx.height <= 0) return start
        fun clampV(v: Float) = v.coerceIn(0f, 1f)
        return when (kind) {
            HitKind.None -> start
            HitKind.Move -> {
                val visibleW = start.width.coerceAtLeast(MIN_SIZE)
                val visibleH = start.height.coerceAtLeast(MIN_SIZE)
                val dx = norm.x - grab.x
                val dy = norm.y - grab.y
                val x = (start.x + dx).coerceIn(0f, max(0f, 1f - visibleW))
                val y = (start.y + dy).coerceIn(0f, max(0f, 1f - visibleH))
                start.copy(x = x, y = y)
            }
            HitKind.TopLeft -> {
                var l = norm.x
                var t = norm.y
                if (start.x + start.width - l < MIN_SIZE) l = start.x + start.width - MIN_SIZE
                if (start.y + start.height - t < MIN_SIZE) t = start.y + start.height - MIN_SIZE
                l = clampV(l)
                t = clampV(t)
                start.copy(x = l, y = t, width = start.x + start.width - l, height = start.y + start.height - t)
            }
            HitKind.TopRight -> {
                var r = norm.x
                var t = norm.y
                if (r - start.x < MIN_SIZE) r = start.x + MIN_SIZE
                if (start.y + start.height - t < MIN_SIZE) t = start.y + start.height - MIN_SIZE
                r = r.coerceAtLeast(0f)
                t = clampV(t)
                start.copy(x = start.x, y = t, width = r - start.x, height = start.y + start.height - t)
            }
            HitKind.BottomLeft -> {
                var l = norm.x
                var b = norm.y
                if (start.x + start.width - l < MIN_SIZE) l = start.x + start.width - MIN_SIZE
                if (b - start.y < MIN_SIZE) b = start.y + MIN_SIZE
                l = clampV(l)
                b = b.coerceAtLeast(0f)
                start.copy(x = l, y = start.y, width = start.x + start.width - l, height = b - start.y)
            }
            HitKind.BottomRight -> {
                var r = norm.x
                var b = norm.y
                if (r - start.x < MIN_SIZE) r = start.x + MIN_SIZE
                if (b - start.y < MIN_SIZE) b = start.y + MIN_SIZE
                r = r.coerceAtLeast(0f)
                b = b.coerceAtLeast(0f)
                start.copy(x = start.x, y = start.y, width = r - start.x, height = b - start.y)
            }
            HitKind.Top -> {
                var t = norm.y
                if (start.y + start.height - t < MIN_SIZE) t = start.y + start.height - MIN_SIZE
                t = clampV(t)
                start.copy(y = t, height = start.y + start.height - t)
            }
            HitKind.Bottom -> {
                val b = (norm.y.coerceAtLeast(start.y + MIN_SIZE)).coerceAtLeast(0f)
                start.copy(height = b - start.y)
            }
            HitKind.Left -> {
                var l = norm.x
                if (start.x + start.width - l < MIN_SIZE) l = start.x + start.width - MIN_SIZE
                l = clampV(l)
                start.copy(x = l, width = start.x + start.width - l)
            }
            HitKind.Right -> {
                val r = (norm.x.coerceAtLeast(start.x + MIN_SIZE)).coerceAtLeast(0f)
                start.copy(width = r - start.x)
            }
        }
    }

    /** 出厂默认取当前方向那套（[MiniWindowSettings] 的默认值本身分方向）。 */
    fun defaultBounds(): WindowBounds =
        MiniWindowSettings().forOrientation(isLandscape)

    fun nudge(dx: Float, dy: Float, dw: Float, dh: Float) {
        pending = pending.copy(
            x = (pending.x + dx).coerceIn(0f, 1f),
            y = (pending.y + dy).coerceIn(0f, 1f),
            // 上限 1.5：小窗允许比屏幕还宽，与运行时 coerceIn(0.2f, 1.5f) 对齐；
            // 收紧成 1f 会让这段区间永远够不到。
            width = (pending.width + dw).coerceAtLeast(MIN_SIZE).coerceAtMost(1.5f),
            height = (pending.height + dh).coerceAtLeast(MIN_SIZE).coerceAtMost(1.5f),
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // 压暗底下的设置页（没有意义的内容），矩形才跳得出来，同时保留「整块屏幕」的感觉。
            .background(Color.Black.copy(alpha = 0.6f))
            .onSizeChanged { screenPx = IntSize(it.width, it.height) }
            .pointerInput(screenPx, isLandscape) {
                val w = screenPx.width.coerceAtLeast(1)
                val h = screenPx.height.coerceAtLeast(1)
                val tolerancePx = handleTolerancePx
                detectDragGestures(
                    onDragStart = { offset ->
                        val kind = hitKindAt(offset, tolerancePx)
                        if (kind != HitKind.None) {
                            dragKind = kind
                            startRect = pending
                            grabNorm = Offset(offset.x / w, offset.y / h)
                        }
                    },
                    onDrag = { change, _ ->
                        pending = applyDrag(
                            startRect,
                            dragKind,
                            Offset(change.position.x / w, change.position.y / h),
                            grabNorm,
                        )
                    },
                    onDragEnd = { dragKind = HitKind.None },
                    onDragCancel = { dragKind = HitKind.None },
                )
            },
    ) {
        val handleColor = MaterialTheme.colorScheme.primary
        val textMeasurer = rememberTextMeasurer()
        Canvas(modifier = Modifier.fillMaxSize()) {
            val px = rectPx(pending)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.18f),
                topLeft = Offset(px.left, px.top),
                size = Size(px.width, px.height),
                cornerRadius = CornerRadius(RECT_CORNER.toPx()),
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(px.left, px.top),
                size = Size(px.width, px.height),
                cornerRadius = CornerRadius(RECT_CORNER.toPx()),
                style = Stroke(width = 2.dp.toPx()),
            )
            val handles = listOf(
                Offset(px.left, px.top), Offset(px.right, px.top),
                Offset(px.left, px.bottom), Offset(px.right, px.bottom),
                Offset((px.left + px.right) / 2f, px.top), Offset((px.left + px.right) / 2f, px.bottom),
                Offset(px.left, (px.top + px.bottom) / 2f), Offset(px.right, (px.top + px.bottom) / 2f),
            )
            for (h in handles) {
                drawCircle(color = Color.White, radius = HANDLE_RADIUS.toPx() + 2.dp.toPx(), center = h)
                drawCircle(color = handleColor, radius = HANDLE_RADIUS.toPx(), center = h)
            }
            // 各边边距：矩形内侧边中点显示到屏幕对应边的距离（dp）。
            // 方向跟随边：左/右边距竖直（旋转90°），上/下水平；往内放避开边中点拖拽点。
            val labelStyle = TextStyle(color = Color.White, fontSize = 12.sp)
            fun edgeLabel(text: String, center: Offset, rotation: Float = 0f) {
                val layout = textMeasurer.measure(text, labelStyle)
                val topLeft = Offset(
                    center.x - layout.size.width / 2f,
                    center.y - layout.size.height / 2f,
                )
                if (rotation == 0f) {
                    drawText(textLayoutResult = layout, topLeft = topLeft)
                } else {
                    rotate(rotation, center) {
                        drawText(textLayoutResult = layout, topLeft = topLeft)
                    }
                }
            }
            val inset = 20.dp.toPx()
            val cx = (px.left + px.right) / 2f
            val cy = (px.top + px.bottom) / 2f
            val leftDp = (pending.x * screenPx.width / density).roundToInt()
            val topDp = (pending.y * screenPx.height / density).roundToInt()
            val rightDp = ((1f - pending.x - pending.width).coerceAtLeast(0f) * screenPx.width / density).roundToInt()
            val bottomDp = ((1f - pending.y - pending.height).coerceAtLeast(0f) * screenPx.height / density).roundToInt()
            edgeLabel("${leftDp}dp", Offset(px.left + inset, cy), rotation = -90f)
            edgeLabel("${topDp}dp", Offset(cx, px.top + inset))
            edgeLabel("${rightDp}dp", Offset(px.right - inset, cy), rotation = 90f)
            edgeLabel("${bottomDp}dp", Offset(cx, px.bottom - inset))
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // 卡片背景按按钮实际占用收缩（wrapContent），不再撑满屏宽，横屏也居中贴合。
                .widthIn(max = 460.dp)
                .padding(12.dp)
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), LargeShape)
                .background(Color(0xFF1C1B1F).copy(alpha = 0.92f), LargeShape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
        ) {
            Column(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ArrowGroup {
                        StepButton(Icons.Default.KeyboardArrowLeft) { nudge(-STEP, 0f, 0f, 0f) }
                        StepButton(Icons.Default.KeyboardArrowRight) { nudge(STEP, 0f, 0f, 0f) }
                        StepButton(Icons.Default.KeyboardArrowUp) { nudge(0f, -STEP, 0f, 0f) }
                        StepButton(Icons.Default.KeyboardArrowDown) { nudge(0f, STEP, 0f, 0f) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    ArrowGroup {
                        StepButton(Icons.Default.Remove) { nudge(0f, 0f, -STEP, 0f) }
                        StepButton(Icons.Default.Add) { nudge(0f, 0f, STEP, 0f) }
                    }
                    ArrowGroup {
                        StepButton(Icons.Default.Remove) { nudge(0f, 0f, 0f, -STEP) }
                        StepButton(Icons.Default.Add) { nudge(0f, 0f, 0f, STEP) }
                    }
                }
            }

            Row(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.Center,
            ) {
                StepButton(
                    icon = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.mini_window_default),
                    onClick = { pending = defaultBounds() },
                )
                Spacer(Modifier.width(8.dp))
                StepButton(
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cancel),
                    onClick = onCancel,
                )
                Spacer(Modifier.width(8.dp))
                StepButton(
                    icon = Icons.Default.Check,
                    contentDescription = stringResource(R.string.mini_window_done),
                    tint = handleColor,
                    onClick = { onCommit(initial.withOrientation(isLandscape, pending)) },
                )
            }
        }
    }
}

/**
 * 动作设置页「小窗」行的独立页面：自己读配置、自己落盘。
 * 整屏接管且无 TopBar，故不套 LunoScreen，出口是预览底部的【完成】/【取消】。
 */
@Composable
fun MiniWindowAdjustScreen(
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val advanced by TuneConfig.advanced.collectAsStateWithLifecycle(
        initialValue = AdvancedSettings()
    )
    MiniWindowAdjustContent(
        initial = advanced.miniWindowSettings(),
        onCommit = { next ->
            scope.launch { TuneConfig.updateAdvanced { it.withMiniWindowSettings(next) } }
            onBack()
        },
        onCancel = onBack,
    )
}

@Composable
private fun ArrowGroup(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { content() }
}

@Composable
private fun StepButton(
    icon: ImageVector,
    contentDescription: String? = null,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.background(Color.White.copy(alpha = 0.10f), CircleShape),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}

