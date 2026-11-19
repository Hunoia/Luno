package hunoia.luno.runtime.floatball.result

import hunoia.luno.config.model.RecognitionBuiltin
import hunoia.luno.config.model.RecognitionOutput
import hunoia.luno.config.model.RecognitionSource
import hunoia.luno.core.domain.NotificationPulse
import hunoia.luno.runtime.floatball.inbox.ResultItem
import hunoia.luno.runtime.floatball.inbox.ResultPrimaryAction
import hunoia.luno.runtime.recognition.CompiledRecognizer
import hunoia.luno.runtime.recognition.RecognitionInput
import hunoia.luno.runtime.recognition.RecognizedItem

/**
 * 结果项构造：识别器结果 → 结果胶囊条目。
 *
 * **这里不判任何规则**：匹配、链接挑选、敏感判定都在 `:core:runtime` 的 `Recognizer`
 * 里做完了，本对象只做「识别结果 → 收件箱状态」的纯投影（方便 JVM 单测）。
 * 结果该直执行还是进胶囊由 `RecognitionDelivery` 判，本对象不参与。
 */
internal object ResultFactory {

    private const val GenericLabel = "结果"

    /** 通知：标题 + 正文（bigText 存在时优先，避免正文与展开文本重复）。 */
    fun matchNotification(
        pulse: NotificationPulse,
        recognizer: CompiledRecognizer,
        sourceKey: String,
    ): List<RecognizedItem> = match(
        source = RecognitionSource.Notification,
        text = notificationText(pulse),
        recognizer = recognizer,
        sourceKey = sourceKey,
    )

    /** 剪贴板：规则命中 + 内置链接（两者都由识别器同一次匹配给出）。 */
    fun matchClipboard(
        text: String,
        recognizer: CompiledRecognizer,
        sourceKey: String,
    ): List<RecognizedItem> = match(
        source = RecognitionSource.Clipboard,
        text = text,
        recognizer = recognizer,
        sourceKey = sourceKey,
    )

    /**
     * 情景识别（当前界面文本）：只认识别器结果。
     *
     * 「界面上链接太多、只有命中分发规则才提示」这个旧闸门现在由规则表自己表达
     * （内置「链接」规则默认对 `Scene` 生效；用户可从该来源摘掉它，或依赖具体应用规则），
     * 代码里不再有 `linkRules.isNotEmpty()` 之类的隐式判断。
     *
     * [sceneUrl] 是特权通道（Shizuku 读 XWeb 日志）拿到的当前页真实链接：它只是多一段
     * 链接候选，直接拼进待识别文本，判不判仍由规则说了算。
     */
    fun matchScene(
        text: String,
        screenKey: String,
        recognizer: CompiledRecognizer,
        sceneUrl: String? = null,
    ): List<RecognizedItem> {
        val scan = if (sceneUrl.isNullOrBlank()) text else "$text\n$sceneUrl"
        if (scan.isBlank()) return emptyList()
        return match(
            source = RecognitionSource.Scene,
            text = scan,
            recognizer = recognizer,
            sourceKey = "scene:$screenKey",
        )
    }

    /**
     * 情景识别补到真实链接后的重算：只挑出与这段真实链接相关的那一条。
     *
     * 返回 null = 这条链接没有命中任何链接规则（调用方什么都不用做）。
     */
    fun sceneLinkItem(
        text: String,
        screenKey: String,
        recognizer: CompiledRecognizer,
        sceneUrl: String?,
        excludeUrls: Set<String> = emptySet(),
    ): RecognizedItem? {
        if (sceneUrl.isNullOrBlank() || sceneUrl in excludeUrls) return null
        return matchScene(text, screenKey, recognizer, sceneUrl).firstOrNull { item ->
            item.urls.any { it == sceneUrl } && item.urls.all { it !in excludeUrls }
        }
    }

    /** 一次匹配：识别器给什么就是什么，顺序即规则表顺序。 */
    fun match(
        source: RecognitionSource,
        text: String,
        recognizer: CompiledRecognizer,
        sourceKey: String,
    ): List<RecognizedItem> = recognizer.match(RecognitionInput(source, text, sourceKey))

