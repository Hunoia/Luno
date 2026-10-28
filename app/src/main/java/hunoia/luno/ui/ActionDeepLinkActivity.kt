package hunoia.luno.ui

import hunoia.luno.domain.AppGraph

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import hunoia.luno.R
import hunoia.luno.action.model.ActionDeepLink
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.domain.action.ActionLibrary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 外部调用入口：`luno://action/{entryId}`。
 * 透明、无 UI，解析出动作库条目后交给 :service 分发；找不到条目或服务未就绪时给提示。
 */
class ActionDeepLinkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        val entryId = ActionDeepLink.entryId(uri)
        val inlineAction = ActionDeepLink.action(uri)
        lifecycleScope.launch {
            val action = inlineAction ?: entryId?.let { id ->
                ActionLibrary.getSettings().entries.firstOrNull { it.id == id }?.storedAction
            }
            when {
                action == null -> showToast(R.string.action_deep_link_not_found)
                !dispatchWithRetry(action) ->
                    showToast(R.string.dispatch_service_not_ready)
            }
            finish()
        }
    }

    /** App.onCreate 的 bindService 是异步的，首次联动可能还没连上，短轮询兜一下。 */
    private suspend fun dispatchWithRetry(action: hunoia.luno.action.model.StoredAction): Boolean {
        repeat(10) {
            if (AppGraph.runtime.dispatch(action)) return true
            delay(100)
        }
        return false
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
