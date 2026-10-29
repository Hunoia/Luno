package hunoia.luno.gesture.touchsource

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.geometry.Rect as ComposeRect
import hunoia.luno.bridge.ui.windowManager

/**
 * 拦截窗集合：只盖住 [InterceptRegion]，把这块触摸吃掉，让系统手势/下层应用都收不到。
 *
 * 与旧试验版的差别：不再回注（无匹配动作时接受这次触摸丢失），因此窗口本身不需要
 * 任何触摸回调，只要存在即可；临时关触能力留给后续「按压取词」之类的会话复用。
 */
class InterceptWindows(private val context: Context) {

    private val wm: WindowManager get() = context.windowManager()
    private val views = LinkedHashMap<InterceptRegion, View>()

    var isShowing: Boolean = false
        private set

    /**
     * 按 diff 更新：只摘掉没了的区、只添加新出现的区。
     * 之前每次配置发射都全拆全建，拦截窗会闪一下（那段时间触摸穿透到下层）。
     */
    fun update(regions: List<InterceptRegion>) {
        if (!Settings.canDrawOverlays(context)) {
            release()
            return
        }
        val wanted = regions.toSet()
        val removed = views.keys.filterNot { it in wanted }
        removed.forEach { region ->
            views.remove(region)?.let { view -> runCatching { wm.removeView(view) } }
        }
        regions.forEach { region ->
            if (region in views) return@forEach
            add(region)?.let { views[region] = it }
        }
        isShowing = views.isNotEmpty()
    }

    /** 注入/临时放行期间关掉触摸，避免自己的注入再次命中拦截窗。 */
    fun setTouchable(touchable: Boolean) {
        views.values.forEach { view ->
            val lp = view.layoutParams as? WindowManager.LayoutParams ?: return@forEach
            val before = lp.flags
            lp.flags = if (touchable) {
                lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            } else {
                lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            if (lp.flags != before) {
                runCatching { wm.updateViewLayout(view, lp) }
            }
        }
    }

    fun release() {
        views.values.forEach { view -> runCatching { wm.removeView(view) } }
        views.clear()
        isShowing = false
    }

    private fun add(region: InterceptRegion): View? {
        val rect: ComposeRect = region.rect
        val left = rect.left.toInt()
        val top = rect.top.toInt()
        val width = (rect.width.toInt()).coerceAtLeast(1)
        val height = (rect.height.toInt()).coerceAtLeast(1)
        val lp = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            x = left
            y = top
            @Suppress("RtlHardcoded")
            gravity = Gravity.LEFT or Gravity.TOP
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        val view = View(context).apply {
            systemGestureExclusionRects = listOf(Rect(0, 0, width, height))
        }
        return runCatching { wm.addView(view, lp) }
            .onFailure { it.printStackTrace() }
            .let { if (it.isSuccess) view else null }
    }
}
