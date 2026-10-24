package hunoia.luno.gridpanel.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.RemoteViews
import hunoia.luno.BuildConfig
import hunoia.luno.core.AppContext
import kotlin.math.abs

/**
 * 面板内第三方小部件的宿主。宿主 id 必须跨进程稳定，故固定写死。
 * 必须在绑定前 startListening（首帧 RemoteViews 是广播回来的，且该调用幂等）；
 * updateAppWidgetOptions 必须在绑定确认之后调用，否则会异常并带走 :service 进程。
 */
private const val TAG = "PanelAppWidget"

class PanelAppWidgetHost private constructor(
    context: Context,
    hostId: Int,
) : AppWidgetHost(context, hostId) {

    override fun onProviderChanged(appWidgetId: Int, newData: AppWidgetProviderInfo?) {
        Log.i(TAG, "provider changed id=$appWidgetId provider=${newData?.provider}")
    }

    /** 宿主视图统一用 [PanelAppWidgetHostView]：拒绝回落到 initialLayout 占位。 */
    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?,
    ): AppWidgetHostView = PanelAppWidgetHostView(context)

    override fun onProvidersChanged() {
        Log.i(TAG, "providers changed bound=${getAppWidgetIds().toList()}")
        // 缓存里只留当前仍绑定的 id：部件应用被卸载/重装后，宿主侧残留的旧
        // RemoteViews 不清理会一直占内存，还可能被误回放。
        PanelAppWidgetHostView.retainViews(getAppWidgetIds().toSet())
    }

    override fun onAppWidgetRemoved(appWidgetId: Int) {
        // provider 被卸载/用户删除：这个 id 不能再用了，通知面板把该项收掉，
        // 否则会留一个永远空白的格子。
        Log.w(TAG, "widget removed id=$appWidgetId")
        notifyWidgetGone(appWidgetId)
    }

    fun release(appWidgetId: Int) {
        PanelAppWidgetHostView.forget(appWidgetId)
        runCatching { deleteAppWidgetId(appWidgetId) }
            .onFailure { Log.w(TAG, "deleteAppWidgetId failed id=$appWidgetId: ${it.message}") }
    }

    /**
     * 回收本宿主下所有不再被配置引用的 id。删除 / reset / 恢复备份都是整批替换
     * 配置，逐项差量容易漏，以「当前配置还引用哪些 id」为准最稳。
     */
    fun releaseOrphans(referencedIds: Set<Int>) {
        getAppWidgetIds().toList().forEach { id ->
            if (id !in referencedIds) release(id)
        }
    }

    companion object {
        /** 固定 id：宿主 id 必须在应用安装周期内唯一且稳定（公开给 HostView 基类用）。 */
        internal const val HOST_ID = 9201

        @Volatile
        private var instance: PanelAppWidgetHost? = null

        fun get(): PanelAppWidgetHost {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: PanelAppWidgetHost(AppContext.get(), HOST_ID).also {
                    instance = it
                    runCatching { it.startListening() }
                        .onFailure { error -> Log.e(TAG, "startListening failed", error) }
                }
            }
        }

        /** 面板还没打开小部件时不必监听；打开任意一个 AppWidget 项才起。 */
        fun ensureListening() {
            get()
        }
    }
}

/** onAppWidgetRemoved 是宿主回调，不能直接改面板状态，这里用一个窄事件通道。 */
internal fun interface WidgetGoneListener {
    fun onGone(appWidgetId: Int)
}

/**
 * 拒绝回落到 provider 的 initialLayout：系统在 provider 信息刷新（小部件应用升级、
 * 宿主重听同步等）时会推 [AppWidgetHostView.onUpdateProviderInfo] → `updateAppWidget(null)`，
 * 另外每次重建视图时 `setListener` 从服务拉的缓存 views 也可能为 null（点部件拉起
 * 应用后返回面板即重建）。两种 null 都会让宿主视图 inflate initialLayout —— 多数
 * 小部件的 initialLayout 是「一行小部件名称」的占位，表现为凭空多出一个标题。
 * 这里按 id 缓存最近一次非 null 的 RemoteViews（进程级，跨视图重建存活），null 时
 * 回放缓存；缓存也没有（全新绑定首帧）才放行，加载期占位由 Compose 侧 Overlay 盖住。
 */
