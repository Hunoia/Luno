package hunoia.luno.runtime.overlay

import android.content.Context
import java.lang.ref.WeakReference

/**
 * 无障碍服务提供的 overlay 宿主 context。
 *
 * `TYPE_ACCESSIBILITY_OVERLAY` 只能由连着的无障碍服务挂；悬浮球服务（同进程）需要一个
 * 可借用它的窗口 context 来做“锁屏全屏黑幕 / 关闭动画”。服务销毁后自动失效，调用方
 * 回落到普通 `TYPE_APPLICATION_OVERLAY`。
 */
object AccessibilityOverlayHost {

    @Volatile private var overlayContext: WeakReference<Context>? = null

    fun attach(context: Context) {
        overlayContext = WeakReference(context)
    }

    fun detach() {
        overlayContext = null
    }

    val context: Context? get() = overlayContext?.get()
}
