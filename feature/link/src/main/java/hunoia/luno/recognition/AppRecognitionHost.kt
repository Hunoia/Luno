package hunoia.luno.recognition

import android.content.Context
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.RecognitionNotifySpec
import hunoia.luno.config.model.RecognitionSettings
import hunoia.luno.link.Links
import hunoia.luno.runtime.recognition.RecognitionHost
import hunoia.luno.runtime.recognition.RecognitionExecutor
import hunoia.luno.runtime.recognition.RecognizedItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 识别器宿主的唯一实现（`:app`）：跳页、选应用、单链接分发都收敛在这里。
 *
 * 识屏浮层（`:service` 进程的 gesture 模块）与结果胶囊（floatball 模块）都用它，
 * 因此两边的「打开链接」行为不可能出现两种写法。
 */
class AppRecognitionHost(private val context: Context) : RecognitionHost {

    override fun pickLinks(urls: List<String>) = Links.pickLinks(urls)

    override fun pickApp(url: String) = Links.pickApp(context, url)

    override fun openInBrowser(url: String) = Links.openInBrowser(context, url)

    override fun share(text: String) = Links.share(context, text)

    override suspend fun dispatchLink(url: String, ruleId: String): Boolean {
        val settings = currentSettings()
        val rule = settings.rules.firstOrNull { it.id == ruleId } ?: return false
        return Links.dispatchMatched(context, url, rule, settings)
    }

    /** 每次都读最新快照：规则改了，长驻浮层不必重启服务也能生效。 */
    private suspend fun currentSettings(): RecognitionSettings =
        runCatching { ConfigProvider.getActionSettings().recognition }
            .getOrDefault(RecognitionSettings())

    /** 「挂到超级岛」：通道 / 回退链 / 完成按钮都归 [RecognitionNotifier]。 */
    override suspend fun notifyRecognition(
        item: RecognizedItem,
        spec: RecognitionNotifySpec,
        idempotencyKey: String?,
    ): Boolean = RecognitionNotifier.post(context, item, spec, idempotencyKey)

    override fun appContext(): Context = context

    /** 进程级作用域：延迟清空剪贴板这类小事不该阻塞任何调用方。 */
    override fun scope(): CoroutineScope = processScope

    /** 执行口：与 [AppRecognitionHost] 配对的唯一实例入口。 */
    fun executor(): RecognitionExecutor = RecognitionExecutor(this)

    private companion object {
        /** 宿主生命周期与应用同长；只是「N 秒后清剪贴板」这种一次性任务的宿主。 */
        val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
