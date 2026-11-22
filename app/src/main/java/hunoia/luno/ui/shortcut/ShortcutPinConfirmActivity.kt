package hunoia.luno.ui.shortcut

import android.content.pm.LauncherApps
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import hunoia.luno.R
import hunoia.luno.actions.shortcut.DynamicShortcutCenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「添加到桌面」拦截宿主：Luno 临时持默认桌面期间，系统把应用的 pin 请求发到这里。
 *
 * 交互是静默的（产品决策，2026-11-22）：有效快捷方式请求 → accept + 入库 + toast，
 * 全程无界面；非快捷方式请求（小部件）或无效请求直接 finish，前台应用不被打断。
 * accept 把 pin 记到 Luno 名下，导入复用 [DynamicShortcutCenter.importPinRequest]
 * 的同一落库形态；入口随后经 `app.launchShortcut`（startShortcut 合规通道）启动。
 */
class ShortcutPinConfirmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = intent?.getParcelableExtra(
            LauncherApps.EXTRA_PIN_ITEM_REQUEST,
            LauncherApps.PinItemRequest::class.java,
        )
        if (request == null) {
            finish()
            return
        }
        lifecycleScope.launch {
            val label = withContext(Dispatchers.IO) {
                runCatching { DynamicShortcutCenter.importPinRequest(applicationContext, request) }
                    .getOrElse { null }
            }
            if (label != null) {
                Toast.makeText(
                    applicationContext,
                    getString(R.string.dynamic_shortcuts_pin_imported, label),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            finish()
        }
    }
}
