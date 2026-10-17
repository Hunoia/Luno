package hunoia.luno.ui.settings.action

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.bridge.ui.findActivity
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.MiniWindowSettings
import hunoia.luno.config.model.WindowBounds
import hunoia.luno.config.model.miniWindowSettings
import hunoia.luno.config.model.withMiniWindowSettings
import hunoia.luno.domain.tune.TuneConfig
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
 *
 * ## 完全接管整屏
 *
 * 藏掉状态栏和导航栏，预览铺满整块屏幕，控件悬浮在预览底部 —— 与 GestureEVO 的
 * `FreeFormSettingsActivity` + `ResizableRectangleView` 同一形态：那个 view 是
 * `CoordinatorLayout` 里 `match_parent` + `elevation=10dp` 的兄弟节点，盖在所有设置卡片和
 * Toolbar 之上，配合 `enableFullScreen()` 接管整屏。
 *
 * 因此这里**没有 TopBar**，系统返回键也不可靠（整屏浮层盖着），出口只有预览底部的
 * 【完成】/【取消】。这就是 GestureEVO 把退出按钮画进预览里的原因 —— 它连「取消」都没有，
 * 因为拖动过程中就已经实时落盘了；Luno 保留「先改后存」所以两个按钮都要。
 *
 * ## 坐标 1:1 对应真实屏幕
 *
 * 预览铺满整屏，所以 x/y 比例直接就是运行时的屏幕比例（`Launcher.makeActivityOptions`
 * 里 `realSw * settings.x` 那套），不需要任何缩放映射。
 *
 * ## 越界被 clamp 住
 *
 * 拖拽限制在 0..1 内，与运行时 `makeActivityOptions` 的 `coerceIn(0f, 1f)` 一致。
 * 预览说什么真机就是什么，不会出现「拖出去了但弹回来」。
 *
 * ## 竖屏横屏各一套，跟随系统转屏
 *
 * 编辑的是 [MiniWindowSettings] 里当前 [orientation] 对应的那套。页面**不锁方向**，
 * 系统转屏时本函数重组、切到另一套，未保存的改动随重组丢失（等价于没点【完成】）。
 *
 * 两个入口共用本内容组件：动作设置页的独立路由页，以及动作库里选「小窗」参数时的全屏
 * 对话框。后者要拿到对话框自己的 Window 才能隐藏系统栏，靠 [DialogWindowProvider]。
 */
@Composable
fun MiniWindowAdjustContent(
    initial: MiniWindowSettings,
    onCommit: (MiniWindowSettings) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ImmersiveSystemBars()

    val orientation = LocalConfiguration.current.orientation
    val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
    // 把手命中半径要在 composable 作用域里换算成 px：下面的回调是非 composable 的普通
    // lambda，那里面拿不到 Density。
    val handleTolerancePx = with(LocalDensity.current) { HANDLE_TOLERANCE.toPx() }

    var pending by remember(initial, orientation) { mutableStateOf(initial.forOrientation(orientation)) }
    var screenPx by remember { mutableStateOf(IntSize.Zero) }
    var dragKind by remember { mutableStateOf(HitKind.None) }
    var grabNorm by remember { mutableStateOf(Offset.Zero) }
    var startRect by remember { mutableStateOf(initial.forOrientation(orientation)) }

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

    /** 回到出厂默认：取当前方向那套（[MiniWindowSettings] 的默认值本身就是分方向的）。 */
    fun defaultBounds(): WindowBounds =
        MiniWindowSettings().forOrientation(orientation)

    fun nudge(dx: Float, dy: Float, dw: Float, dh: Float) {
        pending = pending.copy(
            x = (pending.x + dx).coerceIn(0f, 1f),
            y = (pending.y + dy).coerceIn(0f, 1f),
            // 上限 1.5 是原有行为：小窗允许比屏幕还宽（不是全屏，是浮窗），与运行时的
            // coerceIn(0.2f, 1.5f) 对齐。别收紧成 1f，那会让这段区间再也够不到。
            width = (pending.width + dw).coerceAtLeast(MIN_SIZE).coerceAtMost(1.5f),
            height = (pending.height + dh).coerceAtLeast(MIN_SIZE).coerceAtMost(1.5f),
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // 半透明压暗：底下是设置页（不是有意义的内容），压暗后矩形才跳得出来，
            // 同时保留「这就是整块屏幕」的感觉。GestureEVO 那层是完全透明的。
            .background(Color.Black.copy(alpha = 0.6f))
            .onSizeChanged { screenPx = IntSize(it.width, it.height) }
            .pointerInput(screenPx, orientation) {
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
                drawCircle(color = Color(0xFF1A73E8), radius = HANDLE_RADIUS.toPx(), center = h)
            }
        }

        Text(
            text = stringResource(
                if (isLandscape) R.string.mini_window_landscape_hint else R.string.mini_window_portrait_hint,
            ),
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.75f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp, start = 16.dp, end = 16.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // 横屏下不限宽会占满整屏，把矩形挤到一边、按钮还压在把手上。封顶后居中。
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.mini_window_adjust_hint,
                    (pending.x * 100).roundToInt(),
                    (pending.y * 100).roundToInt(),
                    (pending.width * 100).roundToInt(),
                    (pending.height * 100).roundToInt(),
                ),
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Column(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ArrowGroup(title = stringResource(R.string.mini_window_position)) {
                        StepButton(Icons.Default.KeyboardArrowLeft) { nudge(-STEP, 0f, 0f, 0f) }
                        StepButton(Icons.Default.KeyboardArrowRight) { nudge(STEP, 0f, 0f, 0f) }
                        StepButton(Icons.Default.KeyboardArrowUp) { nudge(0f, -STEP, 0f, 0f) }
                        StepButton(Icons.Default.KeyboardArrowDown) { nudge(0f, STEP, 0f, 0f) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ArrowGroup(title = stringResource(R.string.width)) {
                        StepButton(Icons.Default.Remove) { nudge(0f, 0f, -STEP, 0f) }
                        StepButton(Icons.Default.Add) { nudge(0f, 0f, STEP, 0f) }
                    }
                    ArrowGroup(title = stringResource(R.string.height)) {
                        StepButton(Icons.Default.Remove) { nudge(0f, 0f, 0f, -STEP) }
                        StepButton(Icons.Default.Add) { nudge(0f, 0f, 0f, STEP) }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = { pending = defaultBounds() },
                ) { Text(stringResource(R.string.mini_window_default), color = Color.White) }
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = onCancel,
                ) { Text(stringResource(R.string.cancel), color = Color.White) }
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onCommit(initial.withOrientation(orientation, pending)) },
                ) { Text(stringResource(R.string.mini_window_done)) }
            }
        }
    }
}

/**
 * 藏掉状态栏与导航栏，离开时恢复。
 *
 * 取 Window 优先用 [DialogWindowProvider]（对话框入口拿的是对话框自己的 Window），
 * 退回 Activity 的 Window。找不到就什么都不做 —— 页面仍然可用，只是没隐藏系统栏。
 */
@Composable
private fun ImmersiveSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view as? DialogWindowProvider)?.window
            ?: view.context.findActivity()?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

/**
 * 动作设置页 →「小窗」行的独立页面：自己读配置、自己落盘。
 *
 * 不套 LunoScreen：调整态是整屏接管，没有 TopBar，也就没有返回按钮可点 ——
 * 出口是预览底部的【完成】/【取消】，与 GestureEVO 同一形态。
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
private fun ArrowGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.75f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { content() }
    }
}

@Composable
private fun StepButton(icon: ImageVector, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
        )
    }
}