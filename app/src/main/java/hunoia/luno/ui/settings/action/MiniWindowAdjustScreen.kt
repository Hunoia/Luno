package hunoia.luno.ui.settings.action

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.IntOffset
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
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val MIN_SIZE = 0.2f
private const val STEP = 0.01f
private val HANDLE_TOLERANCE = 40.dp
private val RECT_CORNER = 16.dp
private val HANDLE_RADIUS = 7.dp

/** 框内微调按钮：小圆钮跟随窗口框，只吃点击，拖动仍交给预览层。 */
private val STEP_BUTTON = 34.dp
private val STEP_BUTTON_ICON = 18.dp
private val STEP_GAP = 4.dp
private val STEP_MARGIN = 6.dp

private enum class HitKind {
    None, Move, TopLeft, TopRight, BottomLeft, BottomRight, Top, Bottom, Left, Right
}

/** 微调方向：移动整框，或把某条边往外/往里推。 */
private enum class StepKind {
    MoveLeft, MoveRight, MoveUp, MoveDown,
    LeftOut, LeftIn, RightOut, RightIn, TopOut, TopIn, BottomOut, BottomIn,
}

private data class StepSpec(val kind: StepKind, val center: Offset)

/** 微调按钮布局 + 每边 dp 标注的避让内边距（按钮翻到框内时把标注往里推）。 */
private data class StepLayout(
    val specs: List<StepSpec>,
    val leftLabelInset: Float,
    val topLabelInset: Float,
    val rightLabelInset: Float,
    val bottomLabelInset: Float,
)

/**
 * 四边中点各一对「外推 / 内收」小圆钮（优先框外，空间不足翻框内），
 * 框中心一组四向移动钮。全部跟随窗口框与屏幕尺寸重算。
 */
private fun buildStepLayout(
    px: Rect,
    screenWidthPx: Int,
    screenHeightPx: Int,
    buttonPx: Float,
    gapPx: Float,
    marginPx: Float,
    labelInsetPx: Float,
    labelInsetShiftedPx: Float,
): StepLayout {
    if (screenWidthPx <= 0 || screenHeightPx <= 0 || px.width <= 0f || px.height <= 0f) {
        return StepLayout(
            specs = emptyList(),
            leftLabelInset = labelInsetPx,
            topLabelInset = labelInsetPx,
            rightLabelInset = labelInsetPx,
            bottomLabelInset = labelInsetPx,
        )
    }
    val half = buttonPx / 2f
    val offset = half + marginPx
    val cx = (px.left + px.right) / 2f
    val cy = (px.top + px.bottom) / 2f
    val pairGap = (buttonPx + gapPx) / 2f
    val specs = mutableListOf<StepSpec>()

    // 左 / 右：一对按钮竖排。
    val leftOutside = px.left >= buttonPx + marginPx * 2f
    val leftX = if (leftOutside) px.left - offset else px.left + offset
    specs += StepSpec(StepKind.LeftOut, Offset(leftX, cy - pairGap))
    specs += StepSpec(StepKind.LeftIn, Offset(leftX, cy + pairGap))

    val rightOutside = screenWidthPx - px.right >= buttonPx + marginPx * 2f
    val rightX = if (rightOutside) px.right + offset else px.right - offset
    specs += StepSpec(StepKind.RightOut, Offset(rightX, cy - pairGap))
    specs += StepSpec(StepKind.RightIn, Offset(rightX, cy + pairGap))

    // 上 / 下：一对按钮横排。
    val topOutside = px.top >= buttonPx + marginPx * 2f
    val topY = if (topOutside) px.top - offset else px.top + offset
    specs += StepSpec(StepKind.TopOut, Offset(cx - pairGap, topY))
    specs += StepSpec(StepKind.TopIn, Offset(cx + pairGap, topY))

    val bottomOutside = screenHeightPx - px.bottom >= buttonPx + marginPx * 2f
    val bottomY = if (bottomOutside) px.bottom + offset else px.bottom - offset
    specs += StepSpec(StepKind.BottomOut, Offset(cx - pairGap, bottomY))
    specs += StepSpec(StepKind.BottomIn, Offset(cx + pairGap, bottomY))

    // 框中心：四向移动盘，跟随窗口框。
    specs += StepSpec(StepKind.MoveLeft, Offset(cx - buttonPx - gapPx, cy))
    specs += StepSpec(StepKind.MoveRight, Offset(cx + buttonPx + gapPx, cy))
    specs += StepSpec(StepKind.MoveUp, Offset(cx, cy - buttonPx - gapPx))
    specs += StepSpec(StepKind.MoveDown, Offset(cx, cy + buttonPx + gapPx))

    return StepLayout(
        specs = specs,
        leftLabelInset = if (leftOutside) labelInsetPx else labelInsetShiftedPx,
        topLabelInset = if (topOutside) labelInsetPx else labelInsetShiftedPx,
        rightLabelInset = if (rightOutside) labelInsetPx else labelInsetShiftedPx,
        bottomLabelInset = if (bottomOutside) labelInsetPx else labelInsetShiftedPx,
    )
}