internal class PanelAppWidgetHostView(context: Context) : AppWidgetHostView(context) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    /** 按下位置命中的子 View 是否是「会拉起应用的点击」：是则抬手后无需等前台事件。 */
    private var downStartsActivity = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // 记录部件点击：面板的「小部件拉起应用则关闭」只认这个标记，
        // 否则音量球/灵动胶囊等系统级前台包变化也会误关面板。
        // 同时记 provider 包名：目标前台包必须是被点部件自己的应用——
        // 否则「点网易云暂停 → 之后调音量出网易云胶囊」会被误判成拉起应用。
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                val hit = findHitView(this, ev.x, ev.y)
                downStartsActivity = clickLaunchesApp(hit)
                if (BuildConfig.DEBUG) {
                    Log.i(
                        TAG,
                        "widget tap view=${hit?.id} launches=$downStartsActivity " +
                            "labels=${clickLabels(hit)} targets=${clickTargetsById[appWidgetId]}",
                    )
                }
                markTapCandidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(ev.x - downX) > touchSlop || abs(ev.y - downY) > touchSlop) {
                    downStartsActivity = false
                    clearTapCandidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                // 长按可能超过 1.5s：以抬手时刻重新起算；拖拽（滑部件）不算点击。
                val cleanTap = abs(ev.x - downX) <= touchSlop && abs(ev.y - downY) <= touchSlop
                if (cleanTap) {
                    markTapCandidate()
                } else {
                    downStartsActivity = false
                    clearTapCandidate()
                }
                // 先让 RemoteViews 处理点击（PendingIntent 真正发出），再立即关面板：
                // 不再靠前台包变化猜“是否拉起应用”，点的是启动型 Intent 就直接关。
                val handled = super.dispatchTouchEvent(ev)
                if (cleanTap && downStartsActivity) {
                    launchCloseListener?.invoke()
                }
                downStartsActivity = false
                return handled
            }
            MotionEvent.ACTION_CANCEL -> {
                downStartsActivity = false
                clearTapCandidate()
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun markTapCandidate() {
        lastTapAt = android.os.SystemClock.elapsedRealtime()
        lastTapPackage = runCatching {
            AppWidgetManager.getInstance(context)
                .getAppWidgetInfo(appWidgetId)?.provider?.packageName
        }.getOrNull()
        tapListener?.invoke()
    }

    override fun updateAppWidget(views: RemoteViews?) {
        if (views != null) {
            lastViewsById[appWidgetId] = views
            val targets = parseClickTargets(views)
            if (clickTargetsById.put(appWidgetId, targets) != targets && BuildConfig.DEBUG) {
                Log.i(TAG, "click targets widget=$appWidgetId targets=$targets")
            }
            super.updateAppWidget(views)
            return
        }
        val cached = appWidgetId.let { lastViewsById[it] }
        if (cached != null) super.updateAppWidget(cached) else super.updateAppWidget(null)
    }

    /** 最近的可点击 View 是 Activity 点击，且不是播放/暂停这类控制按钮：抬手立即关。 */
    private fun clickLaunchesApp(hit: View?): Boolean {
        val targets = clickTargetsById[appWidgetId] ?: return false
        if (targets.isEmpty()) return false
        var view: View? = hit
        while (view != null && view !== this) {
            val target = targets[view.id]
            if (target != null) {
                // 找到最近的可点击父级就停止：外层整卡“打开应用”不能盖过
                // 内层“暂停/上一首”等控制按钮。
                if (!target) return false
                return !hasControlLabel(hit, view.id)
            }
            view = view.parent as? View
        }
        return false
    }

    /** 从命中 View 往上看到最近可点击 View：有无播放/暂停/切歌/收藏等控制语义。 */
    private fun hasControlLabel(hit: View?, stopId: Int): Boolean {
        var view: View? = hit
        while (view != null && view !== this) {
            if (controlWords.any { labelOf(view).contains(it) }) return true
            if (view.id == stopId) break
            view = view.parent as? View
        }
        return false
    }

    private fun clickLabels(hit: View?): String {
        val labels = mutableListOf<String>()
        var view: View? = hit
        while (view != null && view !== this && labels.size < 4) {
            val label = labelOf(view)
            if (label.isNotEmpty()) labels += "${view.id}:$label"
            view = view.parent as? View
        }
        return labels.toString()
    }

    private fun labelOf(view: View): String {
        val desc = view.contentDescription?.toString().orEmpty()
        val text = (view as? android.widget.TextView)?.text?.toString().orEmpty()
        return "$desc $text".trim().lowercase()
    }

    private fun findHitView(root: View, x0: Float, y0: Float): View? {
        var view: View = root
        var x = x0
        var y = y0
        while (view is ViewGroup) {
            var hit: View? = null
            for (i in view.childCount - 1 downTo 0) {
                val child = view.getChildAt(i)
                if (child.visibility != View.VISIBLE) continue
                val cx = x + view.scrollX - child.left
                val cy = y + view.scrollY - child.top
                if (cx >= 0f && cy >= 0f && cx < child.width && cy < child.height) {
                    hit = child
                    x = cx
                    y = cy
                    break
                }
            }
            if (hit == null) break
            view = hit
        }
        return view
    }

    companion object {
        private val lastViewsById = java.util.concurrent.ConcurrentHashMap<Int, RemoteViews>()
        /** 每个 widget 的可点击 View id → 是否 Activity 型 PendingIntent。 */
        private val clickTargetsById = java.util.concurrent.ConcurrentHashMap<Int, Map<Int, Boolean>>()

        /** 最近一次部件点击（DOWN/UP）时刻与被点部件的包名：宿主用它在拉起应用时关面板。 */
        @Volatile
        private var lastTapAt = 0L

        @Volatile
        private var lastTapPackage: String? = null

        /**
         * [withinMs] 内有部件被点过，且其 provider 包名等于 [targetPackage]：
         * 部件内按钮（如播放/暂停）也会留下点击标记，只有「目标前台包 ==
         * 被点部件的应用」才算拉起应用，别的不关。
         *
         * 跨包拉起的小部件（点部件打开浏览器/设置/合作方 App）目标包必然不同；
         * 用一个更短的 [crossPackageWithinMs] 窗口兜底，避免把之后才出现的
         * 音量球/媒体胶囊变化也当成拉起应用。
         */
        fun recentWidgetLaunch(
            targetPackage: String,
            withinMs: Long = 1_500L,
            crossPackageWithinMs: Long = 800L,
        ): Boolean {
            val elapsed = android.os.SystemClock.elapsedRealtime() - lastTapAt
            if (elapsed >= withinMs) return false
            val providerPackage = lastTapPackage ?: return false
            return providerPackage == targetPackage || elapsed < crossPackageWithinMs
        }

        /** 最近 [withinMs] 内是否有干净点击：给窗口列表变化的快速收口用。 */
        fun hasRecentTap(withinMs: Long = 1_500L): Boolean {
            val elapsed = android.os.SystemClock.elapsedRealtime() - lastTapAt
            return lastTapAt > 0L && elapsed in 0 until withinMs
        }

        /** 部件被点时通知轮询器：不等延迟的 WINDOW_STATE_CHANGED，主动看窗口列表。 */
        @Volatile
        private var tapListener: (() -> Unit)? = null

        fun setTapListener(listener: (() -> Unit)?) {
            tapListener = listener
        }

        /** 点击命中“拉起应用”的 View 时，由 Overlay 立即关面板。 */
        @Volatile
        private var launchCloseListener: (() -> Unit)? = null

        fun setLaunchCloseListener(listener: (() -> Unit)?) {
            launchCloseListener = listener
        }

        /** 部件之外的触摸（内置滑杆/滑条等）会取消拉起候选。 */
        fun clearTapCandidate() {
            lastTapAt = 0L
            lastTapPackage = null
        }

        /** 小部件 id 被回收（release）：缓存一并清掉，避免复用 id 后串内容。 */
        fun forget(appWidgetId: Int) {
            lastViewsById.remove(appWidgetId)
            clickTargetsById.remove(appWidgetId)
        }

        /** 只保留 [ids] 的缓存（provider 列表刷新时调用）。 */
        fun retainViews(ids: Set<Int>) {
            lastViewsById.keys.retainAll(ids)
            clickTargetsById.keys.retainAll(ids)
        }
    }
}

