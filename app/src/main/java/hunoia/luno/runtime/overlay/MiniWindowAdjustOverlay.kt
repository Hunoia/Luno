package hunoia.luno.runtime.overlay

import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import hunoia.luno.BuildConfig
import hunoia.luno.R
import hunoia.luno.bridge.ui.applyOverlayViewTreeOwners
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.config.model.MiniWindowSettings
import hunoia.luno.runtime.GestureHost
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 全屏 overlay 预览并拖拽调整小窗位置/大小（真实屏幕）。
 *
 * 拖动/箭头调整期间只更新内存中的 [MiniWindowSettings]，点【完成】才经 [onCommit]
 * 落盘；【取消】丢弃改动直接关闭；【默认】按 0.7 屏宽 + 16:10 的原始默认布局重置。
 */
class MiniWindowAdjustOverlay(private val host: GestureHost) {

    private var overlayView: View? = null
    private var onCommit: ((MiniWindowSettings) -> Unit)? = null
    private var onDismiss: (() -> Unit)? = null

    /** 打开 overlay。[onCommit] 在点【完成】时调用；[onDismiss] 在 overlay 关闭（完成/取消）时调用。 */
    fun show(
        initial: MiniWindowSettings,
        commit: (MiniWindowSettings) -> Unit,
        onDismiss: () -> Unit = {},
    ) {
        if (overlayView != null) return
        onCommit = commit
        this.onDismiss = onDismiss
        val wm = host.context.windowManager()
        val lp = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.RGBA_8888
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                trustedOverlayFlag()
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            x = 0
            y = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            @SuppressLint("RtlHardcoded")
            gravity = Gravity.LEFT or Gravity.TOP
        }
        if (BuildConfig.DEBUG) {
            android.util.Log.i("LunoLauncher", "mini window adjust: show initial=$initial")
        }
        val composeView = ComposeView(host.context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            applyOverlayViewTreeOwners(host)
            setContent {
                MiniWindowAdjustScreen(
                    initial = initial,
                    onCancel = { close() },
                    onCommit = { result ->
                        onCommit?.invoke(result)
                        close()
                    },
                )
            }
        }
        if (!wm.safeAddView(composeView, lp)) {
            android.util.Log.e("LunoLauncher", "mini window adjust: addView failed type=touch_trusted")
            return
        }
        overlayView = composeView
    }

    /** Android 12+ 无障碍 overlay 需要此标志，否则触摸会被系统拦截。 */
    private fun trustedOverlayFlag(): Int = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) 0x300000 else 0

    fun close() {
        val view = overlayView ?: return
        val dismiss = onDismiss
        onCommit = null
        onDismiss = null
        overlayView = null
        val wm = host.context.windowManager()
        runCatching { wm.removeView(view) }
        dismiss?.invoke()
    }
}

private const val MIN_SIZE = 0.2f
private const val STEP = 0.01f

private enum class HitKind {
    None, Move, TopLeft, TopRight, BottomLeft, BottomRight, Top, Bottom, Left, Right
}

