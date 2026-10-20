package hunoia.luno.gridpanel.widget

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Outline
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import hunoia.luno.BuildConfig
import kotlin.math.abs
import kotlin.math.min

/**
 * 承载单个第三方小部件的 View。
 *
 * 为什么是 FrameLayout 包装而不是直接继承 AppWidgetHostView：宿主视图的
 * (context, hostId, appWidgetId) 三参构造是 @hide API —— SDK 编不过。
 * AppWidgetHost.createView 本来就负责构造并挂接 RemoteViews，我们把它 add 进来即可。
 *
 * 这一版重写了 measure/layout（一墨 du5 同款）：
 * - onMeasure 用 EXACTLY 把子 View 钉到内容区，provider 的期望尺寸不再撑出白边；
 * - onLayout 把子 View 居中（子 View 画得比格子小的时候，白边均匀落在两侧）；
 * - 圆角三级 fallback：android.R.id.background → 首个有背景的 child → 自身。
 */
class PanelAppWidgetView(context: Context) : FrameLayout(context) {

    private var boundId = -1
    private var boundProvider = ""
    private var maxWidthDp = 0
    private var maxHeightDp = 0

    /** 宿主产出的真正视图；换绑/重建时换掉。 */
    private var hostChild: AppWidgetHostView? = null
    private var blankRetry = 0
    private var contentW = 0
    private var contentH = 0
    private var clipApplied = false

    /** 小部件内部滚不动时，把竖直位移中继给面板的 ScrollState（正值 = 向下滚）。 */
    var onVerticalScroll: ((dy: Float) -> Unit)? = null

    /** 抬手速度中继（正值 = 向下滚），用于面板侧做惯性。 */
    var onFling: ((velocity: Float) -> Unit)? = null

    var loadState: WidgetLoadState = WidgetLoadState.Loading
        private set