private val controlWords = listOf(
    "play", "pause", "next", "prev", "previous", "rewind", "forward",
    "favorite", "like", "love", "heart", "mute", "volume", "shuffle", "repeat",
    "share", "comment", "download",
    "播放", "暂停", "上一", "下一", "收藏", "红心", "喜欢", "音量", "静音",
    "快进", "快退", "分享", "评论", "下载",
)

/** 递归读字段（含父类）：RemoteViews 内部 Action 字段不在 SDK，读不到就当不支持。 */
private fun fieldValue(instance: Any, vararg names: String): Any? {
    var clazz: Class<*>? = instance.javaClass
    while (clazz != null) {
        for (name in names) {
            val field = runCatching {
                clazz.getDeclaredField(name).apply { isAccessible = true }
            }.getOrNull()
            if (field != null) {
                return runCatching { field.get(instance) }.getOrNull()
            }
        }
        clazz = clazz.superclass
    }
    return null
}

/**
 * 从 RemoteViews 动作里找出可点击 View → 是否 Activity 型 PendingIntent。
 * RemoteViews 的 onClick 由系统内部类持有 PendingIntent，没有公开 API；
 * 反射失败时返回空 Map，调用方回退到前台包检测。
 * 同时兼容 collection 的 template + fill-in：fill-in 子项继承模板类型。
 */
