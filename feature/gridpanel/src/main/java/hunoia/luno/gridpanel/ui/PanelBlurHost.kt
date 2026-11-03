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
    /** 只有 drawable 真正 attach 成功才回调 true；卡片据此决定底色是否可透。 */
    onActiveChanged: (Boolean) -> Unit = {},
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
        update = { controller.update(enabled, radiusPx, cornerRadiusPx, tint, onActiveChanged) },
        modifier = modifier,
    )
}

/**
 * 把 attach/update/detach 收在一个普通对象里：AndroidView 的 update 频率很高，
 * 参数变化只 update 已有 drawable，只有 attached 且尚未挂上时才反射 create。
 */
private class BlurHostController {

    private var view: View? = null
    private var handle: BackgroundBlurHandle? = null
    private var enabled = false
    private var radiusPx = 0
    private var cornerRadiusPx = 0f
    private var tint = AndroidColor.TRANSPARENT
    private var attachAttempts = 0
    private var disposed = false
    private var active = false
    private var onActiveChanged: (Boolean) -> Unit = {}

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
                setActive(false)
            }
        })
        // 尺寸/位置变化（卡片拖动、面板缩放）时刷新 blur region 的 bounds 与圆角。
        target.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            apply(force = false)
        }
    }

    fun update(
        enabled: Boolean,
        radiusPx: Int,
        cornerRadiusPx: Float,
        tint: Int,
        onActiveChanged: (Boolean) -> Unit,
    ) {
        this.enabled = enabled
        this.radiusPx = radiusPx
        this.cornerRadiusPx = cornerRadiusPx
        this.tint = tint
        this.onActiveChanged = onActiveChanged
        apply(force = false)
    }

    fun dispose() {
        disposed = true
        handle?.detach()
        handle = null
        active = false
        onActiveChanged = {}
        view = null
    }

    private fun apply(force: Boolean) {
        if (disposed) return
        val target = view ?: return
        if (!enabled || radiusPx <= 0) {
            handle?.detach()
            handle = null
            setActive(false)
            return
        }
        if (!target.isAttachedToWindow) return
        if (handle == null) {
            // attach 可能因 ViewRoot 尚未就绪而失败；限制次数，避免每帧反射。
            if (!force && attachAttempts >= 3) return
            attachAttempts++
            handle = BackgroundBlur.attach(target, radiusPx, cornerRadiusPx, tint) ?: return
            setActive(true)
        }
        handle?.update(radiusPx, cornerRadiusPx, tint)
    }

    /** 模糊状态只在真正变化时回调；post 到下一帧，避免在组合/测量期间写 Compose 状态。 */
    private fun setActive(next: Boolean) {
        if (active == next) return
        active = next
        val target = view ?: return
        target.post {
            if (!disposed && active == next) onActiveChanged(next)
        }
    }
}
