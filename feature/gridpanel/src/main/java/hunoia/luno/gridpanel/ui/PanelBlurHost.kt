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
        update = { controller.update(enabled, radiusPx, cornerRadiusPx, tint) },
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
            }
        })
        // 尺寸/位置变化（卡片拖动、面板缩放）时刷新 blur region 的 bounds 与圆角。
        target.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            apply(force = false)
        }
    }

    fun update(enabled: Boolean, radiusPx: Int, cornerRadiusPx: Float, tint: Int) {
        this.enabled = enabled
        this.radiusPx = radiusPx
        this.cornerRadiusPx = cornerRadiusPx
        this.tint = tint
        apply(force = false)
    }

    fun dispose() {
        disposed = true
        handle?.detach()
        handle = null
        view = null
    }

    private fun apply(force: Boolean) {
        if (disposed) return
        val target = view ?: return
        if (!enabled || radiusPx <= 0) {
            handle?.detach()
            handle = null
            return
        }
        if (!target.isAttachedToWindow) return
        if (handle == null) {
            // attach 可能因 ViewRoot 尚未就绪而失败；限制次数，避免每帧反射。
            if (!force && attachAttempts >= 3) return
            attachAttempts++
            handle = BackgroundBlur.attach(target, radiusPx, cornerRadiusPx, tint) ?: return
        }
        handle?.update(radiusPx, cornerRadiusPx, tint)
    }
}
