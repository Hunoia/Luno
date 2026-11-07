package hunoia.luno.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import hunoia.luno.core.AppContext
import hunoia.luno.core.LunoLog as Log
import hunoia.luno.domain.gridpanel.HabitCenter
import hunoia.luno.domain.gridpanel.TodoCenter
import kotlinx.coroutines.flow.combine
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
        scope.launch {
            combine(HabitCenter.habitConfig, TodoCenter.todoConfig) { habit, todo -> habit to todo }
                .collect {
                    Log.i(TAG, "config change: habit=${it.first.habits.size} todo=${it.second.todos.size}")
                    runCatching { HabitWidget().updateAll(app) }
                        .onFailure { Log.w(TAG, "habit update failed: ${it.message}") }
                    runCatching { TodoWidget().updateAll(app) }
                        .onFailure { Log.w(TAG, "todo update failed: ${it.message}") }
                }
        }
    }

    private const val TAG = "WidgetSync"
}