@Composable
private fun MiniWindowAdjustScreen(
    initial: MiniWindowSettings,
    onCancel: () -> Unit,
    onCommit: (MiniWindowSettings) -> Unit,
) {
    var pending by remember { mutableStateOf(initial) }
    var screenPx by remember { mutableStateOf(IntSize.Zero) }
    var dragKind by remember { mutableStateOf(HitKind.None) }
    var grabNorm by remember { mutableStateOf(Offset.Zero) }
    var startRect by remember { mutableStateOf(initial) }

    fun rectPx(settings: MiniWindowSettings): Rect {
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

    fun applyDrag(start: MiniWindowSettings, kind: HitKind, norm: Offset, grab: Offset): MiniWindowSettings {
        if (kind == HitKind.None || screenPx.width <= 0 || screenPx.height <= 0) return start
        fun clampV(v: Float) = v.coerceIn(0f, 1f)
        return when (kind) {
            HitKind.None -> start
            HitKind.Move -> {
                val visibleW = start.width.coerceAtLeast(0.2f)
                val visibleH = start.height.coerceAtLeast(0.2f)
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

    fun defaultRect(): MiniWindowSettings {
        val sw = screenPx.width.coerceAtLeast(1)
        val sh = screenPx.height.coerceAtLeast(1)
        val height = (sw / 0.625f) / sh
        return MiniWindowSettings(x = 0.15f, y = (0.5f - height / 2f).coerceIn(0f, 1f), width = 1f, height = height)
    }

    fun nudge(dx: Float, dy: Float, dw: Float, dh: Float) {
        val next = pending.copy(
            x = (pending.x + dx).coerceIn(0f, 1f),
            y = (pending.y + dy).coerceIn(0f, 1f),
            width = (pending.width + dw).coerceAtLeast(MIN_SIZE).coerceAtMost(1.5f),
            height = (pending.height + dh).coerceAtLeast(MIN_SIZE).coerceAtMost(1.5f),
        )
        pending = next
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenPx = IntSize(it.width, it.height) }
            .pointerInput(screenPx) {
                val hitPx = 28.dp.toPx()
                detectDragGestures(
                    onDragStart = { offset ->
                        val kind = hitKindAt(offset, hitPx)
                        if (kind != HitKind.None) {
                            dragKind = kind
                            startRect = pending
                            grabNorm = Offset(offset.x / screenPx.width, offset.y / screenPx.height)
                        }
                    },
                    onDrag = { change, _ ->
                        val norm = Offset(change.position.x / screenPx.width, change.position.y / screenPx.height)
                        pending = applyDrag(startRect, dragKind, norm, grabNorm)
                    },
                    onDragEnd = { dragKind = HitKind.None },
                    onDragCancel = { dragKind = HitKind.None },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = Color.Black.copy(alpha = 0.35f))
            val px = rectPx(pending)
            drawRect(
                color = Color.White.copy(alpha = 0.18f),
                topLeft = Offset(px.left, px.top),
                size = androidx.compose.ui.geometry.Size(px.width, px.height),
            )
            drawRect(
                color = Color.White,
                topLeft = Offset(px.left, px.top),
                size = androidx.compose.ui.geometry.Size(px.width, px.height),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
            )
            val handles = listOf(
                Offset(px.left, px.top), Offset(px.right, px.top),
                Offset(px.left, px.bottom), Offset(px.right, px.bottom),
                Offset((px.left + px.right) / 2f, px.top), Offset((px.left + px.right) / 2f, px.bottom),
                Offset(px.left, (px.top + px.bottom) / 2f), Offset(px.right, (px.top + px.bottom) / 2f),
            )
            for (h in handles) {
                drawCircle(color = Color.White, radius = 10.dp.toPx(), center = h)
                drawCircle(color = Color(0xFF1A73E8), radius = 7.dp.toPx(), center = h)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val effWidth = pending.width.coerceAtMost((1f - pending.x).coerceIn(0f, 1f))
            val effHeight = pending.height.coerceAtMost((1f - pending.y).coerceIn(0f, 1f))
            Text(
                text = stringResource(
                    R.string.mini_window_adjust_hint,
                    (pending.x * 100).roundToInt(),
                    (pending.y * 100).roundToInt(),
                    (effWidth * 100).roundToInt(),
                    (effHeight * 100).roundToInt(),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.CenterHorizontally)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )

            Column(
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.CenterHorizontally)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ArrowGroup(title = stringResource(R.string.mini_window_position)) {
                        StepButton(Icons.Default.KeyboardArrowLeft) { nudge(-STEP, 0f, 0f, 0f) }
                        StepButton(Icons.Default.KeyboardArrowRight) { nudge(STEP, 0f, 0f, 0f) }
                        StepButton(Icons.Default.KeyboardArrowUp) { nudge(0f, -STEP, 0f, 0f) }
                        StepButton(Icons.Default.KeyboardArrowDown) { nudge(0f, STEP, 0f, 0f) }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
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
                    onClick = { pending = defaultRect() },
                ) { Text(stringResource(R.string.mini_window_default), color = Color.White) }
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = onCancel,
                ) { Text(stringResource(R.string.cancel), color = Color.White) }
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onCommit(pending) },
                ) { Text(stringResource(R.string.mini_window_done)) }
            }
        }
    }
}

@Composable
private fun ArrowGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.8f),
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
