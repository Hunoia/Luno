package hunoia.luno.bridge.ui

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes
import hunoia.luno.config.model.ToastPresentation
import hunoia.luno.core.AppContext

private val handler = Handler(Looper.getMainLooper())

/**
 * 系统 Toast 提示的可配置呈现器（由组装根注入）。
 *
 * 呈现机制（系统 Toast / 通知 / 实时 / 岛 / 胶囊）是平台策略，变化原因与具体 feature
 * 无关（同一轴上的 [hunoia.luno.config.model.ToastPresentation]），所以契约放这里
 * （bridge 是平台边界），实现在 `:app`（组装根，读通知设置 + 用 `:capability:notify`
 * 的 [hunoia.luno.core.domain.NotificationPresenter] 契约呈现）。未注入时保持系统 Toast。
 */
fun interface ToastPresenter {
    /**
     * 呈现一条提示；[mode] 非空 = 强制该呈现方式，null = 读全局配置分派
     * （toast / 标准 / 实时 / 岛 / 胶囊）。实现由组装根注入。
     */
    fun present(text: String, mode: ToastPresentation?)
}

/** 注入点：组装根启动时设置；null = 保持系统 Toast。 */
@Volatile
private var toastPresenter: ToastPresenter? = null

fun setToastPresenter(presenter: ToastPresenter?) {
    toastPresenter = presenter
}

/**
 * presenter 的 TOAST 分支用：直接弹系统 Toast（悬浮窗 / 无障碍 overlay 收起时也能显示）。
 * 供 [ConfigurableToastPresenter] 在配置为系统 Toast 时回退。
 */
fun showSystemToastCompat(text: String, long: Boolean = false) {
    showSystemToast(text, long)
}

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

/**
 * 优先走可配置呈现器（用户把 toast 设为其它通知方式时）；
 * 未注入 / 未设置时回退系统 Toast。
 */
private fun presentToast(text: String, long: Boolean, presentation: ToastPresentation? = null) {
    val presenter = toastPresenter
    if (presenter != null) {
        presenter.present(text, presentation)
        return
    }
    showSystemToast(text, long)
}

private fun @receiver:StringRes Int.resolveText(): String? =
    runCatching { AppContext.get().getString(this) }.getOrNull()

private fun scheduleContinue(delayMs: Long, continueBlock: (() -> Unit)?) {
    if (delayMs > 0 && continueBlock != null) {
        handler.postDelayed(continueBlock, delayMs)
    }
}

fun showToast(
    text: String,
    delayMs: Long = 0,
    continueBlock: (() -> Unit)? = null,
    presentation: ToastPresentation? = null,
) {
    presentToast(text, long = false, presentation)
    scheduleContinue(delayMs, continueBlock)
}

fun showToastLong(
    text: String,
    delayMs: Long = 0,
    continueBlock: (() -> Unit)? = null,
    presentation: ToastPresentation? = null,
) {
    presentToast(text, long = true, presentation)
    scheduleContinue(delayMs, continueBlock)
}

fun showToast(@StringRes resId: Int, delayMs: Long = 0, continueBlock: (() -> Unit)? = null) {
    resId.resolveText()?.let { presentToast(it, long = false) }
    scheduleContinue(delayMs, continueBlock)
}

fun showToastLong(@StringRes resId: Int, delayMs: Long = 0, continueBlock: (() -> Unit)? = null) {
    resId.resolveText()?.let { presentToast(it, long = true) }
    scheduleContinue(delayMs, continueBlock)
}
