package hunoia.luno.bridge.system

import android.app.ActivityManager
import android.content.Context

/**
 * 只操作 Luno 自己的某个 task 是否出现在最近任务。
 *
 * 调用方传 Activity 的 taskId，避免按组件 / 启动 Intent 猜测把图片标注等独立 task 一起隐藏。
 */
object OwnRecentTask {

    /** 返回是否找到并成功设置指定 task；找不到任务或平台拒绝时返回 false。 */
    fun setTaskExcluded(context: Context, taskId: Int, exclude: Boolean): Boolean {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return false
        return runCatching {
            val tasks = manager.appTasks
            val task = tasks.firstOrNull { appTask ->
                appTask.taskInfo?.taskId == taskId
            }
            task?.setExcludeFromRecents(exclude)
            task != null
        }.getOrDefault(false)
    }
}
