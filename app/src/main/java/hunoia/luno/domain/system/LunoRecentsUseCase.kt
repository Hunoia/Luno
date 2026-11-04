package hunoia.luno.domain.system

import android.content.Context
import hunoia.luno.bridge.system.OwnRecentTask
import hunoia.luno.config.ConfigProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Luno 主页面在最近任务中的显隐。
 *
 * 只作用于启动器拉起的主 task；图片标注等独立任务不受影响。其他应用不在这里控制。
 */
object LunoRecentsUseCase {

    val hideFromRecents: Flow<Boolean> = ConfigProvider.advancedSettings
        .map { it.hideLunoFromRecents }
        .distinctUntilChanged()

    suspend fun setHidden(hidden: Boolean) {
        ConfigProvider.updateAdvancedSettings { current ->
            current.copy(hideLunoFromRecents = hidden)
        }
    }

    /** 立即把当前主 task 设为隐藏 / 显示；找不到任务时返回 false。 */
    fun apply(context: Context, taskId: Int, hidden: Boolean): Boolean =
        OwnRecentTask.setTaskExcluded(context, taskId, hidden)
}
