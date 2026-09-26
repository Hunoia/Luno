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
