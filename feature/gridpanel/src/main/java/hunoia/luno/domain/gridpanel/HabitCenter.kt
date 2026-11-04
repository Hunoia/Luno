package hunoia.luno.domain.gridpanel

import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.Habit
import hunoia.luno.config.model.HabitConfig
import kotlinx.coroutines.flow.Flow

/**
 * 习惯域门面（gridpanel 部件与管理页的唯一入口）。
 *
 * 打卡 / 增删改是应用内状态变更（配置写入），走 domain 门面写回；
 * 不走 Dispatcher / SurfaceDispatch（architecture §1.3 配置写入不是 Action）。
 */
object HabitCenter {

    val habitConfig: Flow<HabitConfig> = ConfigProvider.habitConfig

    suspend fun getHabitConfig(): HabitConfig = ConfigProvider.getHabitConfig()

    /** 部件打卡：只打卡不可取消；今天已打再调是幂等。 */
    suspend fun toggleHabit(habitId: String, day: Long) =
        ConfigProvider.toggleHabit(habitId, day)

    /** 管理页补 / 改历史（近 7 天）：统一写入 / 清除。 */
    suspend fun setHabitCheck(habitId: String, day: Long, checked: Boolean) =
        ConfigProvider.setHabitCheck(habitId, day, checked)

    suspend fun addHabit(habit: Habit) = ConfigProvider.addHabit(habit)

    suspend fun updateHabit(habit: Habit) = ConfigProvider.updateHabit(habit)

    suspend fun deleteHabit(habitId: String) = ConfigProvider.deleteHabit(habitId)
}
