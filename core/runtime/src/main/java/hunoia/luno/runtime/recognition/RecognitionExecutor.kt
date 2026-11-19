package hunoia.luno.runtime.recognition

import hunoia.luno.core.LunoLog as Log
import hunoia.luno.bridge.system.ClipboardContent
import hunoia.luno.clipboard.ClipboardCenter
import hunoia.luno.config.model.LinkMissMode
import hunoia.luno.config.model.RecognitionBuiltin
import hunoia.luno.config.model.RecognitionMatch
import hunoia.luno.config.model.RecognitionOutput
import hunoia.luno.config.model.RecognitionNotifySpec
import hunoia.luno.config.model.RecognitionRule
import hunoia.luno.config.model.RecognitionSettings
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.domain.ActionRequest
import hunoia.luno.core.domain.DispatchOrigin
import hunoia.luno.runtime.dispatch.ActionDispatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLEncoder

/**
 * 识别器输出交给宿主做的事：跳页、挑应用、单链接分发。
 *
 * 实现留在 `:app`（它认识组件与 IPC），`:core:runtime` 只声明契约——
 * 识别器不能反向依赖任何 feature / app 模块。
 */
interface RecognitionHost {

    /** 多条链接的选择页。 */
    fun pickLinks(urls: List<String>)

    /** 「选择应用打开」页。 */
    fun pickApp(url: String)

    /** 用浏览器直接打开（实现侧负责避开 Luno 自己，并优先 [preferredPackage]）。 */
    fun openInBrowser(url: String, preferredPackage: String)

    /** 系统分享面板。 */
    fun share(text: String)

    /** 按规则的目标应用分发单条链接；返回是否确认送达。 */
    suspend fun dispatchLink(url: String, ruleId: String): Boolean

    /** 把识别结果挂到超级岛 / 通知栏（通道、回退链、点击行为都由宿主决定）。 */
    suspend fun notifyRecognition(
        item: RecognizedItem,
        spec: RecognitionNotifySpec,
        idempotencyKey: String?,
    ): Boolean

    /** 延迟任务的作用域（进程级）；不提供则「复制后清除」不生效。 */
    fun scope(): kotlinx.coroutines.CoroutineScope
}

/**
 * 识别结果的统一执行口。
 *
 * 内置输出保留各入口原本的手感（敏感复制、多链接选择页、未命中回退），动作输出走
 * 既有 `ActionRequest → Dispatcher`。**消费者不再各自写执行分支**：胶囊、识屏工具条、
 * 外部 Intent 入口都只调 [execute]，执行完自己决定要不要弹提示。
 */
class RecognitionExecutor(private val host: RecognitionHost) {

    /**
     * 执行一条识别结果，返回是否确认落地（剪贴板写成功 / 选择页拉起 / 动作送达）。
     */
    suspend fun execute(
        item: RecognizedItem,
        settings: RecognitionSettings,
        label: String?,
        idempotencyKey: String? = null,
    ): Boolean = when (val output = item.output) {
        is RecognitionOutput.Builtin -> executeBuiltin(item, output.kind, settings, label, idempotencyKey)
        is RecognitionOutput.Action -> executeAction(item, output, idempotencyKey)
        is RecognitionOutput.Notify -> notify(item, output.spec, idempotencyKey)
    }

    private suspend fun executeBuiltin(
        item: RecognizedItem,
        kind: RecognitionBuiltin,
        settings: RecognitionSettings,
        label: String?,
        idempotencyKey: String?,
    ): Boolean = when (kind) {
        RecognitionBuiltin.Copy -> copy(item, label)
        RecognitionBuiltin.CopyAndClear -> {
            if (copy(item, label)) {
                scheduleClipboardClear(item.value, settings.copyClearDelaySeconds)
                true
            } else {
                false
            }
        }
        RecognitionBuiltin.Share -> share(item)
        RecognitionBuiltin.OpenLink -> {
            // 捕获值不是链接：OPEN 退回复制，与旧口径一致（规则写错不该让点击无反应）。
            if (item.urls.isEmpty()) copy(item, label) else openLink(item, settings)
        }
    }

    /**
     * 「复制后自动清除」：延迟到了只清**我们写入的那一段**。
     *
     * 用户中途复制了别的东西时什么都不做——那才是最常见的多任务场景，误清等于把用户的
     * 剪贴板偷走。窗口很小（[RecognitionSettings.copyClearDelaySeconds]），挂在进程级
     * 作用域上，不阻塞调用方。读取与清空都经 [ClipboardCenter]（后台读不到走 Shizuku）。
     */
    private fun scheduleClipboardClear(text: String, delaySeconds: Int) {
        if (delaySeconds <= 0) return
        host.scope().launch {
            delay(delaySeconds * 1_000L)
            val cleared = ClipboardCenter.clearIfUnchanged(text)
            if (cleared) Log.i(LOG_TAG, "clipboard cleared after ${delaySeconds}s")
        }
    }

    /** 复制：一律进历史（Luno 内部不再区分敏感与否），写入走统一的剪贴板入口。 */
    suspend fun copy(item: RecognizedItem, label: String?): Boolean = withContext(Dispatchers.IO) {
        val written = ClipboardCenter.write(ClipboardContent.Text(item.value), label = label)
        if (!written) Log.w(LOG_TAG, "recognition copy failed")
        written
    }

    /** 超级岛 / 通知输出：交给宿主（`:app` 才有通道与回退链），本层只做占位替换。 */
    private suspend fun notify(
        item: RecognizedItem,
        spec: RecognitionNotifySpec,
        idempotencyKey: String?,
    ): Boolean = host.notifyRecognition(item, spec, idempotencyKey)