private fun parseClickTargets(views: RemoteViews): Map<Int, Boolean> {
    return runCatching {
        val actions = fieldValue(views, "mActions") as? List<*> ?: return emptyMap()
        val direct = mutableMapOf<Int, Boolean>()
        val fillIn = mutableSetOf<Int>()
        var template: Boolean? = null
        for (action in actions) {
            if (action == null) continue
            val viewId = (fieldValue(action, "viewId", "mViewId") as? Int) ?: continue
            if (viewId <= 0) continue
            when (action.javaClass.simpleName) {
                "SetOnClickPendingIntent" -> {
                    val pending = fieldValue(action, "pendingIntent") as? PendingIntent
                    if (pending != null) direct[viewId] = pending.isActivity
                }
                "SetOnClickResponse" -> {
                    val response = fieldValue(action, "mResponse", "response") ?: action
                    val pending = fieldValue(response, "mPendingIntent", "pendingIntent") as? PendingIntent
                    if (pending != null) direct[viewId] = pending.isActivity
                }
                "SetPendingIntentTemplate" -> {
                    val pending = fieldValue(
                        action,
                        "pendingIntentTemplate",
                        "mPendingIntent",
                        "pendingIntent",
                    ) as? PendingIntent
                    template = pending?.isActivity
                }
                "SetOnClickFillInIntent" -> fillIn += viewId
            }
        }
        val result = direct.toMutableMap()
        if (template != null) {
            fillIn.forEach { id -> result[id] = template }
        }
        result
    }.getOrDefault(emptyMap())
}

internal fun notifyWidgetGone(appWidgetId: Int) {
    goneListeners.toList().forEach { it.onGone(appWidgetId) }
}
/** 单个小部件加载态：Loading 与 Failed 在界面上都表现为空底，不区分用户分不清。 */
enum class WidgetLoadState {
    /** 已 createView 但首帧 RemoteViews 还没到（provider 首帧可能晚 400~800ms）。 */
    Loading,

    Ready,

    /** provider 进程僵死 / createView 抛异常，或宿主已回收这个 id。 */
    Failed,
}

/**
 * 把格子尺寸换算成 AppWidget 的 dp 选项：MIN/MAX 都下发格子实际内容尺寸。
 * provider（尤其统计类部件）普遍按 MIN_WIDTH（竖屏当前宽）决定内容布局：
 * 若 MIN 用 provider 声明的 minWidth 而格子更窄，内容会按声明宽画、底部行被
 * 裁出可视区 —— 表现为「刚添加汇总行缺失」「缩小后显示不全」。面板格子是
 * 固定尺寸，MIN == MAX 即 Launcher3 对固定 span 的语义。
 */
fun applyAppWidgetOptions(appWidgetId: Int, maxWidthDp: Int, maxHeightDp: Int) {
    val manager = AppWidgetManager.getInstance(AppContext.get())
    // 未绑定时调用会让 AppWidgetServiceImpl 抛异常，经 Parcel 回来再 NPE，直接崩进程。
    val info = manager.getAppWidgetInfo(appWidgetId)
    if (info == null) {
        Log.i(TAG, "skip options id=$appWidgetId (not bound yet)")
        return
    }
    val maxW = maxWidthDp.coerceAtLeast(1)
    val maxH = maxHeightDp.coerceAtLeast(1)
    // 部件默认内边距（widget 样式自带，Launcher3 同款补偿）：options 按「格子 − 2×padding」
    // 下发，宿主视图侧再按「格子 + 2×padding」外扩，padding 落在格子外的 gap 上。
    val pad = runCatching {
        android.appwidget.AppWidgetHostView.getDefaultPaddingForWidget(
            AppContext.get(), info.provider, null,
        )
    }.getOrDefault(android.graphics.Rect())
    val density = AppContext.get().resources.displayMetrics.density
    val padDpW = ((pad.left + pad.right) / density).toInt()
    val padDpH = ((pad.top + pad.bottom) / density).toInt()
    val contentW = (maxW - padDpW).coerceAtLeast(1)
    val contentH = (maxH - padDpH).coerceAtLeast(1)
    val options = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, contentW)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, contentH)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, contentW)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, contentH)
        // 必须声明 HOME_SCREEN 分类，否则部分 provider 会按普通 widget 的默认内边距
        // 布局，在面板里表现为四周多一圈空白、画不满格子。
        putInt(
            AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,
            AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
        )
    }
    runCatching { manager.updateAppWidgetOptions(appWidgetId, options) }
        .onFailure { Log.w(TAG, "updateAppWidgetOptions failed id=$appWidgetId: ${it.message}") }
}

private val goneListeners = mutableSetOf<WidgetGoneListener>()

internal fun addWidgetGoneListener(listener: WidgetGoneListener) {
    goneListeners += listener
}

internal fun removeWidgetGoneListener(listener: WidgetGoneListener) {
    goneListeners -= listener
}
