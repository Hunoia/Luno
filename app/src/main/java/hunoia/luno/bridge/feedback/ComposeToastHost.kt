package hunoia.luno.bridge.feedback

import androidx.annotation.StringRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

enum class ToastDuration {
    Short, Long
}

private fun getTimeMillis(duration: ToastDuration): Long {
    return when (duration) {
        ToastDuration.Short -> TOAST_SHORT
        ToastDuration.Long -> TOAST_LONG
    }
}

private var toastScope: CoroutineScope? = null

fun initToastScope(scope: CoroutineScope) {
    toastScope = scope
}

fun showComposeToast(@StringRes resId: Int, duration: ToastDuration = ToastDuration.Short) {
    toastScope?.launch {
        channel.send(ToastData(resId = resId, duration = getTimeMillis(duration)))
    }
}

fun showComposeToast(text: String, duration: ToastDuration = ToastDuration.Short) {
    toastScope?.launch {
        channel.send(ToastData(text = text, duration = getTimeMillis(duration)))
    }
}

class ToastData(
    @param:StringRes val resId: Int = 0,
    val text: String = "",
    val duration: Long = TOAST_SHORT
) {
    companion object {
        val None = ToastData()
    }

    val isEmpty: Boolean = resId == 0 && text.isEmpty()
}

internal val channel = Channel<ToastData>(Channel.CONFLATED)

private const val TOAST_SHORT = 2000L
private const val TOAST_LONG = 3500L
