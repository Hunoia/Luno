package hunoia.luno.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import hunoia.luno.core.AppContext
import hunoia.luno.core.LunoLog as Log
import hunoia.luno.domain.gridpanel.HabitCenter
import hunoia.luno.domain.gridpanel.TodoCenter
import kotlinx.coroutines.launch

/**
 * 桌面小部件实时同步：订阅习惯 / 待办配置流，任一变化就重绘所有相关部件。
 *
 * 进程被回收后由系统周期更新（updatePeriodMillis）+ 开机广播兜底；本单例在进程
 * 存活期间保证「应用内 / 面板打卡 → 部件立即变」。数据流是 MultiProcess DataStore，
 * 部件在主进程可直接订阅（与面板/应用同一份配置）。
 *
 * scope 挂在 `AppContext.applicationScope`（architecture §191：进程级唯一宿主 scope，
 * 不自建长生命周期 scope）。`AppContext` 未初始化（极早期 onUpdate）时跳过本次，
 * 下一次 onUpdate 再拉起。
 */
object WidgetSync {

    @Volatile
    private var started = false

    /** 首次有部件启用时启动（幂等）；进程重启后由下一次 onUpdate 再拉起。 */
    fun ensureStarted(context: Context) {
        // WorkManager 只在主进程自动初始化（多进程需手动），Glance 更新依赖 WorkManager；
        // 非主进程（:service 面板服务）启动订阅会在刷新部件时报 WorkManager 未初始化。
        // 面板写入经 MultiProcess DataStore 通知主进程订阅流，主进程负责重绘即可。
        // 注意用 Application.getProcessName()：applicationInfo.processName 是清单里的主进程名，
        // 在 :service 里也不会变，拿它判断等于永远「是主进程」。
        val processName = Application.getProcessName()
        if (processName != context.packageName) {
            Log.i(TAG, "ensureStarted: skip non-main process $processName")
            return
        }
        Log.i(TAG, "ensureStarted: started=$started scope=${AppContext.applicationScope != null}")
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
        }
        val scope = AppContext.applicationScope ?: run {
            Log.w(TAG, "ensureStarted: AppContext not ready, will retry")
            // AppContext 尚未初始化：复位标记，下次 onUpdate 重试。
            started = false
            return
        }
        val app = context.applicationContext
        Log.i(TAG, "ensureStarted: subscribing on applicationScope")
        // 分开订阅：习惯变化只重绘习惯部件，待办变化只重绘待办部件。
        // 两边都刷新会让每次改动都碰一遍另一个 provider。
        scope.launch {
            HabitCenter.habitConfig.collect {
                Log.i(TAG, "habit config change: habits=${it.habits.size}")
                updateHabitWidgets(app)
            }
        }
        scope.launch {
            TodoCenter.todoConfig.collect {
                Log.i(TAG, "todo config change: todos=${it.todos.size}")
                updateTodoWidgets(app)
            }
        }
    }

    /** 刷新全部习惯部件。 */
    internal suspend fun updateHabitWidgets(context: Context) {
        updateWidgets(context, HabitWidgetReceiver::class.java, HabitWidget())
    }

    /** 刷新全部待办部件。 */
    internal suspend fun updateTodoWidgets(context: Context) {
        updateWidgets(context, TodoWidgetReceiver::class.java, TodoWidget())
    }

    /**
     * 系统深浅色切换后主动重绘两块部件。
     *
     * 资源色 / DayNightColorProvider 正常情况下由宿主重新 apply RemoteViews 即时生效；
     * 这里兜住不主动重绘的启动器，也保证固定色（如图片 tint）随本次重绘一起更新。
     * 只在主进程调用（WorkManager 只在主进程自动初始化）。
     */
    fun refreshAll(context: Context) {
        val app = context.applicationContext
        AppContext.applicationScope?.launch {
            updateHabitWidgets(app)
            updateTodoWidgets(app)
        }
    }

    /**
     * 按 **receiver 组件**枚举实例并逐个 `update`，不用 `GlanceAppWidget.updateAll`。
     *
     * `updateAll` 走 Glance 持久化的「provider 类名 → receiver」映射；该映射只在首次写入，
     * 之后不主动刷新（`getGlanceIds` 只认存下来的类名字符串）。release 升级、R8 类合并后
     * 旧映射会把 A 部件的 id 交给 B 部件，实测就是习惯部件自己变成待办。
     * 组件枚举来自 `AppWidgetManager`，不依赖那份缓存。
     *
     * receiver 类名在 `app/proguard-rules.pro` 里显式 keep（清单声明 + 类名参与映射）。
     */
    private suspend fun updateWidgets(
        context: Context,
        receiver: Class<out GlanceAppWidgetReceiver>,
        widget: GlanceAppWidget,
    ) {
        val component = ComponentName(context, receiver)
        val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(component)
        if (ids.isEmpty()) return
        val glanceManager = GlanceAppWidgetManager(context)
        for (id in ids) {
            runCatching { widget.update(context, glanceManager.getGlanceIdBy(id)) }
                .onFailure { Log.w(TAG, "update failed: ${receiver.simpleName}#$id ${it.message}") }
        }
    }

    private const val TAG = "WidgetSync"
}
