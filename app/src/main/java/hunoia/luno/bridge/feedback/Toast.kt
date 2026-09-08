package hunoia.luno.bridge.feedback

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.StringRes
import androidx.core.os.postDelayed
import hunoia.luno.R

private const val DELAYED_MILLIS = 2000L

private val handler = Handler(Looper.getMainLooper())

fun showVersionTooLowToast(context: Context, @StringRes placeholder: Int = 0) {
    if (placeholder == 0) {
        showToast(context.getString(R.string.os_version_too_low))
    } else {
        val name = context.getString(placeholder)
        showToast(context.getString(R.string.os_version_too_low_placeholder, name))
    }
}

fun showToastDelay(text: String, continueBlock: () -> Unit) {
    showToast(text, DELAYED_MILLIS) {
        continueBlock()
    }
}

fun showToastLongDelay(text: String, continueBlock: () -> Unit) {
    showToastLong(text, DELAYED_MILLIS) {
        continueBlock()
    }
}

fun showToastDelay(@StringRes resId: Int, continueBlock: () -> Unit) {
    showToast(resId, DELAYED_MILLIS) {
        continueBlock()
    }
}

fun showToastLongDelay(@StringRes resId: Int, continueBlock: () -> Unit) {
    showToastLong(resId, DELAYED_MILLIS) {
        continueBlock()
    }
}

fun showToast(text: String, delayMs: Long = 0, continueBlock: (() -> Unit)? = null) {
    showComposeToast(text)
    if (delayMs > 0 && continueBlock != null) {
        handler.postDelayed(delayMs) {
            continueBlock()
        }
    }
}

fun showToastLong(text: String, delayMs: Long = 0, continueBlock: (() -> Unit)? = null) {
    showComposeToast(text, ToastDuration.Long)
    if (delayMs > 0 && continueBlock != null) {
        handler.postDelayed(delayMs) {
            continueBlock()
        }
    }
}

fun showToast(@StringRes resId: Int, delayMs: Long = 0, continueBlock: (() -> Unit)? = null) {
    showComposeToast(resId)
    if (delayMs > 0 && continueBlock != null) {
        handler.postDelayed(delayMs) {
            continueBlock()
        }
    }
}

fun showToastLong(@StringRes resId: Int, delayMs: Long = 0, continueBlock: (() -> Unit)? = null) {
    showComposeToast(resId, ToastDuration.Long)
    if (delayMs > 0 && continueBlock != null) {
        handler.postDelayed(delayMs) {
            continueBlock()
        }
    }
}