    // ---- 投影：识别结果 → 收件箱条目（纯胶囊档路径 / 测试用） ----

    fun fromNotification(
        pulse: NotificationPulse,
        recognizer: CompiledRecognizer,
        sourceKey: String,
        nowMs: Long,
    ): List<ResultItem> = matchNotification(pulse, recognizer, sourceKey)
        .map { item -> toResultItem(item, sourceKey, nowMs, sceneKey = null) }

    fun fromClipboard(
        text: String,
        recognizer: CompiledRecognizer,
        sourceKey: String,
        nowMs: Long,
    ): List<ResultItem> = matchClipboard(text, recognizer, sourceKey)
        .map { item -> toResultItem(item, sourceKey, nowMs, sceneKey = null) }

    fun fromScene(
        text: String,
        screenKey: String,
        recognizer: CompiledRecognizer,
        nowMs: Long,
        sceneUrl: String? = null,
    ): List<ResultItem> = matchScene(text, screenKey, recognizer, sceneUrl)
        .map { item -> toResultItem(item, "scene:$screenKey", nowMs, sceneKey = screenKey) }

    fun toResultItem(
        item: RecognizedItem,
        sourceKey: String,
        nowMs: Long,
        sceneKey: String?,
    ): ResultItem {
        val action = primaryActionOf(item)
        // OPEN 但值不是链接：创建阶段就降级成 COPY（与旧口径一致，避免点了没反应）。
        val effective = if (action == ResultPrimaryAction.OPEN && item.urls.isEmpty()) {
            ResultPrimaryAction.COPY
        } else {
            action
        }
        return ResultItem(
            sourceKey = sourceKey,
            title = item.ruleName.ifBlank { GenericLabel },
            text = item.value,
            receivedAtElapsedMs = nowMs,
            ruleId = item.ruleId,
            action = effective,
            fromCapture = item.urls.isEmpty(),
            urls = item.urls,
            recognized = item,
            sceneKey = sceneKey,
        )
    }

    private fun primaryActionOf(item: RecognizedItem): ResultPrimaryAction =
        when (val output = item.output) {
            is RecognitionOutput.Builtin -> when (output.kind) {
                RecognitionBuiltin.Copy, RecognitionBuiltin.CopyAndClear ->
                    ResultPrimaryAction.COPY
                RecognitionBuiltin.OpenLink -> ResultPrimaryAction.OPEN
                RecognitionBuiltin.Share -> ResultPrimaryAction.SHARE
            }
            // 动作 / 通知输出：主动作就是「跑这条规则」，执行细节交给 RecognitionExecutor。
            is RecognitionOutput.Action, is RecognitionOutput.Notify ->
                ResultPrimaryAction.RUN_ACTION
        }

    internal fun notificationText(pulse: NotificationPulse): String {
        val text = pulse.text
        val bigText = pulse.bigText
        val body = when {
            bigText.isNullOrBlank() -> text
            text.isBlank() -> bigText
            bigText.contains(text) -> bigText
            else -> "$text\n$bigText"
        }
        return listOf(pulse.title, body)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }
}

/**
 * 胶囊菜单「复制」的语义投影：`CopyAndClear` 必须保留，否则「复制后清除」在胶囊里永远不生效；
 * 其它输出（打开 / 分享 / 动作 / 通知）点击复制时降级为普通复制。
 */
internal fun RecognizedItem.asCopyOutcome(): RecognizedItem = when (builtin) {
    RecognitionBuiltin.Copy, RecognitionBuiltin.CopyAndClear -> this
    else -> copy(output = RecognitionOutput.Builtin(RecognitionBuiltin.Copy))
}

/** 胶囊菜单「分享」的语义投影。 */
internal fun RecognizedItem.asShareOutcome(): RecognizedItem =
    copy(output = RecognitionOutput.Builtin(RecognitionBuiltin.Share))

/** 复制类输出（`Copy` / `CopyAndClear`）都应按「已复制」反馈。 */
internal fun RecognizedItem.isCopyOutcome(): Boolean =
    builtin == RecognitionBuiltin.Copy || builtin == RecognitionBuiltin.CopyAndClear
