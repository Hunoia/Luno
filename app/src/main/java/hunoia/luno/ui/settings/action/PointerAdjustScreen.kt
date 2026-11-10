package hunoia.luno.ui.settings.action

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.config.defaults.GestureSettingsDefaults
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.PointerPoint
import hunoia.luno.config.model.pointerCanonicalOffset
import hunoia.luno.config.model.clampPointerPoint
import hunoia.luno.config.model.followPoint
import hunoia.luno.config.model.initialPoint
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.ui.component.PointerCursor
import kotlinx.coroutines.launch

/** 拖动目标是主光标还是跟随触发点。 */
private enum class AdjustTarget { None, Cursor, Trigger }

/** 跟随页演示触发点的默认位置（左下分区）。 */
private val DefaultTriggerRatio = Offset(0.25f, 0.75f)

/**
 * 指针位置调整页（整屏 1:1 预览，无滑杆、无标题，顶部只有三个圆形按钮）。
 *
 * - `follow = true`：小圆点是触发点（可拖，只影响演示分区），主光标是实际显示位置，
 *   拖动主光标直接写回「以左下分区为基准」的偏移；跨半屏时镜像立即生效。
 * - `follow = false`：只有一个主光标，拖动即设置初始落点。
 *
 * 拖动过程中直接按「当前显示位置」反算并写回偏移，渲染再用同一口径重算，光标始终跟着手指。
 */
@Composable
fun PointerAdjustScreen(
    follow: Boolean,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val gesture by TuneConfig.gesture.collectAsStateWithLifecycle(initialValue = GestureSettings())
    PointerAdjustContent(
        follow = follow,
        initial = gesture.pointer,
        onCommit = { next ->
            scope.launch { TuneConfig.updateGesture { it.copy(pointer = next) } }
            onBack()
        },
        onCancel = onBack,
    )
}

