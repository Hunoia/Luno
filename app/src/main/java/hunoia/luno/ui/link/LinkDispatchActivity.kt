package hunoia.luno.ui.link

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import hunoia.luno.core.LunoLog as Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import hunoia.luno.R
import hunoia.luno.bridge.system.ClipboardContent
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.LinkDispatchSettings
import hunoia.luno.core.LOG_TAG
import hunoia.luno.link.Links
import hunoia.luno.link.LinkUrls
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.clipboard.ClipboardCenter
import kotlinx.coroutines.launch

/**
 * 链接接收口：Luno 被设为默认浏览器后，其它应用点开的 http(s) 链接、以及「分享到 Luno」
 * 的文本都会进这里，再按 [LinkDispatchSettings] 的规则分发到目标应用。
 *
 * 全程无 UI、立即 finish（透明 + 无动画）：命中规则直达目标应用；未命中按
 * `missAction` 回退（分享面板 / 其他浏览器 / 选择应用）；多条链接转
 * [LinkSelectionActivity] 让用户挑。
 */
class LinkDispatchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    /** singleTop 复用：连点多条链接时不丢后面的事件。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        // 分享图片：直接存进剪贴板并固定。文本分享仍先走链接识别，非链接文本同样存入。
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            lifecycleScope.launch {
                val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                val saved = uri != null && ClipboardCenter.saveShared(
                    ClipboardContent.Image(uri, intent.type ?: "image/*"),
                )
                showToast(
                    if (saved) R.string.share_clipboard_saved else R.string.share_clipboard_failed,
                )
                finish()
            }
            return
        }
        val text = intentText(intent)
        if (text.isNullOrBlank()) {
            finish()
            return
        }
        lifecycleScope.launch {
            val settings = runCatching { TuneConfig.getAction().linkDispatch }
                .onFailure { Log.w(LOG_TAG, "link dispatch: read settings failed: ${it.message}") }
                .getOrDefault(LinkDispatchSettings())
            val urls = LinkUrls.extractUrls(text)
            when {
                // 分享进来的纯文本没有链接：存进剪贴板并固定；VIEW 进来的无链接文本仍提示。
                urls.isEmpty() -> {
                    if (intent?.action == Intent.ACTION_SEND) {
                        val saved = ClipboardCenter.saveShared(ClipboardContent.Text(text))
                        showToast(
                            if (saved) R.string.share_clipboard_saved else R.string.share_clipboard_failed,
                        )
                    } else {
                        showToast(R.string.link_dispatch_no_link)
                    }
                }
                urls.size > 1 -> startActivity(LinkSelectionActivity.intent(this@LinkDispatchActivity, urls))
                else -> {
                    val url = urls.first()
                    val rule = if (settings.enabled) {
                        LinkUrls.firstMatch(url, settings.rules)
                    } else {
                        null
                    }
                    // 交给「规则表级」分发：命中规则若开了短链解析，会在内部跟随后重新匹配。
                    if (rule == null ||
                        !Links.dispatch(this@LinkDispatchActivity, url, settings)
                    ) {
                        Links.fallbackFor(
                            this@LinkDispatchActivity,
                            url,
                            rule,
                            settings,
                        )
                    }
                }
            }
            finish()
        }
    }

    /** VIEW 取 data；分享（ACTION_SEND）取 EXTRA_TEXT。 */
    private fun intentText(intent: Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> intent.dataString ?: intent.getStringExtra(Intent.EXTRA_TEXT)
        }
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        fun intent(context: Context, url: String): Intent =
            Intent(context, LinkDispatchActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = android.net.Uri.parse(url)
            }
    }
}
