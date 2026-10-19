package hunoia.luno.gridpanel.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import hunoia.luno.core.AppContext

/**
 * 面板内第三方小部件的宿主（docs/grid-panel.md）。
 *
 * 为什么不用 Android 自带的 `AppWidgetHost` 直接实例化而要自己管生命周期：
 * - 宿主 id 必须**跨进程稳定**（`:service` 重启后要能接回已绑的 appWidgetId），
 *   所以 id 固定写死，不随进程生命周期变。
 * - **必须在绑定之前 startListening**：绑定的结果（首帧 RemoteViews）是广播给
 *   宿主的，监听没起来就收不到，面板会一直空白。
 * - `startListening` 幂等：面板常驻组合会反复请求。
 *
 * 踩过的坑记在这里：**`updateAppWidgetOptions` 必须在绑定确认之后调用**。
 * 未绑定时 AppWidgetServiceImpl 那边抛异常，异常经 Parcel 回来时再 NPE 一次，
 * 直接把 `:service` 进程带走（不是普通异常，是 RuntimeException: Unable to start
 * activity）。见 WidgetBindActivity 的同类注释。
 */
private const val TAG = "PanelAppWidget"

class PanelAppWidgetHost private constructor(
    context: Context,
    hostId: Int,
) : AppWidgetHost(context, hostId) {

    override fun onProviderChanged(appWidgetId: Int, newData: AppWidgetProviderInfo?) {
        Log.i(TAG, "provider changed id=$appWidgetId provider=${newData?.provider}")
    }

    override fun onProvidersChanged() {
        Log.i(TAG, "providers changed bound=${getAppWidgetIds().toList()}")
    }

    override fun onAppWidgetRemoved(appWidgetId: Int) {
        // provider 被卸载/用户删除：这个 id 不能再用了，通知面板把该项收掉，
        // 否则会留一个永远空白的格子。
        Log.w(TAG, "widget removed id=$appWidgetId")
        notifyWidgetGone(appWidgetId)
    }

    /** 面板里这一项被删掉时调用：释放宿主视图并回收 id。 */
    fun release(appWidgetId: Int) {
        runCatching { deleteAppWidgetId(appWidgetId) }
            .onFailure { Log.w(TAG, "deleteAppWidgetId failed id=$appWidgetId: ${it.message}") }
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

internal fun notifyWidgetGone(appWidgetId: Int) {
    goneListeners.toList().forEach { it.onGone(appWidgetId) }
}

/**
 * 单个小部件的加载态。区分它们不是为了好看 —— [Loading] 与 [Failed] 在界面上
 * 都表现为「一块空底」，用户分不清是 provider 还没回帧、还是它已经崩了，只能干等。
 */
enum class WidgetLoadState {
    /** 已 createView 但首帧 RemoteViews 还没到（provider 首帧可能晚 400~800ms）。 */
    Loading,

    /** 有内容。 */
    Ready,

    /** provider 进程僵死 / createView 抛异常，或宿主已回收这个 id。 */
    Failed,
}

/**
 * 承载单个第三方小部件的 View，包一层是为了让 Compose 的 `AndroidView` 有一个
 * 稳定类型的工厂产物，并集中管理 `updateWidget` / `releaseWidget`。
 *
 * 为什么是FrameLayout 包装而不是直接继承 AppWidgetHostView：宿主视图的那个
 * 「(context, hostId, appWidgetId)」三参构造是 @hide API —— SDK 编不过，强行绕
 * （反射/自编译 stub）会被 ROM 的非 SDK 接口黑名单拦。`AppWidgetHost.createView`
 * 本来就负责构造并挂接 RemoteViews，我们只要把它返回的视图 add 进来即可。
 *
 * 尺寸由 [updateWidget] 传入的**像素**换算成 dp 下发，不再由调用方拿单格dp 去乘
 * span —— 那样会漏掉跨格之间的 gap，实际喂给 provider 的宽度偏小、���容被裁。
 */
class PanelAppWidgetView(context: Context) : FrameLayout(context) {

    private var boundId = -1
    private var boundProvider = ""
    private var maxWidthDp = 0
    private var maxHeightDp = 0
    /** 宿主产出的真正视图；换绑/重建时换掉。 */
    private var hostChild: AppWidgetHostView? = null
    /** 首帧空的重问次数（provider 首帧 Remote_views 可能晚于 createView 到达）。 */
    private var blankRetry = 0

    /** 当前加载态，由 [setLoadStateListener] 的监听者消费（组合侧渲染提示）。 */
    var loadState: WidgetLoadState = WidgetLoadState.Loading
        private set

    private var loadStateListener: ((WidgetLoadState) -> Unit)? = null

    /** 组合侧注册；回调可能来自 createView 的同步路径，也可能来自 postDelayed。 */
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
            // 同一项但格子变大/变小：重下 options 让 provider 重新布局。
            if (sizeChanged) applyWidget(appWidgetId, provider)
            return
        }
        boundId = appWidgetId
        boundProvider = provider
        applyWidget(appWidgetId, provider)
    }

    /** 这一项被**删除**时调用（翻页/关面板不算，见 PanelAppWidgetView 的调用方注释）。 */
    fun releaseWidget() {
        runCatching { PanelAppWidgetHost.get().release(boundId) }
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
        // createView 是 AppWidgetHost 的 final 入口，内部负责 inflate RemoteViews
        // 并把 PendingIntent 接好。直接 new AppWidgetHostView 是拿不到 RemoteViews 的。
        val created = runCatching {
            PanelAppWidgetHost.get().createView(context, appWidgetId, info)
        }.getOrNull()
        if (created == null) {
            // createView抛异常（provider 进程僵死等）：给个可见的空底而不是带崩宿主
            removeAllViews()
            hostChild = null
            setLoadState(WidgetLoadState.Failed)
            return
        }
        hostChild?.let { removeView(it) }
        hostChild = created
        addView(created, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        // 防空线：create 出空壳（childCount==0）不报错也不占位 —— 真机上表现为
        //「部分小部件加完是空白」。补一层有界重问：延迟 create 一次，首帧 RemoteViews
        // 迟到的 provider 会赶上下一次 update 广播。
        if (created.childCount == 0 && blankRetry < 2) {
            blankRetry++
            // 空壳期间仍是 Loading：用户看到的是「在加载」，而不是一个不明所以的
            // 空白格子——重问还有机会成功，先别报 Failed。
            postDelayed({
                if (boundId == appWidgetId) applyWidget(appWidgetId, provider)
            }, 400L * blankRetry)
        } else if (created.childCount > 0) {
            blankRetry = 0
            setLoadState(WidgetLoadState.Ready)
        } else {
            // 重问次数用尽仍空壳：不会再来第三帧了，如实报 Failed。
            setLoadState(WidgetLoadState.Failed)
        }
    }

    companion object {
        private const val TAG = "PanelAppWidget"
    }
}

/**
 * 把面板格子尺寸换算成 AppWidget 的 dp 选项并下发。
 *
 * MIN / MAX **故意不对称**，这是 Launcher3 的做法，也是唯一说得通的：
 * - MIN = provider 自己声明的 `minWidth/minHeight`（+ padding）。告诉它「至少要这么大
 *   才画得出来」。**喂小了一定画不出东西**：实测 WPS 那个 widget 按它声明的约
 *   300×110dp 能正常渲染，给 320×300dp 反而画出空白。
 * - MAX = 我们真能给的上限（span × 单格 dp）。它自己决定用多少，于是同一张 widget
 *   放 1×1 也能画，只是 provider 会挑简版布局。
 *
 * 两边都给同一个值（曾经的做法）等于告诉它「你必须正好这么大」，span 一旦和它
 * 声明的尺寸对不上就变成喂小 —— 正是上面那个空白的成因。
 *
 * **MIN 必须被 MAX 夹住**：span 被缩小（或网格被改小）后，格子给的 dp 可能已经小于
 * provider 声明的 minWidth。此时原样下发就是 MIN > MAX 的非法区间，provider 拿不到
 * 能画的尺寸，只能交白卷 —— 表现是「格子还在、内容空了」，比直接删掉这一项更难
 * 排查。夹到 MIN == MAX 是有损但至少一致的降级（provider 会挑它的紧凑布局）。
 */
fun applyAppWidgetOptions(appWidgetId: Int, maxWidthDp: Int, maxHeightDp: Int) {
    val manager = AppWidgetManager.getInstance(AppContext.get())
    // 未绑定时调用会让 AppWidgetServiceImpl 抛异常，异常经 Parcel 回来再 NPE 一次，
    // 直接崩进程 —— 所以先确认已绑定。
    val info = manager.getAppWidgetInfo(appWidgetId)
    if (info == null) {
        Log.i(TAG, "skip options id=$appWidgetId (not bound yet)")
        return
    }
    val maxW = maxWidthDp.coerceAtLeast(1)
    val maxH = maxHeightDp.coerceAtLeast(1)
    val options = Bundle().apply {
        putInt(
            AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
            (info.minWidth + WIDGET_PADDING_DP).coerceAtMost(maxW),
        )
        putInt(
            AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
            (info.minHeight + WIDGET_PADDING_DP).coerceAtMost(maxH),
        )
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, maxW)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, maxH)
    }
    runCatching { manager.updateAppWidgetOptions(appWidgetId, options) }
        .onFailure { Log.w(TAG, "updateAppWidgetOptions failed id=$appWidgetId: ${it.message}") }
}

/** provider 声明的尺寸之外，四周各留这么多 dp 内边距。 */
private const val WIDGET_PADDING_DP = 16

/** provider 字符串的规范化形式（ComponentName 的 flatten 形式）。 */
fun providerToString(component: ComponentName): String = component.flattenToString()

fun providerFromString(flattened: String): ComponentName? =
    ComponentName.unflattenFromString(flattened)

/** 宿主回调「小部件没了」→ 面板收窄的通道。由组合侧注册。 */
private val goneListeners = mutableSetOf<WidgetGoneListener>()

internal fun addWidgetGoneListener(listener: WidgetGoneListener) {
    goneListeners += listener
}

internal fun removeWidgetGoneListener(listener: WidgetGoneListener) {
    goneListeners -= listener
}