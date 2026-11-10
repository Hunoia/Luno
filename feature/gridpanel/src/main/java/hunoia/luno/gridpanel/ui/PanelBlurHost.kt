package hunoia.luno.gridpanel.ui

import android.graphics.Color as AndroidColor
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import hunoia.luno.bridge.ui.BackgroundBlur
import hunoia.luno.bridge.ui.BackgroundBlurHandle

/**
 * 模糊宿主状态：`Pending` = 还没挂上（入场最初几帧、尺寸未就绪），`Unavailable` = 确认本机挂不上。
 * 卡片只对 `Unavailable` 提高不透明度兜底，`Pending` 不能当失败——否则面板一亮就从
 * 近白的兜底色闪到正常透明度。
 */
enum class BlurState { Pending, Attached, Unavailable }

/**
 * 区域模糊宿主：一块不可点击的 View，背景是 [BackgroundBlur] 的 drawable。
 *
 * 必须放在目标内容**下面**（父 Box 里先声明），它只负责绘制 blur；上层的
 * Compose 背景/内容继续按原样覆盖。隐藏 API 不可用时 attach 返回 null，这里
 * 静默保持透明，不抛异常、不影响面板交互。
 */
@Composable
fun PanelBlurHost(
    enabled: Boolean,
    radiusPx: Int,
    cornerRadiusPx: Float,
    modifier: Modifier = Modifier,
    tint: Int = AndroidColor.TRANSPARENT,
    /** 状态变化回传：调用方据此决定底色是否要兜底提高不透明度。 */
    onStateChanged: (BlurState) -> Unit = {},
) {
    val controller = remember { BlurHostController() }
    DisposableEffect(controller) {
        onDispose { controller.dispose() }
    }
    AndroidView(
        factory = { context ->
            View(context).apply {
                isClickable = false
                isFocusable = false
                isFocusableInTouchMode = false
                setBackgroundColor(AndroidColor.TRANSPARENT)
                controller.bind(this)
            }
        },
        update = { controller.update(enabled, radiusPx, cornerRadiusPx, tint, onStateChanged) },
        modifier = modifier,
    )
}

/**
 * 把 attach/update/detach 收在一个普通对象里：AndroidView 的 update 频率很高，
 * 参数变化只 update 已有 drawable，只有 attached 且尚未挂上时才反射 create。
 */
private class BlurHostController {

    private companion object {
        /** attach 最多试几次（每帧一次）；用完判定本机挂不上，交回调用方提高不透明度兜底。 */
        const val MaxAttachAttempts = 3
    }

    private var view: View? = null
    private var handle: BackgroundBlurHandle? = null
    private var enabled = false
    private var radiusPx = 0
    private var cornerRadiusPx = 0f
    private var tint = AndroidColor.TRANSPARENT
    private var attachAttempts = 0
    private var disposed = false
    private var state = BlurState.Pending
    private var onStateChanged: (BlurState) -> Unit = {}

    fun bind(target: View) {
        view = target
        target.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                attachAttempts = 0
                apply(force = true)
            }

            override fun onViewDetachedFromWindow(v: View) {
                handle?.detach()
                handle = null
                setState(BlurState.Pending)
            }
        })
        // 尺寸/位置变化（卡片拖动、面板缩放）时刷新 blur region 的 bounds 与圆角。
        // 尺寸从 0 变大是首次可 attach 的时机：清掉之前的空跑计数再试一次。
        target.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (handle == null) attachAttempts = 0
            apply(force = handle == null)
        }
    }

    fun update(
        enabled: Boolean,
        radiusPx: Int,
        cornerRadiusPx: Float,
        tint: Int,
        onStateChanged: (BlurState) -> Unit,
    ) {
        this.enabled = enabled
        this.radiusPx = radiusPx
        this.cornerRadiusPx = cornerRadiusPx
        this.tint = tint
        this.onStateChanged = onStateChanged
        apply(force = false)
    }

    fun dispose() {
        disposed = true
        handle?.detach()
        handle = null
        state = BlurState.Pending
        onStateChanged = {}
        view = null
    }

    private fun apply(force: Boolean) {
        if (disposed) return
        val target = view ?: return
        if (!enabled || radiusPx <= 0) {
            handle?.detach()
            handle = null
            setState(BlurState.Pending)
            return
        }
        if (!target.isAttachedToWindow) return
        if (handle == null) {
            // 尺寸未就绪时 attach 必定失败，不计次、也不当作「挂不上」。
            if (target.width <= 0 || target.height <= 0) return
            // attach 可能因 ViewRoot 尚未就绪而失败；限制次数，避免每帧反射。
            if (!force && attachAttempts >= MaxAttachAttempts) return
            attachAttempts++
            val attached = BackgroundBlur.attach(target, radiusPx, cornerRadiusPx, tint)
            if (attached == null) {
                if (attachAttempts >= MaxAttachAttempts) setState(BlurState.Unavailable)
                return
            }
            handle = attached
            setState(BlurState.Attached)
        }
        handle?.update(radiusPx, cornerRadiusPx, tint)
    }

    /** 状态只在真正变化时回调；post 到下一帧，避免在组合/测量期间写 Compose 状态。 */
    private fun setState(next: BlurState) {
        if (state == next) return
        state = next
        val target = view ?: return
        target.post {
            if (!disposed && state == next) onStateChanged(next)
        }
    }
}
