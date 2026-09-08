package hunoia.luno.pointer
import hunoia.luno.ui.theme.*

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import hunoia.luno.ui.theme.AnimRipple
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import hunoia.luno.config.model.GestureSettings
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun PointerCursor(
    position: Offset,
    modifier: Modifier = Modifier,
    settings: GestureSettings.Pointer = GestureSettings.Pointer(),
    clickPulseKey: Int = 0,
    cancelHint: Boolean = false,
) {
    val trail = remember { mutableStateListOf<Offset>() }
    var pulse by remember { mutableStateOf(0f) }
    val rippleAnim = remember { Animatable(0f) }
    val entranceAnim = remember { Animatable(0f) }
    val accentColor = MaterialTheme.colorScheme.primary
    val renderedPosition by animateOffsetAsState(
        targetValue = position,
        animationSpec = tween(durationMillis = 45, easing = LinearEasing),
        label = "PointerCursorPosition",
    )
    val maxTrailSize = 18
    LaunchedEffect(Unit) {
        entranceAnim.animateTo(1f, animationSpec = tween(durationMillis = 140, easing = LinearEasing))
    }
    LaunchedEffect(renderedPosition) {
        if (renderedPosition.x.isFinite() && renderedPosition.y.isFinite()) {
            val last = trail.lastOrNull()
            if (last == null || (renderedPosition - last).getDistance() >= settings.cursorSizeDp * 0.22f) {
                trail.add(renderedPosition)
                while (trail.size > maxTrailSize) trail.removeAt(0)
            }
        }
    }
    LaunchedEffect(clickPulseKey) {
        if (clickPulseKey == 0) return@LaunchedEffect
        pulse = 1f
        repeat(8) {
            delay(16)
            pulse *= 0.72f
        }
        pulse = 0f
    }
    LaunchedEffect(clickPulseKey) {
        if (clickPulseKey == 0) return@LaunchedEffect
        rippleAnim.snapTo(0f)
        rippleAnim.animateTo(1f, animationSpec = tween(durationMillis = AnimRipple.toInt(), easing = LinearEasing))
        rippleAnim.snapTo(0f)
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        if (!renderedPosition.x.isFinite() || !renderedPosition.y.isFinite()) return@Canvas
        val visibleAlpha = settings.cursorAlpha * entranceAnim.value
        val baseColor = accentColor.copy(alpha = visibleAlpha)
        val radius = settings.cursorSizeDp.dp.toPx() / 2f * (0.88f + 0.12f * entranceAnim.value)
        val ringRadius = radius * (1f - pulse * 0.12f)
        val wideStroke = radius * 0.95f
        val coreStroke = radius * 0.34f
        trail.zipWithNext().forEachIndexed { index, (start, end) ->
            val progress = (index + 1).toFloat() / trail.size
            val alpha = progress * progress * visibleAlpha
            drawLine(color = baseColor.copy(alpha = alpha * 0.14f), start = start, end = end, strokeWidth = wideStroke, cap = StrokeCap.Round)
            drawLine(color = baseColor.copy(alpha = alpha * 0.42f), start = start, end = end, strokeWidth = coreStroke, cap = StrokeCap.Round)
        }
        if (cancelHint) {
            val hintColor = Color.Red.copy(alpha = 0.22f * entranceAnim.value)
            drawCircle(color = hintColor, radius = radius * 1.8f, center = renderedPosition)
            drawCircle(
                color = Color.Red.copy(alpha = 0.62f * entranceAnim.value),
                radius = radius * 2.2f,
                center = renderedPosition,
                style = Stroke(width = Spacing2.toPx()),
            )
        }
        if (pulse > 0f) {
            drawCircle(
                color = baseColor.copy(alpha = pulse * 0.26f),
                radius = radius * (1.25f + (1f - pulse) * 1.1f),
                center = renderedPosition,
                style = Stroke(width = Spacing2.toPx()),
            )
        }
        drawCircle(
            color = Color.Black.copy(alpha = 0.75f * visibleAlpha),
            radius = ringRadius,
            center = renderedPosition,
            style = Stroke(width = Spacing4.toPx()),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.9f * visibleAlpha),
            radius = ringRadius,
            center = renderedPosition,
            style = Stroke(width = Spacing2.toPx()),
        )
        drawCircle(
            color = baseColor,
            radius = ringRadius,
            center = renderedPosition,
            style = Stroke(width = 1.2.dp.toPx()),
        )
        drawCircle(color = Color.Black.copy(alpha = 0.75f * visibleAlpha), radius = radius * 0.16f, center = renderedPosition)
        drawCircle(color = Color.White.copy(alpha = 0.9f * visibleAlpha), radius = radius * 0.1f, center = renderedPosition)
        drawCircle(color = baseColor, radius = radius * 0.06f, center = renderedPosition)
        if (rippleAnim.value > 0f) {
            drawCircle(
                color = baseColor.copy(alpha = (1f - rippleAnim.value) * 0.4f),
                radius = radius * (1f + rippleAnim.value * 3f),
                center = renderedPosition,
                style = Stroke(width = Spacing2.toPx()),
            )
        }
    }
}