@Composable
fun PointerAdjustContent(
    follow: Boolean,
    initial: GestureSettings.Pointer,
    onCommit: (GestureSettings.Pointer) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 状态句柄必须稳定：如果按 initial 重新 remember，配置异步到达时会换一个新 state，
    // 而 pointerInput 闭包仍握着旧的那份，拖动写入就丢了（表现为松手回弹）。
    var pending by remember { mutableStateOf(initial) }
    LaunchedEffect(initial) { pending = initial }
    var screenPx by remember { mutableStateOf(IntSize.Zero) }
    // 跟随模式下触发点的演示位置，不落配置：只决定镜像方向。
    var triggerRatio by remember { mutableStateOf(DefaultTriggerRatio) }

    val laidOut = screenPx.width > 0 && screenPx.height > 0
    val trigger = Offset(triggerRatio.x * screenPx.width, triggerRatio.y * screenPx.height)
    val computed = when {
        !laidOut -> PointerPoint(0f, 0f)
        follow -> pending.followPoint(trigger.x, trigger.y, screenPx.width, screenPx.height)
        else -> pending.initialPoint(screenPx.width, screenPx.height)
    }
    val cursor = Offset(computed.x, computed.y)

    val density = LocalDensity.current
    val dotHitPx = with(density) { 24.dp.toPx() }
    val dotRadiusPx = with(density) { 6.dp.toPx() }

    fun clampToScreen(position: Offset) = clampPointerPoint(
        x = position.x,
        y = position.y,
        screenWidthPx = screenPx.width,
        screenHeightPx = screenPx.height,
    )

    fun commitCursor(display: Offset) {
        val width = screenPx.width
        val height = screenPx.height
        if (width <= 0 || height <= 0) return
        // 触发点从状态里重算：pointerInput 闭包捕获的是首帧的 composition 值，
        // 那时 onSizeChanged 还没回填尺寸（trigger 会停在 0,0），提交后光标就会弹回去。
        val triggerNow = Offset(triggerRatio.x * width, triggerRatio.y * height)
        pending = if (follow) {
            val offset = pointerCanonicalOffset(
                trigger = PointerPoint(triggerNow.x, triggerNow.y),
                display = PointerPoint(display.x, display.y),
                screenWidthPx = width,
                screenHeightPx = height,
            )
            pending.copy(followOffsetXRatio = offset.x, followOffsetYRatio = offset.y)
        } else {
            pending.copy(
                initialXRatio = (display.x / width).coerceIn(0f, 1f),
                initialYRatio = (display.y / height).coerceIn(0f, 1f),
            )
        }
    }

    fun reset() {
        pending = if (follow) {
            triggerRatio = DefaultTriggerRatio
            pending.copy(
                followOffsetXRatio = GestureSettingsDefaults.PointerFollowOffsetXRatio,
                followOffsetYRatio = GestureSettingsDefaults.PointerFollowOffsetYRatio,
            )
        } else {
            pending.copy(
                initialXRatio = GestureSettingsDefaults.PointerInitialXRatio,
                initialYRatio = GestureSettingsDefaults.PointerInitialYRatio,
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // 压暗底下的设置页，整块屏幕成为预览画布。
            .background(Color.Black.copy(alpha = 0.6f))
            .onSizeChanged { screenPx = IntSize(it.width, it.height) },
    ) {
        // 输入层在最底：光标与触发点只负责画，不拦事件。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(follow) {
                    // 点按空白处直接把主光标放过去；点在触发点上不处理。
                    detectTapGestures(onTap = { position ->
                        val width = screenPx.width
                        val height = screenPx.height
                        if (width <= 0 || height <= 0) return@detectTapGestures
                        val dot = Offset(triggerRatio.x * width, triggerRatio.y * height)
                        if (follow && (position - dot).getDistance() <= dotHitPx) {
                            return@detectTapGestures
                        }
                        val point = clampPointerPoint(position.x, position.y, width, height)
                        commitCursor(Offset(point.x, point.y))
                    })
                }
                .pointerInput(follow) {
                    var target = AdjustTarget.None
                    detectDragGestures(
                        onDragStart = { position ->
                            val width = screenPx.width
                            val height = screenPx.height
                            if (width <= 0 || height <= 0) {
                                target = AdjustTarget.None
                                return@detectDragGestures
                            }
                            val dot = Offset(triggerRatio.x * width, triggerRatio.y * height)
                            // 触发点画在最上层且命中半径小，重叠时优先给它。
                            target = if (follow && (position - dot).getDistance() <= dotHitPx) {
                                AdjustTarget.Trigger
                            } else {
                                // 拖动过程中直接落配置，不靠松手时的二次提交，避免任何收尾丢失导致回弹。
                                val point = clampPointerPoint(position.x, position.y, width, height)
                                commitCursor(Offset(point.x, point.y))
                                AdjustTarget.Cursor
                            }
                        },
                        onDrag = { change, _ ->
                            val width = screenPx.width
                            val height = screenPx.height
                            if (width <= 0 || height <= 0) return@detectDragGestures
                            when (target) {
                                AdjustTarget.Trigger -> {
                                    triggerRatio = Offset(
                                        (change.position.x / width).coerceIn(0f, 1f),
                                        (change.position.y / height).coerceIn(0f, 1f),
                                    )
                                }
                                AdjustTarget.Cursor -> {
                                    val point = clampPointerPoint(
                                        change.position.x,
                                        change.position.y,
                                        width,
                                        height,
                                    )
                                    commitCursor(Offset(point.x, point.y))
                                }
                                AdjustTarget.None -> Unit
                            }
                        },
                        onDragEnd = { target = AdjustTarget.None },
                        onDragCancel = { target = AdjustTarget.None },
                    )
                },
        )

        if (laidOut) {
            // 触发点到实际显示位置的连线，先画、压在光标下面。
            if (follow) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.55f),
                        start = trigger,
                        end = cursor,
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(10.dp.toPx(), 8.dp.toPx()),
                        ),
                    )
                }
            }
            PointerCursor(
                position = cursor,
                modifier = Modifier.fillMaxSize(),
                settings = pending,
                trailEnabled = false,
            )
            if (follow) {
                // 触发点：简单小圆点，画在主光标之上，保证重叠时仍可抓取。
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.75f),
                        radius = dotRadiusPx + 1.5.dp.toPx(),
                        center = trigger,
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.9f),
                        radius = dotRadiusPx,
                        center = trigger,
                    )
                }
            }
        }

        Box(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            AdjustTopBar(
                onCancel = onCancel,
                onReset = ::reset,
                onConfirm = { onCommit(pending) },
            )
        }
    }
}
