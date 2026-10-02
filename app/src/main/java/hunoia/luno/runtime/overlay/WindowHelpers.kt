package hunoia.luno.runtime.overlay

import android.util.Log
import android.view.View
import android.view.WindowManager

internal fun WindowManager.safeAddView(view: View?, params: WindowManager.LayoutParams): Boolean {
    val target = view ?: return false
    return runCatching { addView(target, params) }
        .onFailure { error ->
            Log.e("LunoLauncher", "overlay addView failed: ${error::class.simpleName} ${error.message}")
        }
        .isSuccess
}

/**
 * updateViewLayout 在窗口已被系统移除（服务销毁竞态、权限被撤销）时会抛
 * BadTokenException / IllegalArgumentException。这属于预期内的竞态，记日志而不是静默吞掉。
 */
internal fun WindowManager.safeUpdateViewLayout(view: View, params: WindowManager.LayoutParams) {
    runCatching { updateViewLayout(view, params) }
        .onFailure { error ->
            Log.w("LunoLauncher", "overlay updateViewLayout failed: ${error::class.simpleName} ${error.message}")
        }
}

internal fun WindowManager.safeRemoveViewImmediate(view: View) {
    runCatching { removeViewImmediate(view) }
        .onFailure { error ->
            Log.w("LunoLauncher", "overlay removeViewImmediate failed: ${error::class.simpleName} ${error.message}")
        }
}
