package hunoia.luno.gridpanel.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import hunoia.luno.core.AppContext

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
    override fun updateAppWidget(views: RemoteViews?) {
        if (views != null) {
            lastViewsById[appWidgetId] = views
            super.updateAppWidget(views)
            return
        }
        val cached = appWidgetId.let { lastViewsById[it] }
        if (cached != null) super.updateAppWidget(cached) else super.updateAppWidget(null)
    }

    companion object {
        private val lastViewsById = java.util.concurrent.ConcurrentHashMap<Int, RemoteViews>()

        /** 小部件 id 被回收（release）：缓存一并清掉，避免复用 id 后串内容。 */
        fun forget(appWidgetId: Int) {
            lastViewsById.remove(appWidgetId)
        }
    }
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