private fun StepKind.icon(): ImageVector = when (this) {
    StepKind.MoveLeft, StepKind.LeftOut, StepKind.RightIn -> Icons.Default.KeyboardArrowLeft
    StepKind.MoveRight, StepKind.LeftIn, StepKind.RightOut -> Icons.Default.KeyboardArrowRight
    StepKind.MoveUp, StepKind.TopOut, StepKind.BottomIn -> Icons.Default.KeyboardArrowUp
    StepKind.MoveDown, StepKind.TopIn, StepKind.BottomOut -> Icons.Default.KeyboardArrowDown
}

private fun StepKind.labelRes(): Int = when (this) {
    StepKind.MoveLeft, StepKind.LeftOut, StepKind.RightIn -> R.string.mini_window_nudge_left
    StepKind.MoveRight, StepKind.LeftIn, StepKind.RightOut -> R.string.mini_window_nudge_right
    StepKind.MoveUp, StepKind.TopOut, StepKind.BottomIn -> R.string.mini_window_nudge_up
    StepKind.MoveDown, StepKind.TopIn, StepKind.BottomOut -> R.string.mini_window_nudge_down
}

/**
 * 小窗位置/大小的调整界面。
 * 整屏接管且无 TopBar：出口是顶部那排透明圆形按钮（取消 / 重置 / 确认）。
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
    // 命中半径与微调按钮尺寸都在这里换算成 px：下面的拖拽回调不是 composable，拿不到 Density。
    val handleTolerancePx = with(LocalDensity.current) { HANDLE_TOLERANCE.toPx() }
    val stepButtonPx = with(LocalDensity.current) { STEP_BUTTON.toPx() }
    val stepGapPx = with(LocalDensity.current) { STEP_GAP.toPx() }
    val stepMarginPx = with(LocalDensity.current) { STEP_MARGIN.toPx() }
    val labelInsetPx = with(LocalDensity.current) { 20.dp.toPx() }
    val labelInsetShiftedPx = with(LocalDensity.current) { 56.dp.toPx() }

    // 状态句柄必须稳定：配置异步到达时若换新 state，pointerInput 闭包仍握着旧的，
    // 拖动会写进废弃状态（表现为窗口被打回初始大小）。转屏仍按 isLandscape 重置草稿。
    var pending by remember { mutableStateOf(initial.forOrientation(isLandscape)) }
    LaunchedEffect(initial, isLandscape) { pending = initial.forOrientation(isLandscape) }
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

    fun moveBy(dx: Float, dy: Float) {
        pending = pending.copy(
            x = (pending.x + dx).coerceIn(0f, (1f - pending.width).coerceAtLeast(0f)),
            y = (pending.y + dy).coerceIn(0f, (1f - pending.height).coerceAtLeast(0f)),
        )
    }

    /** 微调一步：移动整框，或推某条边；复用 [applyDrag] 保证最小尺寸与夹取一致。 */
    fun stepBy(kind: StepKind) {
        when (kind) {
            StepKind.MoveLeft -> moveBy(-STEP, 0f)
            StepKind.MoveRight -> moveBy(STEP, 0f)
            StepKind.MoveUp -> moveBy(0f, -STEP)
            StepKind.MoveDown -> moveBy(0f, STEP)
            StepKind.LeftOut -> pending = applyDrag(
                pending,
                HitKind.Left,
                Offset(pending.x - STEP, 0f),
                Offset.Zero,
            )
            StepKind.LeftIn -> pending = applyDrag(
                pending,
                HitKind.Left,
                Offset(pending.x + STEP, 0f),
                Offset.Zero,
            )
            StepKind.RightOut -> pending = applyDrag(
                pending,
                HitKind.Right,
                Offset(pending.x + pending.width + STEP, 0f),
                Offset.Zero,
            )
            StepKind.RightIn -> pending = applyDrag(
                pending,
                HitKind.Right,
                Offset(pending.x + pending.width - STEP, 0f),
                Offset.Zero,
            )
            StepKind.TopOut -> pending = applyDrag(
                pending,
                HitKind.Top,
                Offset(0f, pending.y - STEP),
                Offset.Zero,
            )
            StepKind.TopIn -> pending = applyDrag(
                pending,
                HitKind.Top,
                Offset(0f, pending.y + STEP),
                Offset.Zero,
            )
            StepKind.BottomOut -> pending = applyDrag(
                pending,
                HitKind.Bottom,
                Offset(0f, pending.y + pending.height + STEP),
                Offset.Zero,
            )
            StepKind.BottomIn -> pending = applyDrag(
                pending,
                HitKind.Bottom,
                Offset(0f, pending.y + pending.height - STEP),
                Offset.Zero,
            )
        }
    }

    /** 微调按钮不参与拖拽分类：按在按钮上拖动仍按「移动整框」处理。 */
    fun isOnStep(offset: Offset): Boolean {
        val layout = buildStepLayout(
            px = rectPx(pending),
            screenWidthPx = screenPx.width,
            screenHeightPx = screenPx.height,
            buttonPx = stepButtonPx,
            gapPx = stepGapPx,
            marginPx = stepMarginPx,
            labelInsetPx = labelInsetPx,
            labelInsetShiftedPx = labelInsetShiftedPx,
        )
        val hit = stepButtonPx / 2f + stepMarginPx
        return layout.specs.any {
            abs(offset.x - it.center.x) <= hit && abs(offset.y - it.center.y) <= hit
        }
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
                        // 空白处也要快照：否则 onDrag 会拿页面刚打开时的 startRect 把窗口打回初始大小。
                        // 空白处按下直接拖动整框（命中不到手柄/微调按钮时默认 Move）。
                        dragKind = if (isOnStep(offset)) {
                            HitKind.Move
                        } else {
                            hitKindAt(offset, tolerancePx).let {
                                if (it == HitKind.None) HitKind.Move else it
                            }
                        }
                        startRect = pending
                        grabNorm = Offset(offset.x / w, offset.y / h)
                    },
                    onDrag = { change, _ ->
                        if (dragKind != HitKind.None) {
                            pending = applyDrag(
                                startRect,
                                dragKind,
                                Offset(change.position.x / w, change.position.y / h),
                                grabNorm,
                            )
                        }
                    },
                    onDragEnd = { dragKind = HitKind.None },
                    onDragCancel = { dragKind = HitKind.None },
                )
            },
    ) {
        val handleColor = MaterialTheme.colorScheme.primary
        val textMeasurer = rememberTextMeasurer()
        val steps = buildStepLayout(
            px = rectPx(pending),
            screenWidthPx = screenPx.width,
            screenHeightPx = screenPx.height,
            buttonPx = stepButtonPx,
            gapPx = stepGapPx,
            marginPx = stepMarginPx,
            labelInsetPx = labelInsetPx,
            labelInsetShiftedPx = labelInsetShiftedPx,
        )
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
            val cx = (px.left + px.right) / 2f
            val cy = (px.top + px.bottom) / 2f
            val leftDp = (pending.x * screenPx.width / density).roundToInt()
            val topDp = (pending.y * screenPx.height / density).roundToInt()
            val rightDp = ((1f - pending.x - pending.width).coerceAtLeast(0f) * screenPx.width / density).roundToInt()
            val bottomDp = ((1f - pending.y - pending.height).coerceAtLeast(0f) * screenPx.height / density).roundToInt()
            edgeLabel("${leftDp}dp", Offset(px.left + steps.leftLabelInset, cy), rotation = -90f)
            edgeLabel("${topDp}dp", Offset(cx, px.top + steps.topLabelInset))
            edgeLabel("${rightDp}dp", Offset(px.right - steps.rightLabelInset, cy), rotation = 90f)
            edgeLabel("${bottomDp}dp", Offset(cx, px.bottom - steps.bottomLabelInset))
        }

        // 框内微调：跟随窗口框与空间自由翻转，只吃点击；拖动由预览层接管（见 isOnStep）。
        for (spec in steps.specs) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (spec.center.x - stepButtonPx / 2f).roundToInt(),
                            (spec.center.y - stepButtonPx / 2f).roundToInt(),
                        )
                    }
                    .size(STEP_BUTTON)
                    .background(Color.White.copy(alpha = 0.12f), CircleShape)
                    .clickable { stepBy(spec.kind) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = spec.kind.icon(),
                    contentDescription = stringResource(spec.kind.labelRes()),
                    tint = Color.White,
                    modifier = Modifier.size(STEP_BUTTON_ICON),
                )
            }
        }

        Box(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            AdjustTopBar(
                onCancel = onCancel,
                onReset = { pending = defaultBounds() },
                onConfirm = { onCommit(initial.withOrientation(isLandscape, pending)) },
                confirmTint = handleColor,
            )
        }
    }
}

/**
 * 动作设置页「小窗」行的独立页面：自己读配置、自己落盘。
 * 整屏接管且无 TopBar，出口是顶部那排透明圆形按钮。
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
