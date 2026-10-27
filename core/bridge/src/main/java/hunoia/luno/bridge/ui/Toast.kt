package hunoia.luno.bridge.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes
import hunoia.luno.core.bridge.R
import hunoia.luno.core.AppContext

private val handler = Handler(Looper.getMainLooper())

/**
 * 统一走系统 Toast：悬浮窗 / 无障碍 overlay 收起时也能显示。
 * 调用方可能来自任意线程，这里统一切到主线程再弹。
 */
private fun showSystemToast(text: String, long: Boolean) {
    handler.post {
        val context = runCatching { AppContext.get() }.getOrNull() ?: return@post
        runCatching {
            Toast.makeText(
                context,
                text,
                if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
            ).show()
        }
    }
}

private fun @receiver:StringRes Int.resolveText(): String? =
    runCatching { AppContext.get().getString(this) }.getOrNull()

private fun scheduleContinue(delayMs: Long, continueBlock: (() -> Unit)?) {
    if (delayMs > 0 && continueBlock != null) {
        handler.postDelayed(continueBlock, delayMs)
    }
}

fun showVersionTooLowToast(context: Context, @StringRes placeholder: Int = 0) {
    if (placeholder == 0) {
        showToast(context.getString(R.string.os_version_too_low))
    } else {
        val name = context.getString(placeholder)
        showToast(context.getString(R.string.os_version_too_low_placeholder, name))
    }
}

fun showToast(text: String, delayMs: Long = 0, continueBlock: (() -> Unit)? = null) {
    showSystemToast(text, long = false)
    scheduleContinue(delayMs, continueBlock)
}

fun showToastLong(text: String, delayMs: Long = 0, continueBlock: (() -> Unit)? = null) {
    showSystemToast(text, long = true)
    scheduleContinue(delayMs, continueBlock)
}

fun showToast(@StringRes resId: Int, delayMs: Long = 0, continueBlock: (() -> Unit)? = null) {
    resId.resolveText()?.let { showSystemToast(it, long = false) }
    scheduleContinue(delayMs, continueBlock)
}

fun showToastLong(@StringRes resId: Int, delayMs: Long = 0, continueBlock: (() -> Unit)? = null) {
    resId.resolveText()?.let { showSystemToast(it, long = true) }
    scheduleContinue(delayMs, continueBlock)
}
