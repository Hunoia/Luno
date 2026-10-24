package hunoia.luno.runtime.overlay

import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import hunoia.luno.BuildConfig
import hunoia.luno.R
import hunoia.luno.bridge.ui.applyOverlayViewTreeOwners
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.runtime.GestureHost
import kotlin.math.roundToInt
import hunoia.luno.core.LOG_TAG

/**
 * 屏幕取点 overlay（动作库点击/长按坐标参数用）：全屏透明（不压暗，保证看清目标应用），
 * 轻点移动十字线，【确定】按 dp 回填（执行时按当时 density 转 px），【取消】丢弃。
 * 回填后坐标仍是普通数字参数，可再手改；状态栏等系统手势区点不到，用手填。
 */
class PointPickOverlay(private val host: GestureHost) {

    private companion object {
        /**
         * 无障碍 overlay 不带这个标志时，触摸会被系统拦掉（Android 12 起）。
         * `0x300000` 是两个 layout flag 的或，ROM 不接受拆分写法，只能照发。
         */
        const val TRUSTED_OVERLAY_FLAG = 0x300000
    }

    private var overlayView: View? = null
    private var onCommit: ((Int, Int) -> Unit)? = null
    private var onDismiss: (() -> Unit)? = null

    /** 打开 overlay。[onCommit] 在点【确定】时回调 dp 坐标；[onDismiss] 在关闭时调用。 */
    fun show(
        initialXdp: Int?,
        initialYdp: Int?,
        commit: (Int, Int) -> Unit,
        onDismiss: () -> Unit = {},
        onError: () -> Unit = {},
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
                TRUSTED_OVERLAY_FLAG
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            x = 0
            y = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            @SuppressLint("RtlHardcoded")
            gravity = Gravity.LEFT or Gravity.TOP
        }
        if (BuildConfig.DEBUG) {
            android.util.Log.i(LOG_TAG, "point pick: show initial=($initialXdp,$initialYdp)")
        }
        val composeView = ComposeView(host.context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            applyOverlayViewTreeOwners(host)
            setContent {
                PointPickScreen(
                    initialXdp = initialXdp,
                    initialYdp = initialYdp,
                    onCancel = { close() },
                    onCommit = { xDp, yDp ->
                        onCommit?.invoke(xDp, yDp)
                        close()
                    },
                )
            }
        }
        if (!wm.safeAddView(composeView, lp)) {
            android.util.Log.e(LOG_TAG, "point pick: addView failed type=touch_trusted")
            onError()
            return
        }
        overlayView = composeView
    }

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

@Composable
private fun PointPickScreen(
    initialXdp: Int?,
    initialYdp: Int?,
    onCancel: () -> Unit,
    onCommit: (Int, Int) -> Unit,
) {
    var pointPx by remember { mutableStateOf<Offset?>(null) }
    var screenPx by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current.density
    // 有旧值先落在旧点上（dp→px 按当前 density），否则等用户点。
    LaunchedEffect(screenPx, density) {
        if (pointPx == null && initialXdp != null && initialYdp != null &&
            initialXdp >= 0 && initialYdp >= 0
        ) {
            pointPx = Offset(initialXdp * density, initialYdp * density)
        }
    }
    val pickedDp = pointPx?.let { (it.x / density).roundToInt() to (it.y / density).roundToInt() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenPx = it }
            .pointerInput(Unit) {
                detectTapGestures { pointPx = it }
            },
    ) {
        pointPx?.let { point ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = 14.dp.toPx()
                drawCircle(color = Color(0xFF1A73E8), radius = r, center = point)
                drawCircle(color = Color.White, radius = r, center = point,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                drawLine(color = Color.White,
                    start = Offset(point.x - r - 8.dp.toPx(), point.y),
                    end = Offset(point.x + r + 8.dp.toPx(), point.y),
                    strokeWidth = 2.dp.toPx())
                drawLine(color = Color.White,
                    start = Offset(point.x, point.y - r - 8.dp.toPx()),
                    end = Offset(point.x, point.y + r + 8.dp.toPx()),
                    strokeWidth = 2.dp.toPx())
            }
        }

        Text(
            text = stringResource(R.string.point_pick_hint),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = pickedDp?.let { (x, y) ->
                    stringResource(R.string.point_pick_point, x, y)
                } ?: stringResource(R.string.point_pick_empty),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel), color = Color.White)
            }
            FilledTonalButton(
                enabled = pickedDp != null,
                onClick = { pickedDp?.let { (x, y) -> onCommit(x, y) } },
            ) { Text(stringResource(R.string.confirm)) }
        }
    }
}
