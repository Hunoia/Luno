package hunoia.luno.clipboard

import android.content.ClipboardManager
import hunoia.luno.core.AppContext
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.LunoLog as Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 剪贴板捕获：系统主剪贴项变化的唯一监听点。
 *
 * 之前 `FloatBallService`、`InternalClipboardWidget`、`QuickSearchActivity` 各注册一套监听 /
 * 打开时同步读，同一内容会被多路重复入库、敏感项还会从不同路径漏进历史。现在：
 *
 * - 监听只在这里注册一次，宿主是常驻的无障碍服务（`SideGestureService`，覆盖面大于球 / 面板）；
 * - 回调线程只做转发，读取与入库都在 [ClipboardCenter] 里切到 IO；
 * - 判重、自写抑制、敏感分流、容量裁剪全在 [ClipboardCenter]，本对象不含规则。
 *
 * 面板 / 球 / 页面打开时仍可各调一次 [ClipboardCenter.refresh] 补捕获（同内容幂等），
 * 用来覆盖「监听未被 ROM 投递」的机型，不构成第二套捕获逻辑。
 */
object ClipboardCapture {

    private var manager: ClipboardManager? = null
    private var listener: ClipboardManager.OnPrimaryClipChangedListener? = null

    /** 是否已注册（幂等用；注册失败保持 false，下次调用会重试）。 */
    val isRunning: Boolean get() = listener != null

    /** 注册监听；重复调用无副作用。 */
    fun start(scope: CoroutineScope) {
        if (listener != null) return
        val clipboard = runCatching {
            AppContext.get().getSystemService(ClipboardManager::class.java)
        }.getOrNull() ?: return
        val callback = ClipboardManager.OnPrimaryClipChangedListener {
            // 回调可能跑在 binder 线程：只转发，不在这里读剪贴板。
            scope.launch { runCatching { ClipboardCenter.refresh() } }
        }
        val added = runCatching { clipboard.addPrimaryClipChangedListener(callback) }
            .onFailure { Log.w(LOG_TAG, "clipboard capture register failed: ${it.message}") }
            .isSuccess
        if (added) {
            manager = clipboard
            listener = callback
            Log.i(LOG_TAG, "clipboard capture registered")
        }
    }

    /** 注销监听；宿主销毁时调用。重复调用无副作用。 */
    fun stop() {
        val clipboard = manager
        val callback = listener
        manager = null
        listener = null
        if (clipboard != null && callback != null) {
            runCatching { clipboard.removePrimaryClipChangedListener(callback) }
        }
    }
}
