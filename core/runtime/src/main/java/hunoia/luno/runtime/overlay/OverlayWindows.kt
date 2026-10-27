package hunoia.luno.runtime.overlay

import android.util.Log
import android.view.View
import android.view.WindowManager
import hunoia.luno.core.LOG_TAG

/**
 * 悬浮窗窗口操作的安全封装（Surface 基础设施）。
 *
 * 放在 `:core:runtime`：所有 Surface（面板、球、取点、识屏）共用同一套竞态处理，
 * 不允许每个 Surface 各写一份 try/catch。
 */
fun WindowManager.safeAddView(view: View?, params: WindowManager.LayoutParams): Boolean {
    val target = view ?: return false
    return runCatching { addView(target, params) }
        .onFailure { error ->
            Log.e(LOG_TAG, "overlay addView failed: ${error::class.simpleName} ${error.message}")
        }
        .isSuccess
}

/** updateViewLayout 在窗口已被系统移除（服务销毁竞态、权限被撤销）时会抛异常；这是预期竞态，记日志而非静默吞掉。 */
fun WindowManager.safeUpdateViewLayout(view: View, params: WindowManager.LayoutParams) {
    runCatching { updateViewLayout(view, params) }
        .onFailure { error ->
            Log.w(LOG_TAG, "overlay updateViewLayout failed: ${error::class.simpleName} ${error.message}")
        }
}