    private var loadStateListener: ((WidgetLoadState) -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val cornerRadiusPx = 14f * resources.displayMetrics.density
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var relaying = false
    private var velocityTracker: VelocityTracker? = null

    fun setLoadStateListener(listener: ((WidgetLoadState) -> Unit)?) {
        loadStateListener = listener
    }

    private fun setLoadState(state: WidgetLoadState) {
        if (loadState == state) return
        loadState = state
        loadStateListener?.invoke(state)
    }

    init {
        PanelAppWidgetHost.ensureListening()
    }

    fun updateWidget(appWidgetId: Int, provider: String, maxWidthDp: Int, maxHeightDp: Int) {
        val sizeChanged = maxWidthDp != this.maxWidthDp || maxHeightDp != this.maxHeightDp
        this.maxWidthDp = maxWidthDp
        this.maxHeightDp = maxHeightDp
        if (appWidgetId == boundId && provider == boundProvider) {
            if (sizeChanged) {
                clipApplied = false
                requestLayout()
                applyWidget(appWidgetId, provider)
            }
            return
        }
        boundId = appWidgetId
        boundProvider = provider
        clipApplied = false
        blankRetry = 0
        applyWidget(appWidgetId, provider)
    }

    private fun applyWidget(appWidgetId: Int, provider: String) {
        val manager = AppWidgetManager.getInstance(context)
        val info = manager.getAppWidgetInfo(appWidgetId)
        if (info == null) {
            Log.w(TAG, "widget id=$appWidgetId no longer bound; showing placeholder")
            setLoadState(WidgetLoadState.Failed)
            notifyWidgetGone(appWidgetId)
            return
        }
        setLoadState(WidgetLoadState.Loading)
        // 必须先下 options 再 createView：provider 收到尺寸更新会重发 RemoteViews，
        // 顺序反了首帧就是按旧尺寸画的那一版。
        applyAppWidgetOptions(appWidgetId, maxWidthDp, maxHeightDp)
        val created = runCatching {
            PanelAppWidgetHost.get().createView(context, appWidgetId, info)
        }.getOrNull()
        if (created == null) {
            removeAllViews()
            hostChild = null
            setLoadState(WidgetLoadState.Failed)
            return
        }
        hostChild?.let { removeView(it) }
        hostChild = created
        clipApplied = false
        addView(created, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        if (created.childCount == 0 && blankRetry < 2) {
            blankRetry++
            postDelayed({
                if (boundId == appWidgetId) applyWidget(appWidgetId, provider)
            }, 400L * blankRetry)
        } else if (created.childCount > 0) {
            blankRetry = 0
            setLoadState(WidgetLoadState.Ready)
        } else {
            setLoadState(WidgetLoadState.Failed)
        }
        if (BuildConfig.DEBUG) {
            logSizeChain(info)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        contentW = MeasureSpec.getSize(widthMeasureSpec)
        contentH = MeasureSpec.getSize(heightMeasureSpec)
        if (contentW <= 0 || contentH <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        setMeasuredDimension(contentW, contentH)
        val wSpec = MeasureSpec.makeMeasureSpec(contentW, MeasureSpec.EXACTLY)
        val hSpec = MeasureSpec.makeMeasureSpec(contentH, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) {
            getChildAt(i).measure(wSpec, hSpec)
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cw = r - l
        val ch = b - t
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val w = min(child.measuredWidth, contentW)
            val h = min(child.measuredHeight, contentH)
            child.layout((cw - w) / 2, (ch - h) / 2, (cw + w) / 2, (ch + h) / 2)
        }
        if (!clipApplied) {
            clipApplied = true
            applyRoundedClip()
        }
        if (BuildConfig.DEBUG) {
            val inner = hostChild?.let { h -> if (h.childCount > 0) h.getChildAt(0) else h }
            Log.d(
                TAG,
                "size chain canvas=${contentW}x${contentH} host=${hostChild?.width}x${hostChild?.height}" +
                    " inner=${inner?.width}x${inner?.height}",
            )
        }
    }

    // ---- 圆角 ----------------------------------------------------------------

    /**
     * provider 不同，背景所在层级也不同：有的在 android.R.id.background，有的
     * 直接在第一个 child 上，还有的自绘背景。三级 fallback 逐层试，宁可少裁一层，
     * 也不要出现直角外露。
     */
    private fun applyRoundedClip() {
        val host = hostChild ?: return
        val bg = host.findViewById<View>(android.R.id.background)
        if (bg != null && bg.background != null) {
            clipView(bg)
            return
        }
        if (host.childCount > 0) {
            val child = host.getChildAt(0)
            if (child.background != null) {
                clipView(child)
                return
            }
        }
        // 自身分支：给宿主一个圆角 outline，clipToOutline 只裁这一层，
        // 不再叠加子层的裁剪（双重裁剪会在圆角处出锯齿）。
        host.clipToOutline = true
        host.outlineProvider = roundRectOutline()
    }

    private fun clipView(view: View) {
        view.clipToOutline = true
        view.outlineProvider = roundRectOutline()
    }

    private fun roundRectOutline() = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
        }
    }

    // ---- 触摸中继 ------------------------------------------------------------

    /**
     * 只在「竖直为主 + 子层确实滚不动」时拦截。否则 widget 内部的列表/按钮
     * 会完全失效 —— 那是比「拖不动面板」更糟的回归。
     */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                lastY = ev.y
                relaying = false
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().apply { addMovement(ev) }
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(ev)
                if (!relaying) {
                    val dy = ev.y - downY
                    val dx = ev.x - downX
                    if (abs(dy) > touchSlop && abs(dy) > abs(dx)) {
                        // 手指上移 = 想看下面的内容（ScrollState 正值）。
                        val dir = if (dy < 0) 1 else -1
                        if (!canChildScrollVertically(hostChild, ev.x, ev.y, dir)) {
                            relaying = true
                            return true
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.recycle()
                velocityTracker = null
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!relaying) return super.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                // 手指上移（y 减小）→ 内容上移 → ScrollState 增。
                onVerticalScroll?.invoke(lastY - ev.y)
                lastY = ev.y
                return true
            }

            MotionEvent.ACTION_UP -> {
                val tracker = velocityTracker
                if (tracker != null) {
                    tracker.computeCurrentVelocity(1000)
                    onFling?.invoke(-tracker.yVelocity)
                }
                velocityTracker?.recycle()
                velocityTracker = null
                relaying = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.recycle()
                velocityTracker = null
                relaying = false
                return true
            }
        }
        return true
    }

    private fun canChildScrollVertically(view: View?, x: Float, y: Float, direction: Int): Boolean {
        if (view == null) return false
        if (view.canScrollVertically(direction)) return true
        if (view is ViewGroup) {
            for (i in view.childCount - 1 downTo 0) {
                val child = view.getChildAt(i)
                val cx = x - child.left + view.scrollX
                val cy = y - child.top + view.scrollY
                if (cx >= 0 && cx < child.width && cy >= 0 && cy < child.height) {
                    if (canChildScrollVertically(child, cx, cy, direction)) return true
                }
            }
        }
        return false
    }

    private fun logSizeChain(info: android.appwidget.AppWidgetProviderInfo) {
        val inner = hostChild?.let { h -> if (h.childCount > 0) h.getChildAt(0) else h }
        Log.d(
            TAG,
            "widget=${info.provider.flattenToShortString()} min=${info.minWidth}x${info.minHeight}" +
                " allocated=${maxWidthDp}x${maxHeightDp} canvas=${contentW}x${contentH}" +
                " inner=${inner?.width}x${inner?.height}",
        )
    }

    companion object {
        private const val TAG = "PanelAppWidgetView"
    }
}
