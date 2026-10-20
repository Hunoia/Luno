package hunoia.luno.gridpanel.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import hunoia.luno.core.AppContext

/**
 * 面板内第三方小部件的宿主（docs/grid-panel.md）。
 *
 * 为什么不用 Android 自带的 AppWidgetHost 直接实例化而要自己管生命周期：
 * - 宿主 id 必须**跨进程稳定**（:service 重启后要能接回已绑的 appWidgetId），
 *   所以 id 固定写死，不随进程生命周期变。
 * - **必须在绑定之前 startListening**：绑定的结果（首帧 RemoteViews）是广播给
 *   宿主的，监听没起来就收不到，面板会一直空白。
 * - startListening 幂等：面板常驻组合会反复请求。
 *
 * 踩过的坑：**updateAppWidgetOptions 必须在绑定确认之后调用**。未绑定时
 * AppWidgetServiceImpl 抛异常，异常经 Parcel 回来时再 NPE 一次，直接把
 * :service 进程带走。见 WidgetBindActivity 的同类注释。
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
 * 单个小部件的加载态。区分它们不是为了好看 —— Loading 与 Failed 在界面上
 * 都表现为「一块空底」，用户分不清是 provider 还没回帧、还是它已经崩了。
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
 * 把面板格子尺寸换算成 AppWidget 的 dp 选项并下发。
 *
 * MIN / MAX **故意不对称**，这是 Launcher3 的做法：
 * - MIN = provider 自己声明的 minWidth/minHeight（不再 +16）。喂小了它画不出东西，
 *   但 +16 的「余量」会让 MIN 超过实际可用宽度（尤其是小格），provider 反而挑
 *   一个画不下的布局。
 * - MAX = 我们真能给的尺寸（含 gap 的真实像素换算成 dp）。
 *
 * **MIN 必须被 MAX 夹住**：span 被缩小后格子给的 dp 可能小于 provider 的 minWidth，
 * 原样下发就是 MIN > MAX 的非法区间，provider 只能交白卷。夹成 MIN == MAX 是
 * 有损但至少一致的降级。
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
            info.minWidth.coerceAtMost(maxW),
        )
        putInt(
            AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
            info.minHeight.coerceAtMost(maxH),
        )
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, maxW)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, maxH)
        // 不声明 HOST_CATEGORY（尤其不要 HOME_SCREEN）：部分 provider 会按桌面
        // widget 的默认内边距布局，在面板里表现为四周多一圈空白。缺省分类下
        // provider 走最朴素的尺寸协商，正好符合面板格子的预期。
    }
    runCatching { manager.updateAppWidgetOptions(appWidgetId, options) }
        .onFailure { Log.w(TAG, "updateAppWidgetOptions failed id=$appWidgetId: ${it.message}") }
}

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