class PointerHandle(
    internal val isActiveState: MutableState<Boolean>,
    internal val start: (GestureSettings.Pointer, Offset) -> Boolean,
    internal val onDrag: (Offset) -> Boolean,
    internal val onDragEnd: () -> Unit,
    internal val onDragCancel: () -> Unit,
) {
    val isActive: Boolean get() = isActiveState.value
}

@Composable
internal fun rememberPointerHandle(
    gestureSettings: GestureSettings,
    modifier: Modifier = Modifier,
    onPointerStart: (GestureSettings.Pointer) -> Boolean,
    onPointerEnd: () -> Unit,
    shouldPreservePointerCancel: () -> Boolean = { false },
    onPointerActionAtPosition: (Int, Int, Boolean) -> Unit,
): PointerHandle {
    val isActive = remember { mutableStateOf(false) }
    val cursorPosition = remember { mutableStateOf(pointerInitialPosition(gestureSettings.pointer)) }
    val touchPosition = remember { mutableStateOf(Offset.Unspecified) }
    val leftCancelEdge = remember { mutableStateOf(false) }
    val cancelHint = remember { mutableStateOf(false) }
    val clickPulseKey = remember { mutableStateOf(0) }
    val pSettings = remember { mutableStateOf(gestureSettings.pointer) }

    LaunchedEffect(gestureSettings.pointer) {
        pSettings.value = gestureSettings.pointer
    }

    fun clearTouchState() {
        touchPosition.value = Offset.Unspecified
        leftCancelEdge.value = false
        cancelHint.value = false
    }

    fun dispatchClick(keepActive: Boolean, target: Offset) {
        clickPulseKey.value += 1
        onPointerActionAtPosition(
            target.x.roundToInt(),
            target.y.roundToInt(),
            keepActive,
        )
    }

    fun finish(click: Boolean) {
        if (!isActive.value) return
        if (!click && shouldPreservePointerCancel()) return
        if (click) {
            val target = cursorPosition.value
            val keepActive = pSettings.value.continuousMode
            isActive.value = false
            clearTouchState()
            dispatchClick(keepActive, target)
        } else {
            isActive.value = false
            clearTouchState()
            onPointerEnd()
        }
    }

    if (isActive.value) {
        PointerCursor(
            position = cursorPosition.value,
            modifier = modifier.fillMaxSize(),
            settings = pSettings.value,
            clickPulseKey = clickPulseKey.value,
            cancelHint = cancelHint.value,
        )
    }

    fun handleStart(pointerSettings: GestureSettings.Pointer, fingerPos: Offset): Boolean {
        if (isActive.value) return false
        pSettings.value = pointerSettings
        if (!onPointerStart(pSettings.value)) return false
        cursorPosition.value = pointerInitialPosition(pSettings.value, Offset.Unspecified)
        touchPosition.value = fingerPos
        leftCancelEdge.value = false
        cancelHint.value = false
        isActive.value = true
        return true
    }

    fun handleOnDrag(dragAmount: Offset): Boolean {
        touchPosition.value += dragAmount
        val inCancelEdge = pSettings.value.continuousMode &&
            isPointerCancelGesture(touchPosition.value, pSettings.value)
        cancelHint.value = inCancelEdge
        if (!inCancelEdge) {
            leftCancelEdge.value = true
        } else if (leftCancelEdge.value) {
            finish(click = false)
            return false
        }
        cursorPosition.value = movePointerCursor(cursorPosition.value, dragAmount, pSettings.value)
        return true
    }

    return remember {
        PointerHandle(
            isActiveState = isActive,
            start = ::handleStart,
            onDrag = ::handleOnDrag,
            onDragEnd = { finish(click = true) },
            onDragCancel = { finish(click = false) },
        )
    }
}