    private fun share(item: RecognizedItem): Boolean {
        val value = if (item.urls.size > 1) item.urls.joinToString("\n") else item.value
        if (value.isBlank()) return false
        host.share(value)
        return true
    }

    /**
     * 打开链接：多链接进选择页；单链接按规则分发，未命中 / 未送达按回退策略处置。
     *
     * 回退顺序与旧链接分发一致：命中规则可自带自己的未命中策略，否则跟随全局。
     */
    private suspend fun openLink(item: RecognizedItem, settings: RecognitionSettings): Boolean {
        val urls = item.urls
        if (urls.size > 1) {
            host.pickLinks(urls)
            return true
        }
        val url = urls.first()
        // 只有链接规则能带分发目标；文本规则命中的链接按「未命中」处理。
        val matched = LinkUrls.firstMatch(url, settings.rules)
        val dispatched = if (matched != null) host.dispatchLink(url, matched.id) else false
        if (dispatched) return true
        // 命中规则却没发出去 = 分发失败；完全没命中 = 未命中。两者是不同的回退口径。
        fallback(url, matched, settings)
        return false
    }

    /**
     * 回退：规则自带的优先，否则跟随全局 [RecognitionSettings.fallback]。
     *
     * 「没命中」与「命中但打不开」走同一个兜底——对用户是同一件事（点了没反应）。
     */
    suspend fun fallback(url: String, rule: RecognitionRule?, settings: RecognitionSettings) {
        val own = (rule?.match as? RecognitionMatch.Link)?.missAction
        val mode = own ?: settings.fallback
        when (mode) {
            LinkMissMode.SHARE -> host.share(url)
            LinkMissMode.BROWSER -> host.openInBrowser(url, settings.browserPackage)
            LinkMissMode.PICKER -> host.pickApp(url)
        }
    }

    /** 动作输出：把捕获值替换进参数，再交给统一执行口。 */
    private suspend fun executeAction(
        item: RecognizedItem,
        output: RecognitionOutput.Action,
        idempotencyKey: String?,
    ): Boolean {
        val request = ActionRequest(
            stored = output.action.fillRecognition(item),
            origin = DispatchOrigin.UNKNOWN,
            idempotencyKey = idempotencyKey,
        )
        return runCatching { ActionDispatch.dispatchAwait(request) }
            .onFailure {
                Log.w(LOG_TAG, "recognition action dispatch failed: ${output.action.typeId}", it)
            }
            .getOrDefault(false)
    }
}

/**
 * 把识别结果替换进动作参数。
 *
 * 变量口径与搜索启动的 `fillSearchQuery` 一致，只是多认了识别器自己的几个：
 * `%s`（URL 编码后的值）/ `%q`（原样值）/ `{value}` / `{url}` / `{host}` / `{rule}`。
 * 未识别的占位符原样保留，非字符串参数不动。
 */
fun hunoia.luno.core.domain.StoredAction.fillRecognition(item: RecognizedItem): hunoia.luno.core.domain.StoredAction {
    val values = buildMap {
        put("%s", urlEncode(item.value))
        put("%q", item.value)
        put("{value}", item.value)
        put("{url}", item.urls.firstOrNull().orEmpty())
        put("{host}", item.urls.firstOrNull()?.let { LinkUrls.hostOf(it) }.orEmpty())
        put("{rule}", item.ruleId)
    }
    return copy(params = params.fillRecognition(values) as JsonObject)
}

private fun JsonElement.fillRecognition(values: Map<String, String>): JsonElement = when (this) {
    is JsonPrimitive -> if (isString) JsonPrimitive(replaceAll(content, values)) else this
    is JsonObject -> JsonObject(mapValues { (_, value) -> value.fillRecognition(values) })
    is JsonArray -> JsonArray(map { it.fillRecognition(values) })
}

private fun replaceAll(text: String, values: Map<String, String>): String {
    var out = text
    values.forEach { (token, value) -> out = out.replace(token, value) }
    return out
}

private fun urlEncode(value: String): String =
    runCatching { URLEncoder.encode(value, "UTF-8") }.getOrDefault(value)


/**
 * 未接线的宿主：`:core:runtime` 里需要「有个默认值」，但真正实现由宿主注入。
 *
 * 所有跳页 / 分发都是空实现，因此默认值下识别器只会执行内置的「复制」，
 * 链接类输出静默降级——比抛异常更适合浮层这类没有兜底 UI 的调用点。
 */
object RecognitionHosts {

    @Volatile
    private var installed: RecognitionHost? = null

    /** 由 `:app` 在进程启动时安装一次；传 null 复位（测试 / 服务销毁）。 */
    fun install(host: RecognitionHost?) {
        installed = host
        executorCache = host?.let { RecognitionExecutor(it) }
    }

    @Volatile
    private var executorCache: RecognitionExecutor? = null

    /** 当前执行口；没安装时退回 [UnknownRecognitionHost]（链接类输出静默降级）。 */
    val executor: RecognitionExecutor
        get() = executorCache ?: RecognitionExecutor(UnknownRecognitionHost)
}

object UnknownRecognitionHost : RecognitionHost {
    override fun pickLinks(urls: List<String>) = Unit
    override fun pickApp(url: String) = Unit
    override fun openInBrowser(url: String, preferredPackage: String) = Unit
    override fun share(text: String) = Unit
    override suspend fun dispatchLink(url: String, ruleId: String): Boolean = false

    override suspend fun notifyRecognition(
        item: RecognizedItem,
        spec: RecognitionNotifySpec,
        idempotencyKey: String?,
    ): Boolean = false

    override fun scope(): kotlinx.coroutines.CoroutineScope =
        kotlinx.coroutines.MainScope()
}
